package vn.anpay.backend.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.notification.dto.NotificationDto;
import vn.anpay.backend.notification.entity.Notification;
import vn.anpay.backend.notification.repository.NotificationRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class NotificationService {
    private static final Set<String> CATEGORIES = Set.of("ALL", "MY", "BALANCE_CHANGE", "NEWS");

    private final NotificationRepository repository;
    private final int retentionDays;

    public NotificationService(
            NotificationRepository repository,
            @Value("${app.notification.retention-days:90}") int retentionDays
    ) {
        this.repository = repository;
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> list(
            UUID userId,
            Pageable pageable,
            boolean unreadOnly,
            String category
    ) {
        String normalizedCategory = normalizeCategory(category);
        Instant cutoff = retentionCutoff();

        Page<Notification> page;
        if ("ALL".equals(normalizedCategory)) {
            page = unreadOnly
                    ? repository.findByUserIdAndReadAtIsNullAndCreatedAtGreaterThanEqual(userId, cutoff, pageable)
                    : repository.findByUserIdAndCreatedAtGreaterThanEqual(userId, cutoff, pageable);
        } else {
            page = unreadOnly
                    ? repository.findByUserIdAndCategoryAndReadAtIsNullAndCreatedAtGreaterThanEqual(
                            userId, normalizedCategory, cutoff, pageable)
                    : repository.findByUserIdAndCategoryAndCreatedAtGreaterThanEqual(
                            userId, normalizedCategory, cutoff, pageable);
        }
        return page.map(this::dto);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId, String category) {
        String normalizedCategory = normalizeCategory(category);
        Instant cutoff = retentionCutoff();
        if ("ALL".equals(normalizedCategory)) {
            return repository.countByUserIdAndReadAtIsNullAndCreatedAtGreaterThanEqual(userId, cutoff);
        }
        return repository.countByUserIdAndCategoryAndReadAtIsNullAndCreatedAtGreaterThanEqual(
                userId,
                normalizedCategory,
                cutoff
        );
    }

    @Transactional
    public void read(UUID userId, UUID id) {
        var notification = repository.findById(id).orElseThrow(() -> new BusinessException(
                "NOTIFICATION_NOT_FOUND",
                "Không tìm thấy thông báo",
                HttpStatus.NOT_FOUND
        ));
        if (!notification.userId.equals(userId)) {
            throw new BusinessException("FORBIDDEN", "Không có quyền truy cập", HttpStatus.FORBIDDEN);
        }
        if (notification.createdAt.isBefore(retentionCutoff())) {
            throw new BusinessException(
                    "NOTIFICATION_EXPIRED",
                    "Thông báo này đã hết thời gian lưu trữ",
                    HttpStatus.GONE
            );
        }
        if (notification.readAt == null) {
            notification.readAt = Instant.now();
        }
    }

    @Transactional
    public int all(UUID userId, String category) {
        String normalizedCategory = normalizeCategory(category);
        Instant now = Instant.now();
        Instant cutoff = retentionCutoff(now);
        if ("ALL".equals(normalizedCategory)) {
            return repository.markAllRead(userId, now, cutoff);
        }
        return repository.markAllReadByCategory(userId, normalizedCategory, now, cutoff);
    }

    public Instant retentionCutoff() {
        return retentionCutoff(Instant.now());
    }

    public int retentionDays() {
        return retentionDays;
    }

    private Instant retentionCutoff(Instant now) {
        return now.minus(retentionDays, ChronoUnit.DAYS);
    }

    private String normalizeCategory(String category) {
        String normalized = category == null || category.isBlank()
                ? "ALL"
                : category.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(normalized)) {
            throw new BusinessException(
                    "INVALID_NOTIFICATION_CATEGORY",
                    "Loại thông báo không hợp lệ. Hỗ trợ: ALL, MY, BALANCE_CHANGE, NEWS",
                    HttpStatus.BAD_REQUEST
            );
        }
        return normalized;
    }

    private NotificationDto dto(Notification n) {
        return new NotificationDto(
                n.id,
                n.type,
                n.category,
                n.title,
                n.body,
                n.relatedTransactionId,
                n.amount,
                n.direction,
                n.balanceAfter,
                "VND",
                n.readAt,
                n.readAt != null,
                n.createdAt
        );
    }
}
