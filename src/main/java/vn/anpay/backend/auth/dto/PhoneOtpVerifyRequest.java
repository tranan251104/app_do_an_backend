package vn.anpay.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PhoneOtpVerifyRequest(
        @NotNull UUID challengeId,
        @NotBlank @Size(max = 32) String phoneNumber,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String otp,
        @Size(max = 255) String deviceId,
        @Size(max = 160) String fullName,
        @Size(min = 8, max = 128) String password
) {
}
