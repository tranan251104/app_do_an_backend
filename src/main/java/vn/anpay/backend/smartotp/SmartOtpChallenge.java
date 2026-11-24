package vn.anpay.backend.smartotp;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="smart_otp_transaction_challenges")
public class SmartOtpChallenge {
    @Id public UUID id;
    @Column(name="transaction_id", nullable=false, unique=true) public UUID transactionId;
    @Column(name="user_id", nullable=false) public UUID userId;
    @Column(name="device_id", nullable=false) public UUID deviceId;
    @Column(name="nonce", nullable=false, length=64) public String nonce;
    @Column(name="transaction_digest", nullable=false, length=64) public String transactionDigest;
    @Column(name="expires_at", nullable=false) public Instant expiresAt;
    @Column(name="consumed_at") public Instant consumedAt;
    @Column(name="attempt_count", nullable=false) public int attemptCount;
    @Column(name="created_at", nullable=false) public Instant createdAt;
    protected SmartOtpChallenge() {}
}
