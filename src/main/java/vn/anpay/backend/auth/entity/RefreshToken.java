package vn.anpay.backend.auth.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity @Table(name="refresh_tokens") public class RefreshToken {
    @Id
    public UUID id;
    @Column(name="user_id")
    public UUID userId;
    @Column(name="family_id")
    public UUID familyId;
    @Column(name="token_hash")
    public String tokenHash;
    @Column(name="expires_at")
    public Instant expiresAt;
    @Column(name="revoked_at")
    public Instant revokedAt;
    @Column(name="device_id")
    public String deviceId;
    @Column(name="created_at")
    public Instant createdAt;
    protected RefreshToken() {

    }
    public RefreshToken(UUID uid,UUID family,String hash,Instant expires,String device) {
        id=UUID.randomUUID();
        userId=uid;
        familyId=family;
        tokenHash=hash;
        expiresAt=expires;
        deviceId=device;
        createdAt=Instant.now();

    }

}
