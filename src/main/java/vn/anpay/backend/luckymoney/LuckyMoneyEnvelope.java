package vn.anpay.backend.luckymoney;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lucky_money_envelopes")
public class LuckyMoneyEnvelope {
    @Id public UUID id;
    @Column(name = "sender_user_id") public UUID senderUserId;
    @Column(name = "receiver_user_id") public UUID receiverUserId;
    @Column(name = "idempotency_key") public UUID idempotencyKey;
    @Column(name = "recipient_wallet_code") public String recipientWalletCode;
    public String template;
    public String greeting;
    @Column(name = "otp_challenge_id") public UUID otpChallengeId;
    @Column(name = "otp_expires_at") public Instant otpExpiresAt;
    @Column(name = "expires_at") public Instant expiresAt;
    @Column(name = "opened_at") public Instant openedAt;
    @Column(name = "created_at") public Instant createdAt;
}
