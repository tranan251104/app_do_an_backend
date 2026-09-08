package vn.anpay.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PhoneOtpResendRequest(
        @NotNull UUID challengeId,
        @NotBlank @Size(max = 32) String phoneNumber,
        @Size(max = 4096) String fcmToken
) {
}
