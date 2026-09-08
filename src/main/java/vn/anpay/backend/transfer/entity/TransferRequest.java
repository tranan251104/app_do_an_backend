package vn.anpay.backend.transfer.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity @Table(name="transfer_requests") public class TransferRequest {
    @Id
    public UUID id;
    @Column(name="transaction_id")
    public UUID transactionId;
    @Column(name="user_id", nullable=false)
    public UUID userId;
    @Column(name="recipient_type")
    public String recipientType;
    @Column(name="recipient_reference")
    public String recipientReference;
    public String note;
    @Column(name="idempotency_key")
    public UUID idempotencyKey;
    @Column(name="otp_challenge_id")
    public UUID otpChallengeId;
    @Column(name="expires_at")
    public Instant expiresAt;
    @Column(name="created_at")
    public Instant createdAt;
    public TransferRequest() {

    }

}
