package vn.anpay.backend.auth.dto;

import java.util.UUID;

public record PhoneOtpSendResponse(
        UUID challengeId,
        String phoneNumber,
        String maskedPhone,
        long expiresInSeconds,
        long resendAfterSeconds,
        String delivery,
        String demoOtp
) {
}
