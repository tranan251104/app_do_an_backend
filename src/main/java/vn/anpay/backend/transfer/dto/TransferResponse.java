package vn.anpay.backend.transfer.dto;

import java.time.Instant;
import java.util.UUID;

public record TransferResponse(UUID transactionId,String reference,long amount,String status,String receiverWalletCode,String receiverDisplayName,Instant expiresAt,boolean otpRequired) {

}
