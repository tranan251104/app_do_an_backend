package vn.anpay.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record EmailOtpResendRequest(
        @NotNull UUID challengeId,
        @NotBlank @Email @Size(max = 160) String email
) {
}
