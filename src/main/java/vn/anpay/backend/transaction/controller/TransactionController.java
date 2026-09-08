package vn.anpay.backend.transaction.controller;

import org.springframework.data.domain.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import vn.anpay.backend.common.api.ApiResponse;
import vn.anpay.backend.common.security.SecurityUtil;
import vn.anpay.backend.transaction.dto.TransactionResponse;
import vn.anpay.backend.transaction.service.TransactionService;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {
    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @GetMapping
    ApiResponse<Page<TransactionResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 50));
        var pageable = PageRequest.of(safePage, safeSize);
        return ApiResponse.ok(service.list(
                SecurityUtil.userId(),
                pageable,
                type,
                direction,
                status,
                keyword,
                from,
                to
        ));
    }

    @GetMapping("/{id}")
    ApiResponse<TransactionResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(SecurityUtil.userId(), id));
    }
}
