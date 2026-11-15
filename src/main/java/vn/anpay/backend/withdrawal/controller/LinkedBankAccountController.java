package vn.anpay.backend.withdrawal.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.anpay.backend.common.api.ApiResponse;
import vn.anpay.backend.common.security.SecurityUtil;
import vn.anpay.backend.withdrawal.dto.LinkBankAccountRequest;
import vn.anpay.backend.withdrawal.dto.LinkedBankAccountResponse;
import vn.anpay.backend.withdrawal.service.LinkedBankAccountService;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/linked-bank-accounts")
public class LinkedBankAccountController {
    private final LinkedBankAccountService service;
    public LinkedBankAccountController(LinkedBankAccountService service) { this.service = service; }

    @GetMapping
    public ApiResponse<List<LinkedBankAccountResponse>> list() {
        return ApiResponse.ok(service.list(SecurityUtil.userId()));
    }

    @PostMapping
    public ApiResponse<LinkedBankAccountResponse> link(@Valid @RequestBody LinkBankAccountRequest request) {
        return ApiResponse.ok(service.link(SecurityUtil.userId(), request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> unlink(@PathVariable UUID id) {
        service.unlink(SecurityUtil.userId(), id);
        return ApiResponse.ok(null);
    }
}
