package vn.anpay.backend.transfer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.notification.repository.PushDeviceRepository;
import vn.anpay.backend.outbox.service.OutboxService;
import vn.anpay.backend.user.entity.User;
import vn.anpay.backend.user.repository.UserRepository;

import java.util.Map;
import java.util.UUID;

/**
 * AnPay email OTP remains unchanged. Phone-only users may receive a demo OTP
 * over an authenticated account's registered FCM device when explicitly enabled.
 * FCM is NOT SMS and does NOT verify ownership of a SIM; demo only.
 */
@Service
public class InternalTransferOtpDispatcher {
    public enum Channel { EMAIL, FCM_DEMO }

    private final UserRepository users;
    private final PushDeviceRepository devices;
    private final OutboxService outbox;
    private final ObjectMapper mapper;
    private final boolean demoEnabled;
    private final boolean fcmEnabled;

    public InternalTransferOtpDispatcher(
            UserRepository users,
            PushDeviceRepository devices,
            OutboxService outbox,
            ObjectMapper mapper,
            @Value("${app.transfer.demo-phone-otp.enabled:false}") boolean demoEnabled,
            @Value("${app.push.fcm.enabled:false}") boolean fcmEnabled
    ) {
        this.users = users;
        this.devices = devices;
        this.outbox = outbox;
        this.mapper = mapper;
        this.demoEnabled = demoEnabled;
        this.fcmEnabled = fcmEnabled;
    }

    /** Fail before creating a transaction when an OTP cannot be delivered. */
    public Channel channelFor(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> bad("USER_NOT_FOUND", "Không tìm thấy tài khoản"));
        if (!"ACTIVE".equals(user.status)) {
            throw bad("ACCOUNT_UNAVAILABLE", "Tài khoản không khả dụng");
        }
        if (user.email != null && !user.email.isBlank()) {
            return Channel.EMAIL;
        }
        if (user.phone == null || user.phone.isBlank()) {
            throw bad("OTP_CHANNEL_REQUIRED", "Tài khoản chưa có phương thức nhận OTP");
        }
        if (!demoEnabled || !fcmEnabled) {
            throw bad("OTP_PHONE_DEMO_UNAVAILABLE", "Chưa bật OTP điện thoại giả lập. Vui lòng bổ sung email hoặc cấu hình FCM demo");
        }
        if (devices.findByUserIdAndEnabledTrue(userId).isEmpty()) {
            throw bad("OTP_FCM_DEVICE_REQUIRED", "Cần đăng ký thiết bị để nhận OTP demo qua FCM");
        }
        return Channel.FCM_DEMO;
    }

    public void enqueue(UUID userId, UUID txId, UUID challengeId, Channel channel) {
        try {
            if (channel == Channel.EMAIL) {
                User user = users.findById(userId).orElseThrow();
                outbox.add("TRANSFER", txId, "SEND_OTP_EMAIL", mapper.writeValueAsString(Map.of(
                        "challengeId", challengeId.toString(), "email", user.email, "purpose", "TRANSFER")));
            } else {
                enqueueFcmDemo(userId, txId, challengeId, "TRANSFER");
            }
        } catch (Exception ex) {
            // Roll back transaction and OTP if an event cannot be saved.
            throw new IllegalStateException("Failed to enqueue transfer OTP", ex);
        }
    }

    /** Use for internal or external transfers; never return the plaintext OTP in an API. */
    public void enqueueFcmDemo(UUID userId, UUID txId, UUID challengeId, String aggregate) {
        if (!"TRANSFER".equals(aggregate) && !"EXTERNAL_TRANSFER".equals(aggregate)) {
            throw new IllegalArgumentException("Unsupported transfer OTP aggregate");
        }
        try {
            outbox.add(aggregate, txId, "SEND_OTP_FCM_DEMO", mapper.writeValueAsString(Map.of(
                    "challengeId", challengeId.toString(), "userId", userId.toString(),
                    "transactionId", txId.toString())));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to enqueue demo transfer OTP", ex);
        }
    }

    private BusinessException bad(String code, String message) {
        return new BusinessException(code, message, HttpStatus.BAD_REQUEST);
    }
}
