package vn.anpay.backend.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
        UUID id,
        String type,
        String category,
        String title,
        String body,
        UUID relatedTransactionId,
        Long amount,
        String direction,
        Long balanceAfter,
        String currency,
        String priority,
        String actionType,
        String actionData,
        Instant expiresAt,
        Instant readAt,
        boolean read,
        Instant createdAt
) {
}
