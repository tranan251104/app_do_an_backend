package vn.anpay.backend.auth.dto;

import jakarta.validation.constraints.*;

public record ChangePasswordRequest(@NotBlank String currentPassword,@Size(min=8,max=128) String newPassword) {

}
