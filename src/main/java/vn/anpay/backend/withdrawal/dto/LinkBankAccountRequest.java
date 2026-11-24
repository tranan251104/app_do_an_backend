package vn.anpay.backend.withdrawal.dto;

import jakarta.validation.constraints.*;

public record LinkBankAccountRequest(
        @NotBlank @Pattern(regexp = "\\d{6}") String bankBin,
        @NotBlank @Size(max = 128) String bankName,
        @NotBlank @Pattern(regexp = "\\d{6,20}") String accountNumber,
        @NotBlank @Size(max = 160) String accountName,
        @AssertTrue(message = "Phải xác nhận tài khoản ngân hàng thuộc về bạn") boolean confirmOwnAccount
) { }
