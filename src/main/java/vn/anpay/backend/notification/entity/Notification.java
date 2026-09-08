package vn.anpay.backend.notification.entity;

import jakarta.persistence.*;

import java.time.Instant;
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
                balanceAfter
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
}
