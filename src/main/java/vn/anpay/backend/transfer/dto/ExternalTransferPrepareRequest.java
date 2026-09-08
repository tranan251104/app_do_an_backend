package vn.anpay.backend.transfer.dto;

import jakarta.validation.constraints.*;

public record ExternalTransferPrepareRequest(
        @NotBlank @Pattern(regexp = "\\d{6}") String bankBin,
        @NotBlank @Size(max = 128) String bankName,
        @NotBlank @Pattern(regexp = "\\d{6,20}") String accountNumber,
        @NotBlank @Size(max = 160) String accountName,
        @Min(10000) long amount,
        @Size(max = 500) String note
) {
}
