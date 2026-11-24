package vn.anpay.backend.withdrawal.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.anpay.backend.common.api.ApiResponse;
import vn.anpay.backend.common.security.SecurityUtil;
import vn.anpay.backend.transfer.dto.ConfirmTransferRequest;
import vn.anpay.backend.withdrawal.dto.PrepareWithdrawalRequest;
import vn.anpay.backend.withdrawal.dto.WithdrawalResponse;
import vn.anpay.backend.withdrawal.service.WithdrawalService;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/withdrawals")
public class WithdrawalController {
    private final WithdrawalService service;
    public WithdrawalController(WithdrawalService service) { this.service = service; }

    @PostMapping("/prepare")
    public ApiResponse<WithdrawalResponse> prepare(@RequestHeader("Idempotency-Key") UUID key,
                                                   @Valid @RequestBody PrepareWithdrawalRequest request) {
        return ApiResponse.ok(service.prepare(SecurityUtil.userId(), key, request));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<WithdrawalResponse> confirm(@PathVariable UUID id,
                                                   @Valid @RequestBody ConfirmTransferRequest request) {
        return ApiResponse.ok(service.confirm(SecurityUtil.userId(), id, request.deviceId(),request.signature(),request.smartOtpCode()));
    }

    @PostMapping("/{id}/resend-otp")
    public ApiResponse<WithdrawalResponse> resend(@PathVariable UUID id) {
        return ApiResponse.ok(service.resend(SecurityUtil.userId(), id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<WithdrawalResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.ok(service.cancel(SecurityUtil.userId(), id));
    }

    @GetMapping("/{id}")
    public ApiResponse<WithdrawalResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(SecurityUtil.userId(), id));
    }
}
