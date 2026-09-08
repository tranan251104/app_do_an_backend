package vn.anpay.backend.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import vn.anpay.backend.notification.entity.Notification;
import vn.anpay.backend.outbox.service.OutboxService;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class NotificationPushQueue {
    private static final Logger log = LoggerFactory.getLogger(NotificationPushQueue.class);

    private final OutboxService outbox;
    private final ObjectMapper mapper;
    private final boolean enabled;

    public NotificationPushQueue(
            OutboxService outbox,
            ObjectMapper mapper,
            @Value("${app.push.fcm.enabled:false}") boolean enabled
    ) {
        this.outbox = outbox;
        this.mapper = mapper;
        this.enabled = enabled;
    }

    public void enqueue(Notification notification) {
        if (!enabled || notification == null) {
            return;
        }
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("userId", notification.userId.toString());
            payload.put("notificationId", notification.id.toString());
            payload.put("title", notification.title);
            payload.put("body", notification.body);
            payload.put("type", notification.type);
            payload.put("category", notification.category);
            if (notification.relatedTransactionId != null) {
                payload.put("transactionId", notification.relatedTransactionId.toString());
            }
            outbox.add(
                    "NOTIFICATION",
                    notification.id,
                    "SEND_PUSH_NOTIFICATION",
                    mapper.writeValueAsString(payload)
            );
        } catch (Exception ex) {
            // Serialization of this simple String map should never fail. Throwing here keeps
            // notification row and its outbox row atomic if a programming error occurs.
            throw new IllegalStateException("Could not queue FCM notification", ex);
        }
        log.debug("[FCM_QUEUE] notificationId={} userId={}", notification.id, notification.userId);
    }
}
