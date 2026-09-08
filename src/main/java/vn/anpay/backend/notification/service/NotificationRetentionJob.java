package vn.anpay.backend.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.anpay.backend.notification.repository.NotificationRepository;

@Component
public class NotificationRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(NotificationRetentionJob.class);

    private final NotificationRepository repository;
    private final NotificationService service;

    public NotificationRetentionJob(NotificationRepository repository, NotificationService service) {
        this.repository = repository;
        this.service = service;
    }

    @Scheduled(cron = "${app.notification.cleanup-cron:0 15 3 * * *}", zone = "UTC")
    @Transactional
    public void cleanupExpiredNotifications() {
        var cutoff = service.retentionCutoff();
        int deleted = repository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("[NOTIFICATION_RETENTION] Deleted expired notifications count={} retentionDays={} cutoff={}",
                    deleted, service.retentionDays(), cutoff);
        }
    }
}
