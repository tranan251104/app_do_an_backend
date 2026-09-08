package vn.anpay.backend.wallet.dto;

import java.util.UUID;

public record WalletResponse(UUID id,String walletCode,String currency,long availableBalance,long heldBalance,String status) {

}
