package vn.anpay.backend.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WalletNumberClaimRequest(
        @NotBlank @Size(max = 32) String walletCode
) {
}
