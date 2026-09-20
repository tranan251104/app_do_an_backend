package vn.anpay.backend.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.anpay.backend.user.repository.UserRepository;

@Component
@ConditionalOnProperty(
        name = "app.notification.proactive.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ProactiveNotificationJob {
    private static final Logger log = LoggerFactory.getLogger(ProactiveNotificationJob.class);

    private final UserRepository users;
    private final ProactiveNotificationService proactive;

    public ProactiveNotificationJob(UserRepository users, ProactiveNotificationService proactive) {
        this.users = users;
        this.proactive = proactive;
    }

    /**
     * Two daily opportunities make local/demo environments more forgiving when the backend is not
     * running all day. ProactiveNotificationService still enforces a maximum of one per user/day.
     */
    @Scheduled(
            cron = "${app.notification.proactive.cron:0 0 9,18 * * *}",
            zone = "${app.notification.proactive.zone:Asia/Ho_Chi_Minh}"
    )
    public void createDailyNotifications() {
        var activeUsers = users.findAllByStatus("ACTIVE");
        int created = 0;
        for (var user : activeUsers) {
            try {
                if (proactive.sendDailyForUser(user.id)) {
                    created++;
                }
            } catch (RuntimeException ex) {
                log.warn(
                        "[PROACTIVE_NOTIFICATION_ERR] userId={} errorType={} message={}",
                        user.id,
                        ex.getClass().getSimpleName(),
                        safeMessage(ex)
                );
            }
        }
        log.info(
                "[PROACTIVE_NOTIFICATION_JOB] activeUsers={} created={}",
                activeUsers.size(),
                created
        );
    }

    private String safeMessage(Exception ex) {
        String value = ex.getMessage();
        if (value == null) return "";
        value = value.replaceAll("[\\r\\n]+", " ");
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
