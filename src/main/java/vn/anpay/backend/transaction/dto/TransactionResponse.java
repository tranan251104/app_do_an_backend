package vn.anpay.backend.transaction.dto;

import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String reference,
        String type,
        String status,
        long amount,
        long fee,
        String currency,
        String direction,
        String title,
        String counterparty,
        String counterpartyName,
        String counterpartyAccount,
        String bankName,
        String bankCode,
        String description,
        String provider,
        String providerReference,
        boolean simulated,
        Instant createdAt,
        Instant completedAt
) {
}
