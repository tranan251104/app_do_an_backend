package vn.anpay.backend.smartotp;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="smart_otp_devices")
public class SmartOtpDevice {
    @Id public UUID id;
    @Column(name="user_id", nullable=false) public UUID userId;
    @Column(name="public_key", nullable=false, length=64) public String publicKey;
    @Column(name="status", nullable=false, length=20) public String status;
    @Column(name="enrollment_otp_id") public UUID enrollmentOtpId;
    @Column(name="enrollment_expires_at") public Instant enrollmentExpiresAt;
    @Column(name="created_at", nullable=false) public Instant createdAt;
    @Column(name="activated_at") public Instant activatedAt;
    @Column(name="revoked_at") public Instant revokedAt;
    protected SmartOtpDevice() {}
}
