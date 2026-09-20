package vn.anpay.backend.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.anpay.backend.notification.entity.Notification;
import vn.anpay.backend.notification.repository.NotificationRepository;

import java.time.*;
import java.util.List;
import java.util.UUID;

@Service
public class ProactiveNotificationService {
    private static final Logger log = LoggerFactory.getLogger(ProactiveNotificationService.class);

    private static final List<Template> TEMPLATES = List.of(
            new Template(
                    "PROACTIVE_PROMOTIONS",
                    "NEWS",
                    "Khám phá ưu đãi trên AnPay",
                    "Mở AnPay để xem các chương trình ưu đãi và dịch vụ đang có dành cho bạn.",
                    "NORMAL",
                    "OPEN_PROMOTIONS",
                    null,
                    7
            ),
            new Template(
                    "PROACTIVE_MOBILE_TOPUP",
                    "NEWS",
                    "Nạp điện thoại ngay trên AnPay",
                    "Bạn có thể nạp điện thoại trực tiếp trong ứng dụng mà không cần rời AnPay.",
                    "NORMAL",
                    "OPEN_MOBILE_TOPUP",
                    null,
                    7
            ),
            new Template(
                    "PROACTIVE_QR",
                    "NEWS",
                    "Thanh toán QR thuận tiện hơn",
                    "Mở tính năng QR trên AnPay để thanh toán nhanh khi cần.",
                    "NORMAL",
                    "OPEN_QR",
                    null,
                    7
            ),
            new Template(
                    "PROACTIVE_TRANSFER",
                    "NEWS",
                    "Chuyển tiền nhanh với AnPay",
                    "Bạn có thể chuyển tiền ngay trong ứng dụng và theo dõi kết quả trong lịch sử giao dịch.",
                    "NORMAL",
                    "OPEN_TRANSFER",
                    null,
                    7
            ),
            new Template(
                    "PROACTIVE_SERVICES",
                    "NEWS",
                    "Khám phá Dịch vụ AnPay",
                    "Xem thêm các tiện ích và dịch vụ có sẵn ngay trên ứng dụng AnPay.",
                    "LOW",
                    "OPEN_SERVICES",
                    null,
                    7
            ),
            new Template(
                    "PROACTIVE_SECURITY_TIP",
                    "MY",
                    "Mẹo bảo mật tài khoản",
                    "Không chia sẻ mật khẩu hoặc mã OTP cho bất kỳ ai, kể cả người tự xưng là nhân viên hỗ trợ.",
                    "HIGH",
                    null,
                    null,
                    3
            )
    );

    private final NotificationRepository repository;
    private final NotificationPushQueue pushQueue;
    private final ZoneId zoneId;

    public ProactiveNotificationService(
            NotificationRepository repository,
            NotificationPushQueue pushQueue,
            @Value("${app.notification.proactive.zone:Asia/Ho_Chi_Minh}") String zone
    ) {
        this.repository = repository;
        this.pushQueue = pushQueue;
        this.zoneId = ZoneId.of(zone);
    }

    /**
     * Creates at most one proactive notification per user per local calendar day.
     * Template selection is deterministic for user + date, which makes retries safe and predictable.
     */
    @Transactional
    public boolean sendDailyForUser(UUID userId) {
        LocalDate today = LocalDate.now(zoneId);
        Instant from = today.atStartOfDay(zoneId).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(zoneId).toInstant();

        if (repository.countProactiveForUserBetween(userId, from, to) > 0) {
            return false;
        }

        Template template = selectTemplate(userId, today);
        Instant expiresAt = Instant.now().plus(Duration.ofDays(template.expiresAfterDays()));
        Notification notification = Notification.proactive(
                userId,
                template.type(),
                template.category(),
                template.title(),
                template.body(),
                template.priority(),
                template.actionType(),
                template.actionData(),
                expiresAt
        );

        notification = repository.save(notification);
        pushQueue.enqueue(notification);
        log.info(
                "[PROACTIVE_NOTIFICATION] Created notificationId={} userId={} type={} actionType={}",
                notification.id,
                userId,
                notification.type,
                notification.actionType
        );
        return true;
    }

    private Template selectTemplate(UUID userId, LocalDate date) {
        int seed = 31 * userId.hashCode() + date.hashCode();
        int index = Math.floorMod(seed, TEMPLATES.size());
        return TEMPLATES.get(index);
    }

    private record Template(
            String type,
            String category,
            String title,
            String body,
            String priority,
            String actionType,
            String actionData,
            int expiresAfterDays
    ) {
    }
}
