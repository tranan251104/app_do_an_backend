package vn.anpay.backend.payment.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.*;
import java.util.*;

@Entity @Table(name="payment_intents") public class PaymentIntent {
    @Id
    public UUID id;
    @Column(name="user_id")
    public UUID userId;
    @Column(name="transaction_id")
    public UUID transactionId;
    public String provider;
    @Column(name="order_code")
    public long orderCode;
    @Column(name="idempotency_key")
    public UUID idempotencyKey;
    public long amount;
    public String currency;
    @Column(name="checkout_url")
    public String checkoutUrl;
    @Column(name="payment_link_id")
    public String paymentLinkId;
    public String status;
    @Column(name="provider_reference")
    public String providerReference;
    @Column(name="expires_at")
    public Instant expiresAt;
    @Column(name="paid_at")
    public Instant paidAt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="raw_metadata",columnDefinition="jsonb")
    public String rawMetadata="{}";
    @Column(name="created_at")
    public Instant createdAt;
    @Column(name="updated_at")
    public Instant updatedAt;
    public PaymentIntent() {

    }

}
