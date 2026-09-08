package vn.anpay.backend.notification.controller;

import org.springframework.web.bind.annotation.*;
import vn.anpay.backend.common.api.ApiResponse;
import vn.anpay.backend.common.security.SecurityUtil;
import vn.anpay.backend.notification.dto.PushDeviceDto;
import vn.anpay.backend.notification.dto.RegisterPushDeviceRequest;
import vn.anpay.backend.notification.dto.UnregisterPushDeviceRequest;
import vn.anpay.backend.notification.service.PushDeviceService;

@RestController
@RequestMapping("/api/v1/push/devices")
public class PushDeviceController {
    private final PushDeviceService service;

    public PushDeviceController(PushDeviceService service) {
        this.service = service;
    }

    @PostMapping
    ApiResponse<PushDeviceDto> register(@RequestBody RegisterPushDeviceRequest request) {
        return ApiResponse.ok(service.register(
                SecurityUtil.userId(),
                request == null ? null : request.token(),
                request == null ? null : request.platform(),
                request == null ? null : request.deviceId()
        ));
    }

    @PostMapping("/unregister")
    ApiResponse<Void> unregister(@RequestBody UnregisterPushDeviceRequest request) {
        service.unregister(SecurityUtil.userId(), request == null ? null : request.token());
        return ApiResponse.ok(null);
    }
}
