package vn.anpay.backend.transfer.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.anpay.backend.common.api.ApiResponse;
import vn.anpay.backend.common.security.SecurityUtil;
import vn.anpay.backend.transfer.dto.ConfirmTransferRequest;
import vn.anpay.backend.transfer.dto.ExternalTransferPrepareRequest;
import vn.anpay.backend.transfer.dto.ExternalTransferResponse;
import vn.anpay.backend.transfer.service.ExternalTransferService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/external-transfers")
public class ExternalTransferController {
    private final ExternalTransferService service;

    public ExternalTransferController(ExternalTransferService service) {
        this.service = service;
    }

    @PostMapping("/prepare")
    ApiResponse<ExternalTransferResponse> prepare(
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody ExternalTransferPrepareRequest request
    ) {
        return ApiResponse.ok(service.prepare(SecurityUtil.userId(), key, request));
    }

    @PostMapping("/{id}/resend-otp")
    ApiResponse<ExternalTransferResponse> resend(@PathVariable UUID id) {
        return ApiResponse.ok(service.resend(SecurityUtil.userId(), id));
    }

    @PostMapping("/{id}/confirm")
    ApiResponse<ExternalTransferResponse> confirm(
            @PathVariable UUID id,
            @Valid @RequestBody ConfirmTransferRequest request
    ) {
        return ApiResponse.ok(service.confirm(SecurityUtil.userId(), id, request.otp()));
    }

    @GetMapping("/{id}")
    ApiResponse<ExternalTransferResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(SecurityUtil.userId(), id));
    }
}
