package vn.anpay.backend.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.anpay.backend.auth.dto.AuthResponse;
import vn.anpay.backend.auth.dto.EmailOtpResendRequest;
import vn.anpay.backend.auth.dto.EmailOtpSendRequest;
import vn.anpay.backend.auth.dto.EmailOtpSendResponse;
import vn.anpay.backend.auth.dto.EmailOtpVerifyRequest;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.outbox.service.OtpDeliveryService;
import vn.anpay.backend.outbox.service.OutboxService;
import vn.anpay.backend.user.entity.User;
import vn.anpay.backend.user.repository.UserRepository;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class EmailOtpAuthService {
    private final EmailOtpChallengeService challenges;
    private final OtpDeliveryService delivery;
    private final OutboxService outbox;
    private final ObjectMapper mapper;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AuthService auth;

    public EmailOtpAuthService(
            EmailOtpChallengeService challenges,
            OtpDeliveryService delivery,
            OutboxService outbox,
            ObjectMapper mapper,
            UserRepository users,
            PasswordEncoder passwords,
            AuthService auth
    ) {
        this.challenges = challenges;
        this.delivery = delivery;
        this.outbox = outbox;
        this.mapper = mapper;
        this.users = users;
        this.passwords = passwords;
        this.auth = auth;
    }

    @Transactional
    public EmailOtpSendResponse send(EmailOtpSendRequest request) {
        String email = normalize(request.email());
        requireEmailAvailable(email);
        EmailOtpChallengeService.Issued issued = challenges.start(email);
        queueEmail(issued, email);
        return response(issued, email);
    }

    @Transactional
    public EmailOtpSendResponse resend(EmailOtpResendRequest request) {
        String email = normalize(request.email());
        requireEmailAvailable(email);
        EmailOtpChallengeService.Issued issued = challenges.resend(request.challengeId(), email);
        queueEmail(issued, email);
        return response(issued, email);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResponse verify(EmailOtpVerifyRequest request) {
        String email = normalize(request.email());
        requireEmailAvailable(email);
        String registrationName = requireRegistrationDetails(request);

        // Validate registration fields before consuming the single-use challenge.
        challenges.verify(request.challengeId(), email, request.otp());

        User user = new User(
                UUID.randomUUID(),
                email,
                null,
                passwords.encode(request.password()),
                registrationName
        );
        user.emailVerified = true;
        user = users.save(user);

        return auth.issueSession(user.id, request.deviceId());
    }

    private void requireEmailAvailable(String email) {
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(
                    "ACCOUNT_EXISTS",
                    "Email đã được đăng ký trước đó. Vui lòng sử dụng email khác",
                    HttpStatus.CONFLICT
            );
        }
    }

    private String requireRegistrationDetails(EmailOtpVerifyRequest request) {
        String fullName = request.fullName() == null ? null : request.fullName().trim();
        if (fullName == null || fullName.isEmpty()
                || request.password() == null || request.password().isBlank()) {
            throw new BusinessException(
                    "REGISTRATION_DETAILS_REQUIRED",
                    "Cần họ tên và mật khẩu để tạo tài khoản bằng email",
                    HttpStatus.BAD_REQUEST
            );
        }
        return fullName;
    }

    private void queueEmail(EmailOtpChallengeService.Issued issued, String email) {
        UUID challengeId = issued.challenge().id;
        delivery.stage(challengeId, issued.plaintextCode());
        try {
            String payload = mapper.writeValueAsString(Map.of(
                    "challengeId", challengeId.toString(),
                    "email", email,
                    "purpose", EmailOtpChallengeService.PURPOSE
            ));
            outbox.add("OTP", challengeId, "SEND_OTP_EMAIL", payload);
        } catch (Exception exception) {
            delivery.consume(challengeId);
            throw new IllegalStateException("Không thể xếp hàng gửi OTP email", exception);
        }
    }

    private EmailOtpSendResponse response(EmailOtpChallengeService.Issued issued, String email) {
        return new EmailOtpSendResponse(
                issued.challenge().id,
                email,
                mask(email),
                challenges.ttlSeconds(),
                challenges.resendSeconds(),
                "EMAIL"
        );
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String mask(String email) {
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + domain;
        }
        return local.substring(0, Math.min(3, local.length())) + "***" + domain;
    }
}
