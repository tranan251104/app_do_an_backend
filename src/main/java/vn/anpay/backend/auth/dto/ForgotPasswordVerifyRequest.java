package vn.anpay.backend.auth.dto;

import jakarta.validation.constraints.*;

public record ForgotPasswordVerifyRequest(@NotBlank String identifier,@Pattern(regexp="\\d{6}") String otp) {

}
