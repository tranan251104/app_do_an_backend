package vn.anpay.backend.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.anpay.backend.notification.repository.PushDeviceRepository;

import java.util.Map;
import java.util.UUID;

@Service
public class PushDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(PushDeliveryService.class);

    private final PushDeviceRepository devices;
    private final PushDeviceService deviceService;
    private final FcmPushService fcm;

    public PushDeliveryService(
            PushDeviceRepository devices,
            PushDeviceService deviceService,
            FcmPushService fcm
    ) {
        this.devices = devices;
        this.deviceService = deviceService;
        this.fcm = fcm;
    }

    public void sendToUser(UUID userId, String title, String body, Map<String, String> data) {
        var activeDevices = devices.findByUserIdAndEnabledTrue(userId);
        if (activeDevices.isEmpty()) {
            log.debug("[FCM_SKIP] No active push device userId={}", userId);
            return;
        }

        RuntimeException transientFailure = null;
        int sent = 0;
        for (var device : activeDevices) {
            try {
                fcm.send(device.fcmToken, title, body, data);
                sent++;
            } catch (InvalidFcmTokenException ex) {
                deviceService.disableInvalidToken(device.fcmToken);
                log.info("[FCM_TOKEN_DISABLED] userId={} deviceId={} reason={}",
                        userId, device.id, ex.getMessage());
            } catch (RuntimeException ex) {
                transientFailure = ex;
                log.warn("[FCM_SEND_ERR] userId={} deviceId={} errorType={} message={}",
                        userId,
                        device.id,
                        ex.getClass().getSimpleName(),
                        safeMessage(ex));
            }
        }

        log.info("[FCM_DELIVERY] userId={} activeDevices={} sent={}", userId, activeDevices.size(), sent);
        if (transientFailure != null) {
            // Let the outbox retry transient failures. Already-successful devices can receive a
            // duplicate on retry, which is acceptable for the current simple push implementation.
            throw transientFailure;
        }
    }

    private String safeMessage(Exception ex) {
        String value = ex.getMessage();
        if (value == null) return "";
        value = value.replaceAll("[\\r\\n]+", " ");
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
