package vn.anpay.backend.auth.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Size(max = 160) String fullName,
        @Email @Size(max = 160) String email,
        @Size(max = 32) String phone,
        @NotBlank @Size(min = 8, max = 128) String password,
        @Size(max = 4096) String firebaseIdToken
) {

}
