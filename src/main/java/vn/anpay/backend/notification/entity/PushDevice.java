package vn.anpay.backend.notification.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "push_devices")
public class PushDevice {
    @Id
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "fcm_token", nullable = false, unique = true, length = 2048)
    public String fcmToken;

    @Column(nullable = false, length = 16)
    public String platform;

    @Column(name = "device_id", length = 255)
    public String deviceId;

    @Column(nullable = false)
    public boolean enabled;

    @Column(name = "last_seen_at", nullable = false)
    public Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    protected PushDevice() {
    }

    public PushDevice(UUID userId, String fcmToken, String platform, String deviceId) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.fcmToken = fcmToken;
        this.platform = platform;
        this.deviceId = deviceId;
        this.enabled = true;
        this.lastSeenAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }
}
