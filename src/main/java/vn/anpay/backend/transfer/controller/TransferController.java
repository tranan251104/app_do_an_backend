package vn.anpay.backend.transfer.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.anpay.backend.common.api.ApiResponse;
import vn.anpay.backend.common.security.SecurityUtil;
import vn.anpay.backend.transfer.dto.*;
import vn.anpay.backend.transfer.service.TransferService;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/transfers") public class TransferController {
    private final TransferService s;
    public TransferController(TransferService s) {
        this.s=s;

    }
    @PostMapping("/prepare")ApiResponse<TransferResponse> prepare(@RequestHeader("Idempotency-Key")UUID key,@Valid @RequestBody PrepareTransferRequest r) {
        return ApiResponse.ok(s.prepare(SecurityUtil.userId(),key,r));

    }
    @PostMapping("/{id}/resend-otp")ApiResponse<TransferResponse> resend(@PathVariable UUID id) {
        return ApiResponse.ok(s.resend(SecurityUtil.userId(),id));

    }
    @PostMapping("/{id}/confirm")ApiResponse<TransferResponse> confirm(@PathVariable UUID id,@Valid @RequestBody ConfirmTransferRequest r) {
        return ApiResponse.ok(s.confirm(SecurityUtil.userId(),id,r.otp()));

    }
    @GetMapping("/{id}")ApiResponse<TransferResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(s.get(SecurityUtil.userId(),id));

    }

}
