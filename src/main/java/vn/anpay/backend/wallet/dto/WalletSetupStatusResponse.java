package vn.anpay.backend.wallet.dto;

public record WalletSetupStatusResponse(
        boolean walletCreated,
        String walletCode
) {
}
