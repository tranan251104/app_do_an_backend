package vn.anpay.backend.transfer.dto;
import jakarta.validation.constraints.*;
import java.util.UUID;
/** PIN never crosses the API; signature and its six-digit display code identify the challenge. */
public record ConfirmTransferRequest(@NotNull UUID deviceId, @NotBlank String signature,
                                     @NotBlank @Pattern(regexp="\\d{6}") String smartOtpCode) {}
