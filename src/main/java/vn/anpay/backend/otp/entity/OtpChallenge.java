package vn.anpay.backend.otp.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity @Table(name="otp_challenges") public class OtpChallenge {
    @Id
    public UUID id;
    @Column(name="user_id")
    public UUID userId;
    @Column(name="phone_normalized")
    public String phoneNormalized;
    @Column(name="email_normalized")
    public String emailNormalized;
    public String purpose;
    @Column(name="context_id")
    public UUID contextId;
    @Column(name="code_hash")
    public String codeHash;
    @Column(name="expires_at")
    public Instant expiresAt;
    @Column(name="resend_available_at")
    public Instant resendAvailableAt;
    @Column(name="attempt_count")
    public int attemptCount;
    @Column(name="max_attempts")
    public int maxAttempts;
    @Column(name="resend_count")
    public int resendCount;
    @Column(name="consumed_at")
    public Instant consumedAt;
    @Column(name="created_at")
    public Instant createdAt;
    public OtpChallenge() {

    }

}
