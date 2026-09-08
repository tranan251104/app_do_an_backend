package vn.anpay.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailOtpSendRequest(
        @NotBlank @Email @Size(max = 160) String email
) {
}
