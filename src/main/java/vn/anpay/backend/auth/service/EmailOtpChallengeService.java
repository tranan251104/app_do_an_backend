package vn.anpay.backend.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.otp.entity.OtpChallenge;
import vn.anpay.backend.otp.repository.OtpRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

@Service
public class EmailOtpChallengeService {
    static final String PURPOSE = "EMAIL_AUTH";

    private final OtpRepository challenges;
    private final PasswordEncoder passwords;
    private final long ttlSeconds;
    private final long resendSeconds;
    private final int maxAttempts;
    private final int maxResends;
    private final SecureRandom random = new SecureRandom();

    public EmailOtpChallengeService(
            OtpRepository challenges,
            PasswordEncoder passwords,
            @Value("${app.otp.ttl-minutes:5}") long ttlMinutes,
            @Value("${app.otp.resend-seconds:60}") long resendSeconds,
            @Value("${app.otp.max-attempts:5}") int maxAttempts,
            @Value("${app.otp.max-resends:3}") int maxResends
    ) {
        this.challenges = challenges;
        this.passwords = passwords;
        this.ttlSeconds = Math.max(ttlMinutes, 1) * 60;
        this.resendSeconds = resendSeconds;
        this.maxAttempts = maxAttempts;
        this.maxResends = maxResends;
    }

    @Transactional
    public Issued start(String email) {
        Instant now = Instant.now();
        int resendCount = 0;
        OtpChallenge previous = challenges
                .findFirstByEmailNormalizedAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(email, PURPOSE)
                .orElse(null);

        if (previous != null) {
            if (previous.expiresAt.isAfter(now)) {
                requireResendAllowed(previous, now);
                resendCount = previous.resendCount + 1;
            }
            previous.consumedAt = now;
            challenges.save(previous);
        }
        return issue(email, resendCount, now);
    }

    @Transactional
    public Issued resend(UUID challengeId, String email) {
        Instant now = Instant.now();
        OtpChallenge previous = requireChallenge(challengeId, email);
        requireNotConsumed(previous);

        int resendCount = 0;
        if (previous.expiresAt.isAfter(now)) {
            requireResendAllowed(previous, now);
            resendCount = previous.resendCount + 1;
        }
        previous.consumedAt = now;
        challenges.save(previous);
        return issue(email, resendCount, now);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void verify(UUID challengeId, String email, String code) {
        OtpChallenge challenge = requireChallenge(challengeId, email);
        requireNotConsumed(challenge);
        Instant now = Instant.now();

        if (!challenge.expiresAt.isAfter(now)) {
            fail("OTP_EXPIRED", "OTP đã hết hạn", HttpStatus.BAD_REQUEST);
        }
        if (challenge.attemptCount >= challenge.maxAttempts) {
            fail("OTP_LOCKED", "OTP đã vượt quá số lần thử", HttpStatus.TOO_MANY_REQUESTS);
        }
        if (code == null || !passwords.matches(code, challenge.codeHash)) {
            challenge.attemptCount++;
            challenges.save(challenge);
            if (challenge.attemptCount >= challenge.maxAttempts) {
                fail("OTP_LOCKED", "OTP đã vượt quá số lần thử", HttpStatus.TOO_MANY_REQUESTS);
            }
            fail("OTP_INVALID", "OTP không chính xác", HttpStatus.BAD_REQUEST);
        }

        challenge.consumedAt = now;
        challenges.save(challenge);
    }

    private Issued issue(String email, int resendCount, Instant now) {
        String code = "%06d".formatted(random.nextInt(1_000_000));
        OtpChallenge challenge = new OtpChallenge();
        challenge.id = UUID.randomUUID();
        challenge.emailNormalized = email;
        challenge.purpose = PURPOSE;
        challenge.codeHash = passwords.encode(code);
        challenge.expiresAt = now.plusSeconds(ttlSeconds);
        challenge.resendAvailableAt = now.plusSeconds(resendSeconds);
        challenge.maxAttempts = maxAttempts;
        challenge.resendCount = resendCount;
        challenge.createdAt = now;
        challenges.save(challenge);
        return new Issued(challenge, code);
    }

    private OtpChallenge requireChallenge(UUID challengeId, String email) {
        OtpChallenge challenge = challenges.lockById(challengeId).orElseThrow(this::invalidChallenge);
        if (!PURPOSE.equals(challenge.purpose) || !email.equals(challenge.emailNormalized)) {
            throw invalidChallenge();
        }
        return challenge;
    }

    private void requireNotConsumed(OtpChallenge challenge) {
        if (challenge.consumedAt != null) {
            fail("OTP_USED", "OTP đã được sử dụng", HttpStatus.BAD_REQUEST);
        }
    }

    private void requireResendAllowed(OtpChallenge challenge, Instant now) {
        if (challenge.resendAvailableAt.isAfter(now)) {
            fail("OTP_RESEND_TOO_SOON", "Vui lòng chờ trước khi gửi lại OTP", HttpStatus.TOO_MANY_REQUESTS);
        }
        if (challenge.resendCount >= maxResends) {
            fail("OTP_RESEND_LIMIT_REACHED", "Đã vượt quá số lần gửi lại OTP", HttpStatus.TOO_MANY_REQUESTS);
        }
    }

    private BusinessException invalidChallenge() {
        return new BusinessException(
                "OTP_INVALID",
                "OTP không chính xác hoặc phiên xác thực không hợp lệ",
                HttpStatus.BAD_REQUEST
        );
    }

    private void fail(String code, String message, HttpStatus status) {
        throw new BusinessException(code, message, status);
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    public long resendSeconds() {
        return resendSeconds;
    }

    public record Issued(OtpChallenge challenge, String plaintextCode) {
    }
}
