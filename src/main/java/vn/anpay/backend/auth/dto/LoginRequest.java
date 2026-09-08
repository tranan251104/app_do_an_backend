package vn.anpay.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 160) String identifier,
        @NotBlank @Size(max = 128) String password,
        @Size(max = 255) String deviceId
) {

}
