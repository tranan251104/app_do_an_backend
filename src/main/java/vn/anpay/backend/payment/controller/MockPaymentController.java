package vn.anpay.backend.payment.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.anpay.backend.common.api.ApiResponse;
import vn.anpay.backend.common.security.SecurityUtil;
import vn.anpay.backend.payment.dto.MockPaymentConfirmRequest;
import vn.anpay.backend.payment.dto.MockPaymentDetailsResponse;
import vn.anpay.backend.payment.dto.PaymentIntentResponse;
import vn.anpay.backend.payment.dto.TopupRequest;
import vn.anpay.backend.payment.service.MockPaymentService;

import java.util.UUID;

@RestController
public class MockPaymentController {
    private final MockPaymentService service;

    public MockPaymentController(MockPaymentService service) {
        this.service = service;
    }

    // Authenticated: the Flutter app creates a mock top-up for the logged-in wallet.
    @PostMapping("/api/v1/payments/topups/mock")
    ApiResponse<PaymentIntentResponse> create(
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody TopupRequest request
    ) {
        return ApiResponse.ok(service.create(SecurityUtil.userId(), key, request.amount()));
    }

    // Public DEV checkout endpoints. Access is protected by the random token embedded in checkoutUrl.
    @GetMapping("/api/v1/dev/mock-payments/{id}")
    ApiResponse<MockPaymentDetailsResponse> details(
            @PathVariable UUID id,
            @RequestParam String token
    ) {
        return ApiResponse.ok(service.details(id, token));
    }

    @PostMapping("/api/v1/dev/mock-payments/{id}/confirm")
    ApiResponse<MockPaymentDetailsResponse> confirm(
            @PathVariable UUID id,
            @RequestParam String token,
            @RequestBody(required = false) MockPaymentConfirmRequest request
    ) {
        return ApiResponse.ok(service.confirm(id, token, request));
    }

    @PostMapping("/api/v1/dev/mock-payments/{id}/cancel")
    ApiResponse<MockPaymentDetailsResponse> cancel(
            @PathVariable UUID id,
            @RequestParam String token
    ) {
        return ApiResponse.ok(service.cancel(id, token));
    }
}
