package vn.anpay.backend.qr.controller;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import vn.anpay.backend.common.api.ApiResponse;
import vn.anpay.backend.common.security.SecurityUtil;
import vn.anpay.backend.qr.dto.QrResponse;
import vn.anpay.backend.qr.service.QrService;

record ResolveQrRequest(@NotBlank String value) {

}
@RestController
public class QrController {
    private final QrService s;
    public QrController(QrService s) {
        this.s=s;

    }
    @GetMapping("/api/v1/wallets/me/qr")ApiResponse<QrResponse> mine() {
        return ApiResponse.ok(s.mine(SecurityUtil.userId()));

    }
    @PostMapping("/api/v1/qr/resolve")ApiResponse<QrResponse> resolve(@RequestBody ResolveQrRequest r) {
        return ApiResponse.ok(s.resolve(r.value()));

    }

}
