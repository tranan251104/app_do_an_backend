package vn.anpay.backend.notification.dto;

import java.time.Instant;
import java.util.UUID;

public record PushDeviceDto(
        UUID id,
        String platform,
        String deviceId,
        boolean enabled,
        Instant lastSeenAt
) {
}
