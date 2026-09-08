package vn.anpay.backend.otp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.otp.entity.OtpChallenge;
import vn.anpay.backend.otp.repository.OtpRepository;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class OtpService {
    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final OtpRepository repo;
    private final PasswordEncoder encoder;
    private final long ttl;
    private final long resend;
    private final int max;
    private final SecureRandom random = new SecureRandom();

    public OtpService(
            OtpRepository repo,
            PasswordEncoder encoder,
            @Value("${app.otp.ttl-minutes:5}") long ttl,
            @Value("${app.otp.resend-seconds:60}") long resend,
            @Value("${app.otp.max-attempts:5}") int max
    ) {
        this.repo = repo;
        this.encoder = encoder;
        this.ttl = ttl;
        this.resend = resend;
        this.max = max;
    }

    public Issued issue(UUID userId, String purpose, UUID context) {
        String code = "%06d".formatted(random.nextInt(1_000_000));
        var challenge = new OtpChallenge();
        challenge.id = UUID.randomUUID();
        challenge.userId = userId;
        challenge.purpose = purpose;
        challenge.contextId = context;
        challenge.codeHash = encoder.encode(code);
        challenge.expiresAt = Instant.now().plus(Duration.ofMinutes(ttl));
        challenge.resendAvailableAt = Instant.now().plusSeconds(resend);
        challenge.maxAttempts = max;
        challenge.createdAt = Instant.now();
        repo.save(challenge);

        log.info(
                "[OTP] Challenge issued challengeId={} userId={} purpose={} contextId={} expiresAt={} maxAttempts={}",
                challenge.id, userId, purpose, context, challenge.expiresAt, challenge.maxAttempts
        );
        return new Issued(challenge, code);
    }

    public void verifyLocked(OtpChallenge challenge, String purpose, UUID context, String code) {
        if (challenge.consumedAt != null) {
            log.warn("[OTP_VERIFY_ERR] OTP already consumed challengeId={} contextId={}", challenge.id, context);
            fail("OTP_USED", "OTP đã được sử dụng");
        }
        if (!purpose.equals(challenge.purpose) || !Objects.equals(context, challenge.contextId)) {
            log.warn("[OTP_VERIFY_ERR] OTP context mismatch challengeId={} purpose={} contextId={}", challenge.id, purpose, context);
            fail("OTP_CONTEXT_INVALID", "OTP không hợp lệ cho giao dịch này");
        }
        if (challenge.expiresAt.isBefore(Instant.now())) {
            log.warn("[OTP_VERIFY_ERR] OTP expired challengeId={} contextId={} expiresAt={}", challenge.id, context, challenge.expiresAt);
            fail("OTP_EXPIRED", "OTP đã hết hạn");
        }
        if (challenge.attemptCount >= challenge.maxAttempts) {
            log.warn("[OTP_VERIFY_ERR] OTP locked challengeId={} contextId={} attempts={}", challenge.id, context, challenge.attemptCount);
            fail("OTP_LOCKED", "OTP đã vượt quá số lần thử");
        }
        if (code == null || !encoder.matches(code, challenge.codeHash)) {
            challenge.attemptCount++;
            repo.save(challenge);
            log.warn(
                    "[OTP_VERIFY_ERR] OTP invalid challengeId={} contextId={} attempt={}/{}",
                    challenge.id, context, challenge.attemptCount, challenge.maxAttempts
            );
            if (challenge.attemptCount >= challenge.maxAttempts) {
                throw new OtpAttemptException("OTP_LOCKED", "OTP đã vượt quá số lần thử");
            }
            throw new OtpAttemptException("OTP_INVALID", "OTP không chính xác");
        }

        challenge.consumedAt = Instant.now();
        repo.save(challenge);
        log.info("[OTP_VERIFY_OK] OTP verified challengeId={} purpose={} contextId={}", challenge.id, purpose, context);
    }

    private void fail(String code, String message) {
        throw new BusinessException(code, message, HttpStatus.BAD_REQUEST);
    }

    public record Issued(OtpChallenge challenge, String plaintextCode) {
    }
}
