package vn.anpay.backend.auth.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.anpay.backend.auth.dto.AuthResponse;
import vn.anpay.backend.auth.dto.LinkPhoneRequest;
import vn.anpay.backend.auth.dto.PhoneLinkResponse;
import vn.anpay.backend.auth.dto.PhoneLoginRequest;
import vn.anpay.backend.auth.firebase.FirebasePhoneIdentity;
import vn.anpay.backend.auth.firebase.FirebasePhoneTokenVerifier;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.user.entity.User;
import vn.anpay.backend.user.repository.UserRepository;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class PhoneAuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final FirebasePhoneTokenVerifier firebaseTokens;
    private final PhoneNumberNormalizer phoneNumbers;
    private final AuthService auth;

    public PhoneAuthService(
            UserRepository users,
            PasswordEncoder passwords,
            FirebasePhoneTokenVerifier firebaseTokens,
            PhoneNumberNormalizer phoneNumbers,
            AuthService auth
    ) {
        this.users = users;
        this.passwords = passwords;
        this.firebaseTokens = firebaseTokens;
        this.phoneNumbers = phoneNumbers;
        this.auth = auth;
    }

    public AuthResponse login(PhoneLoginRequest request) {
        FirebasePhoneIdentity identity = firebaseTokens.verify(request.firebaseIdToken());
        String phone = phoneNumbers.normalize(identity.phoneNumber());
        User user = users.findByPhoneNormalized(phone).orElse(null);

        if (user == null
                || !user.phoneVerified
                || !Objects.equals(user.firebasePhoneUid, identity.uid())) {
            throw invalidCredentials();
        }
        if (!"ACTIVE".equals(user.status)) {
            throw new BusinessException(
                    "ACCOUNT_UNAVAILABLE",
                    "Tài khoản không khả dụng",
                    HttpStatus.FORBIDDEN
            );
        }
        return auth.issueSession(user.id, request.deviceId());
    }

    @Transactional
    public PhoneLinkResponse link(UUID userId, LinkPhoneRequest request) {
        FirebasePhoneIdentity identity = firebaseTokens.verify(request.firebaseIdToken());
        String phone = phoneNumbers.normalize(identity.phoneNumber());
        User user = users.lockById(userId).orElseThrow(() -> new BusinessException(
                "USER_NOT_FOUND",
                "Không tìm thấy người dùng",
                HttpStatus.NOT_FOUND
        ));

        if (!"ACTIVE".equals(user.status)) {
            throw new BusinessException(
                    "ACCOUNT_UNAVAILABLE",
                    "Tài khoản không khả dụng",
                    HttpStatus.FORBIDDEN
            );
        }
        if (!passwords.matches(request.currentPassword(), user.passwordHash)) {
            throw new BusinessException(
                    "CURRENT_PASSWORD_INVALID",
                    "Mật khẩu hiện tại không đúng",
                    HttpStatus.BAD_REQUEST
            );
        }

        users.findByPhoneNormalized(phone)
                .filter(existing -> !existing.id.equals(user.id))
                .ifPresent(existing -> {
                    throw phoneInUse();
                });
        users.findByFirebasePhoneUid(identity.uid())
                .filter(existing -> !existing.id.equals(user.id))
                .ifPresent(existing -> {
                    throw phoneInUse();
                });

        Instant verifiedAt = Instant.now();
        user.phone = phone;
        user.phoneNormalized = phone;
        user.firebasePhoneUid = identity.uid();
        user.phoneVerified = true;
        user.phoneVerifiedAt = verifiedAt;
        user.updatedAt = verifiedAt;

        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw phoneInUse();
        }
        return new PhoneLinkResponse(phone, true, verifiedAt);
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(
                "INVALID_PHONE_CREDENTIALS",
                "Số điện thoại chưa được liên kết hoặc phiên OTP không hợp lệ",
                HttpStatus.UNAUTHORIZED
        );
    }

    private BusinessException phoneInUse() {
        return new BusinessException(
                "PHONE_ALREADY_IN_USE",
                "Số điện thoại này đã được sử dụng. Vui lòng sử dụng số điện thoại khác hoặc đăng nhập bằng số điện thoại này.",
                HttpStatus.CONFLICT
        );
    }
}
