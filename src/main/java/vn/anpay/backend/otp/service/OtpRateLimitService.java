package vn.anpay.backend.otp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.anpay.backend.common.exception.BusinessException;

import java.time.Duration;
import java.util.UUID;

@Service
public class OtpRateLimitService {
    private final StringRedisTemplate redis;
    private final int maxResends;
    private final Duration counterTtl;

    public OtpRateLimitService(
            StringRedisTemplate redis,
            @Value("${app.otp.max-resends:3}") int maxResends,
            @Value("${app.otp.resend-counter-minutes:30}") long counterMinutes
    ) {
        this.redis = redis;
        this.maxResends = maxResends;
        this.counterTtl = Duration.ofMinutes(Math.max(counterMinutes, 5));
    }

    /**
     * Counts resend requests per transaction. The initial OTP is not counted.
     */
    public int registerResend(UUID transactionId) {
        String key = "otp:resend-count:external-transfer:" + transactionId;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, counterTtl);
        }
        int current = count == null ? 1 : count.intValue();
        if (current > maxResends) {
            throw new BusinessException(
                    "OTP_RESEND_LIMIT_REACHED",
                    "Đã vượt quá số lần gửi lại OTP cho giao dịch này",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
        return current;
    }

    public void clear(UUID transactionId) {
        redis.delete("otp:resend-count:external-transfer:" + transactionId);
    }
}
