package vn.anpay.backend.transfer.dto;

import jakarta.validation.constraints.*;

public record PrepareTransferRequest(@NotBlank String recipientWalletCode,@Min(10000) long amount,@Size(max=500) String note) {

}
