package vn.anpay.backend.transfer.dto;

import java.time.Instant;
import java.util.UUID;

public record ExternalTransferResponse(
        UUID transactionId,
        String reference,
        long amount,
        String status,
        String bankBin,
        String bankName,
        String accountNumberMasked,
        String accountName,
        String provider,
        String providerReference,
        Instant expiresAt,
        boolean otpRequired,
        String maskedEmail
) {
}
