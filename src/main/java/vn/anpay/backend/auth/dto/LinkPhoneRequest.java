package vn.anpay.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LinkPhoneRequest(
        @NotBlank @Size(max=4096) String firebaseIdToken,
        @NotBlank @Size(max=128) String currentPassword
) {
}
