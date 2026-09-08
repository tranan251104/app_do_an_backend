package vn.anpay.backend.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.notification.service.FcmPushService;
import vn.anpay.backend.notification.service.InvalidFcmTokenException;

import java.util.Locale;
import java.util.Map;

@Service
public class DemoPhoneOtpDeliveryService implements PhoneOtpDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(DemoPhoneOtpDeliveryService.class);

    private final FcmPushService fcm;
    private final String mode;
    private final boolean fcmEnabled;

    public DemoPhoneOtpDeliveryService(
            FcmPushService fcm,
            @Value("${app.auth.demo-phone-otp.delivery:response}") String mode,
            @Value("${app.push.fcm.enabled:false}") boolean fcmEnabled
    ) {
        this.fcm = fcm;
        this.mode = mode == null ? "RESPONSE" : mode.trim().toUpperCase(Locale.ROOT);
        this.fcmEnabled = fcmEnabled;
        if (!"RESPONSE".equals(this.mode) && !"FCM".equals(this.mode)) {
            throw new IllegalArgumentException(
                    "app.auth.demo-phone-otp.delivery must be either 'response' or 'fcm'"
            );
        }
    }

    @Override
    public DeliveryReceipt deliver(String phoneNumber, String fcmToken, String otp) {
        if ("RESPONSE".equals(mode)) {
            log.warn("[DEMO_PHONE_OTP] Returning OTP in API response phone={}", mask(phoneNumber));
            return new DeliveryReceipt("RESPONSE", otp);
        }

        if (!fcmEnabled) {
            throw new BusinessException(
                    "FCM_NOT_CONFIGURED",
                    "FCM chưa được bật cho chế độ gửi OTP demo",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
        if (fcmToken == null || fcmToken.isBlank()) {
            throw new BusinessException(
                    "FCM_TOKEN_REQUIRED",
                    "Cần FCM token để nhận OTP trên thiết bị",
                    HttpStatus.BAD_REQUEST
            );
        }

        try {
            fcm.send(
                    fcmToken.trim(),
                    "AnPay — Mã xác thực demo",
                    "Mã OTP của bạn là " + otp + ". Mã có hiệu lực trong 3 phút.",
                    Map.of("type", "DEMO_PHONE_OTP")
            );
            return new DeliveryReceipt("FCM", null);
        } catch (InvalidFcmTokenException ex) {
            throw new BusinessException(
                    "FCM_TOKEN_INVALID",
                    "FCM token không hợp lệ hoặc đã hết hạn",
                    HttpStatus.BAD_REQUEST
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("[DEMO_PHONE_OTP_FCM_ERR] phone={} errorType={}",
                    mask(phoneNumber), ex.getClass().getSimpleName());
            throw new BusinessException(
                    "OTP_DELIVERY_UNAVAILABLE",
                    "Chưa thể gửi OTP tới thiết bị. Vui lòng thử lại.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    private String mask(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 6) return "***";
        return phoneNumber.substring(0, 3) + "******" + phoneNumber.substring(phoneNumber.length() - 3);
    }
}
