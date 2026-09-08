package vn.anpay.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PhoneOtpSendRequest(
        @NotBlank @Size(max = 32) String phoneNumber,
        @Size(max = 4096) String fcmToken
) {
}
