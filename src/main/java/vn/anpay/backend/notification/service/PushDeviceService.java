package vn.anpay.backend.notification.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.notification.dto.PushDeviceDto;
import vn.anpay.backend.notification.entity.PushDevice;
import vn.anpay.backend.notification.repository.PushDeviceRepository;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PushDeviceService {
    private static final Set<String> PLATFORMS = Set.of("ANDROID", "IOS", "WEB");

    private final PushDeviceRepository repository;

    public PushDeviceService(PushDeviceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PushDeviceDto register(UUID userId, String token, String platform, String deviceId) {
        String normalizedToken = required(token, "FCM token không được để trống");
        if (normalizedToken.length() > 2048) {
            throw new BusinessException("INVALID_FCM_TOKEN", "FCM token không hợp lệ", HttpStatus.BAD_REQUEST);
        }

        String normalizedPlatform = required(platform, "Platform không được để trống")
                .toUpperCase(Locale.ROOT);
        if (!PLATFORMS.contains(normalizedPlatform)) {
            throw new BusinessException(
                    "INVALID_PUSH_PLATFORM",
                    "Platform không hợp lệ. Hỗ trợ: ANDROID, IOS, WEB",
                    HttpStatus.BAD_REQUEST
            );
        }

        String normalizedDeviceId = deviceId == null || deviceId.isBlank() ? null : deviceId.trim();
        if (normalizedDeviceId != null && normalizedDeviceId.length() > 255) {
            throw new BusinessException("INVALID_DEVICE_ID", "Device ID quá dài", HttpStatus.BAD_REQUEST);
        }

        Instant now = Instant.now();
        PushDevice device = repository.findByFcmToken(normalizedToken).orElse(null);
        if (device == null) {
            device = new PushDevice(userId, normalizedToken, normalizedPlatform, normalizedDeviceId);
        } else {
            // An FCM token identifies one app installation. If another account logs in on
            // the same installation, move the token to the newly authenticated account.
            device.userId = userId;
            device.platform = normalizedPlatform;
            device.deviceId = normalizedDeviceId;
            device.enabled = true;
            device.lastSeenAt = now;
            device.updatedAt = now;
        }

        return dto(repository.save(device));
    }

    @Transactional
    public void unregister(UUID userId, String token) {
        String normalizedToken = required(token, "FCM token không được để trống");
        repository.findByFcmToken(normalizedToken).ifPresent(device -> {
            if (!device.userId.equals(userId)) {
                return;
            }
            device.enabled = false;
            device.updatedAt = Instant.now();
        });
    }

    @Transactional
    public void disableInvalidToken(String token) {
        repository.findByFcmToken(token).ifPresent(device -> {
            device.enabled = false;
            device.updatedAt = Instant.now();
        });
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("INVALID_PUSH_DEVICE", message, HttpStatus.BAD_REQUEST);
        }
        return value.trim();
    }

    private PushDeviceDto dto(PushDevice device) {
        return new PushDeviceDto(
                device.id,
                device.platform,
                device.deviceId,
                device.enabled,
                device.lastSeenAt
        );
    }
}
