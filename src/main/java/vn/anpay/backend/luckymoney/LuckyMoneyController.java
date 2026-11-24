package vn.anpay.backend.luckymoney;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import vn.anpay.backend.common.api.ApiResponse;
import vn.anpay.backend.common.security.SecurityUtil;
import vn.anpay.backend.transfer.dto.ConfirmTransferRequest;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lucky-money")
public class LuckyMoneyController {
    private final LuckyMoneyService service;
    public LuckyMoneyController(LuckyMoneyService service) { this.service=service; }

    public record Prepare(@NotBlank @Size(max=32) String recipientWalletCode,
                          @Positive long amount, @NotBlank @Size(max=32) String template,
                          @NotBlank @Size(max=200) String greeting) {}

    @PostMapping("/prepare")
    public ApiResponse<LuckyMoneyService.View> prepare(@RequestHeader("Idempotency-Key") UUID key,
                                                     @Valid @RequestBody Prepare body) {
        return ApiResponse.ok(service.prepare(SecurityUtil.userId(),key,body));
    }
    @GetMapping
    public ApiResponse<?> list(@RequestParam(defaultValue="received") String box,
                               @RequestParam(defaultValue="0") int page) {
        return ApiResponse.ok(service.list(SecurityUtil.userId(),box,page));
    }
    @GetMapping("/{id}")
    public ApiResponse<LuckyMoneyService.View> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(SecurityUtil.userId(),id));
    }
    @PostMapping("/{id}/confirm")
    public ApiResponse<LuckyMoneyService.View> confirm(@PathVariable UUID id,
                                                     @Valid @RequestBody ConfirmTransferRequest body) {
        return ApiResponse.ok(service.confirm(SecurityUtil.userId(),id,body.deviceId(),body.signature(),body.smartOtpCode()));
    }
    @PostMapping("/{id}/resend-otp")
    public ApiResponse<LuckyMoneyService.View> resend(@PathVariable UUID id) {
        return ApiResponse.ok(service.resend(SecurityUtil.userId(),id));
    }
    @PostMapping("/{id}/open")
    public ApiResponse<LuckyMoneyService.View> open(@PathVariable UUID id) {
        return ApiResponse.ok(service.open(SecurityUtil.userId(),id));
    }
}
