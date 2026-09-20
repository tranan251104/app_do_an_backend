package vn.anpay.backend.notification.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(nullable = false, length = 64)
    public String type;

    @Column(nullable = false, length = 32)
    public String category;

    @Column(nullable = false)
    public String title;

    @Column(nullable = false)
    public String body;

    @Column(name = "related_transaction_id")
    public UUID relatedTransactionId;

    /** Snapshot amount for balance-change notifications. Null for non-money notifications. */
    public Long amount;

    /** IN / OUT for balance-change notifications. Null for non-money notifications. */
    public String direction;

    /** Wallet balance immediately after the event. Null when it is not applicable/known. */
    @Column(name = "balance_after")
    public Long balanceAfter;

    /** LOW / NORMAL / HIGH. Transaction notifications continue to default to NORMAL. */
    @Column(nullable = false, length = 16)
    public String priority;

    /** Logical action for Flutter, for example OPEN_QR or OPEN_PROMOTIONS. */
    @Column(name = "action_type", length = 64)
    public String actionType;

    /** Optional action payload. Kept as text so Flutter can interpret it without schema churn. */
    @Column(name = "action_data")
    public String actionData;

    /** Optional business expiry. Retention is still controlled separately by retention-days. */
    @Column(name = "expires_at")
    public Instant expiresAt;

    @Column(name = "read_at")
    public Instant readAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    protected Notification() {
    }

    /**
     * Generic personal/system notification. Category is inferred from type for backward compatibility.
     */
    public Notification(UUID uid, String type, String title, String body, UUID tx) {
        this(uid, type, inferCategory(type), title, body, tx, null, null, null);
    }

    public Notification(
            UUID uid,
            String type,
            String category,
            String title,
            String body,
            UUID tx,
            Long amount,
            String direction,
            Long balanceAfter
    ) {
        this(
                uid,
                type,
                category,
                title,
                body,
                tx,
                amount,
                direction,
                balanceAfter,
                "NORMAL",
                null,
                null,
                null
        );
    }

    public Notification(
            UUID uid,
            String type,
            String category,
            String title,
            String body,
            UUID tx,
            Long amount,
            String direction,
            Long balanceAfter,
            String priority,
            String actionType,
            String actionData,
            Instant expiresAt
    ) {
        this.id = UUID.randomUUID();
        this.userId = uid;
        this.type = type;
        this.category = category;
        this.title = title;
        this.body = body;
        this.relatedTransactionId = tx;
        this.amount = amount;
        this.direction = direction;
        this.balanceAfter = balanceAfter;
        this.priority = normalizePriority(priority);
        this.actionType = blankToNull(actionType);
        this.actionData = blankToNull(actionData);
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public static Notification balanceChange(
            UUID uid,
            String type,
            String title,
            String body,
            UUID tx,
            long amount,
            String direction,
            long balanceAfter
    ) {
        return new Notification(
                uid,
                type,
                "BALANCE_CHANGE",
                title,
                body,
                tx,
                amount,
                direction,
                balanceAfter,
                "NORMAL",
                "OPEN_TRANSACTION",
                null,
                null
        );
    }

    public static Notification proactive(
            UUID uid,
            String type,
            String category,
            String title,
            String body,
            String priority,
            String actionType,
            String actionData,
            Instant expiresAt
    ) {
        return new Notification(
                uid,
                type,
                category,
                title,
                body,
                null,
                null,
                null,
                null,
                priority,
                actionType,
                actionData,
                expiresAt
        );
    }

    private static String inferCategory(String type) {
        if (type == null) return "MY";
        return switch (type) {
            case "TOPUP_SUCCESS", "TRANSFER_SENT", "TRANSFER_RECEIVED", "EXTERNAL_TRANSFER_SENT" -> "BALANCE_CHANGE";
            case "NEWS", "ANNOUNCEMENT", "SYSTEM_NEWS" -> "NEWS";
            default -> "MY";
        };
    }

    private static String normalizePriority(String value) {
        if (value == null || value.isBlank()) return "NORMAL";
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "LOW", "NORMAL", "HIGH" -> normalized;
            default -> "NORMAL";
        };
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
