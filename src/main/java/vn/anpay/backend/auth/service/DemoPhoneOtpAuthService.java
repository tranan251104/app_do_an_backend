package vn.anpay.backend.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.anpay.backend.auth.dto.AuthResponse;
import vn.anpay.backend.auth.dto.PhoneOtpResendRequest;
import vn.anpay.backend.auth.dto.PhoneOtpSendRequest;
import vn.anpay.backend.auth.dto.PhoneOtpSendResponse;
import vn.anpay.backend.auth.dto.PhoneOtpVerifyRequest;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.user.entity.User;
import vn.anpay.backend.user.repository.UserRepository;

import java.util.UUID;

@Service
public class DemoPhoneOtpAuthService {
    private static final Logger log = LoggerFactory.getLogger(DemoPhoneOtpAuthService.class);

    private final PhoneNumberNormalizer phoneNumbers;
    private final DemoPhoneOtpChallengeService challenges;
    private final PhoneOtpDeliveryService delivery;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AuthService auth;
    private final boolean enabled;
    private final boolean autoRegister;

    public DemoPhoneOtpAuthService(
            PhoneNumberNormalizer phoneNumbers,
            DemoPhoneOtpChallengeService challenges,
            PhoneOtpDeliveryService delivery,
            UserRepository users,
            PasswordEncoder passwords,
            AuthService auth,
            @Value("${app.auth.demo-phone-otp.enabled:false}") boolean enabled,
            @Value("${app.auth.demo-phone-otp.auto-register:true}") boolean autoRegister
    ) {
        this.phoneNumbers = phoneNumbers;
        this.challenges = challenges;
        this.delivery = delivery;
        this.users = users;
        this.passwords = passwords;
        this.auth = auth;
        this.enabled = enabled;
        this.autoRegister = autoRegister;
    }

    @Transactional
    public PhoneOtpSendResponse send(PhoneOtpSendRequest request) {
        requireEnabled();
        String phone = phoneNumbers.normalize(request.phoneNumber());
        requirePhoneAvailable(phone);
        var issued = challenges.start(phone);
        var receipt = delivery.deliver(phone, request.fcmToken(), issued.plaintextCode());
        return response(issued, phone, receipt);
    }

    @Transactional
    public PhoneOtpSendResponse resend(PhoneOtpResendRequest request) {
        requireEnabled();
        String phone = phoneNumbers.normalize(request.phoneNumber());
        requirePhoneAvailable(phone);
        var issued = challenges.resend(request.challengeId(), phone);
        var receipt = delivery.deliver(phone, request.fcmToken(), issued.plaintextCode());
        return response(issued, phone, receipt);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public AuthResponse verify(PhoneOtpVerifyRequest request) {
        requireEnabled();
        String phone = phoneNumbers.normalize(request.phoneNumber());
        requirePhoneAvailable(phone);
        requireAutoRegister();
        String registrationName = requireRegistrationDetails(request);

        // Validate registration details before consuming the single-use OTP so the
        // client can correct a missing name/password and retry the same challenge.
        challenges.verify(request.challengeId(), phone, request.otp());

        User user = new User(
                UUID.randomUUID(),
                null,
                phone,
                passwords.encode(request.password()),
                registrationName
        );
        // FCM/response delivery proves access to a demo channel, not ownership of the SIM.
        user.phoneVerified = false;
        user.phoneVerifiedAt = null;
        user = users.save(user);

        log.warn("[DEMO_PHONE_REGISTER] Session issued phone={} userId={}", mask(phone), user.id);
        return auth.issueSession(user.id, request.deviceId());
    }

    private void requirePhoneAvailable(String phone) {
        if (users.existsByPhoneNormalized(phone)) {
            throw new BusinessException(
                    "ACCOUNT_EXISTS",
                    "Số điện thoại đã được đăng ký trước đó. Vui lòng sử dụng số điện thoại khác",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void requireAutoRegister() {
        if (!autoRegister) {
            throw new BusinessException(
                    "PHONE_REGISTRATION_DISABLED",
                    "Đăng ký bằng OTP điện thoại demo chưa được bật",
                    HttpStatus.NOT_FOUND
            );
        }
    }

    private String requireRegistrationDetails(PhoneOtpVerifyRequest request) {
        String fullName = request.fullName() == null ? null : request.fullName().trim();
        if (fullName == null || fullName.isEmpty()
                || request.password() == null || request.password().isBlank()) {
            throw new BusinessException(
                    "REGISTRATION_DETAILS_REQUIRED",
                    "Cần họ tên và mật khẩu để tạo tài khoản bằng số điện thoại",
                    HttpStatus.BAD_REQUEST
            );
        }
        return fullName;
    }

    private PhoneOtpSendResponse response(
            DemoPhoneOtpChallengeService.Issued issued,
            String phone,
            PhoneOtpDeliveryService.DeliveryReceipt receipt
    ) {
        return new PhoneOtpSendResponse(
                issued.challenge().id,
                phone,
                mask(phone),
                challenges.ttlSeconds(),
                challenges.resendSeconds(),
                receipt.delivery(),
                receipt.demoOtp()
        );
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new BusinessException(
                    "DEMO_PHONE_OTP_DISABLED",
                    "Đăng nhập OTP demo chưa được bật",
                    HttpStatus.NOT_FOUND
            );
        }
    }

    private String mask(String phone) {
        return phone.substring(0, 3) + "******" + phone.substring(phone.length() - 3);
    }
}
