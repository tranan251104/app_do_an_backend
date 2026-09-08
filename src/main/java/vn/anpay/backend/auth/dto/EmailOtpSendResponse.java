package vn.anpay.backend.auth.dto;

import java.util.UUID;

public record EmailOtpSendResponse(
        UUID challengeId,
        String email,
        String maskedEmail,
        long expiresInSeconds,
        long resendAfterSeconds,
        String delivery
) {
}
