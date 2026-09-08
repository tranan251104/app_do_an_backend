package vn.anpay.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PhoneLoginRequest(
        @NotBlank @Size(max=4096) String firebaseIdToken,
        @Size(max=255) String deviceId
) {
}
