package vn.anpay.backend.notification.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import vn.anpay.backend.common.api.ApiResponse;
import vn.anpay.backend.common.security.SecurityUtil;
import vn.anpay.backend.notification.dto.NotificationDto;
import vn.anpay.backend.notification.dto.UnreadCountResponse;
import vn.anpay.backend.notification.service.NotificationService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<Page<NotificationDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "ALL") String category
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 50));
        return ApiResponse.ok(service.list(
                SecurityUtil.userId(),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt", "id")),
                unreadOnly,
                category
        ));
    }

    @GetMapping("/unread-count")
    ApiResponse<UnreadCountResponse> unreadCount(
            @RequestParam(defaultValue = "ALL") String category
    ) {
        return ApiResponse.ok(new UnreadCountResponse(
                service.unreadCount(SecurityUtil.userId(), category)
        ));
    }

    @PatchMapping("/{id}/read")
    ApiResponse<Void> read(@PathVariable UUID id) {
        service.read(SecurityUtil.userId(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/read-all")
    ApiResponse<Integer> all(
            @RequestParam(defaultValue = "ALL") String category
    ) {
        return ApiResponse.ok(service.all(SecurityUtil.userId(), category));
    }
}
