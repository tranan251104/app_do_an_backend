package vn.anpay.backend.auth.dto;

import jakarta.validation.constraints.*;

public record ForgotPasswordResetRequest(@NotBlank String resetToken,@Size(min=8,max=128) String newPassword) {

}
