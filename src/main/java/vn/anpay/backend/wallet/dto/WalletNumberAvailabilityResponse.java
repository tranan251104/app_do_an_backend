package vn.anpay.backend.wallet.dto;

public record WalletNumberAvailabilityResponse(
        String walletCode,
        boolean available
) {
}
