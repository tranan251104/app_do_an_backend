package vn.anpay.backend.payment.dto;

import java.time.Instant;
import java.util.UUID;

public record MockPaymentDetailsResponse(
        UUID paymentIntentId,
        long orderCode,
        long amount,
        String currency,
        String status,
        String recipientName,
        Instant expiresAt,
        String selectedMethod,
        String selectedBank
) {
}
