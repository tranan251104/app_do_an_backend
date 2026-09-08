package vn.anpay.backend.outbox.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class OtpDeliveryService {
    private final StringRedisTemplate redis;
    private final long deliveryTtlMinutes;

    public OtpDeliveryService(
            StringRedisTemplate redis,
            @Value("${app.otp.ttl-minutes:5}") long otpTtlMinutes
    ) {
        this.redis = redis;
        // Keep the plaintext delivery copy only slightly longer than the OTP itself.
        // The verification copy is hashed in PostgreSQL; this Redis value only exists
        // so the asynchronous outbox worker can send the email.
        this.deliveryTtlMinutes = Math.max(otpTtlMinutes + 1, 2);
    }

    public void stage(UUID challengeId, String code) {
        redis.opsForValue().set(key(challengeId), code, Duration.ofMinutes(deliveryTtlMinutes));
    }

    /**
     * Read without deleting. The worker must only delete the staged OTP after the
     * SMTP send succeeds; otherwise a transient SMTP failure would lose the code.
     */
    public String peek(UUID challengeId) {
        return redis.opsForValue().get(key(challengeId));
    }

    public void consume(UUID challengeId) {
        redis.delete(key(challengeId));
    }

    private String key(UUID challengeId) {
        return "otp:delivery:" + challengeId;
    }
}
