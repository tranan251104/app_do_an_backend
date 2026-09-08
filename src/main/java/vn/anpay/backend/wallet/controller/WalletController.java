package vn.anpay.backend.wallet.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.anpay.backend.common.api.ApiResponse;
import vn.anpay.backend.common.security.SecurityUtil;
import vn.anpay.backend.wallet.dto.WalletNumberAvailabilityResponse;
import vn.anpay.backend.wallet.dto.WalletNumberClaimRequest;
import vn.anpay.backend.wallet.dto.WalletResponse;
import vn.anpay.backend.wallet.dto.WalletSetupStatusResponse;
import vn.anpay.backend.wallet.service.WalletNumberService;
import vn.anpay.backend.wallet.service.WalletService;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {
    private final WalletService wallets;
    private final WalletNumberService numbers;

    public WalletController(WalletService wallets, WalletNumberService numbers) {
        this.wallets = wallets;
        this.numbers = numbers;
    }

    @GetMapping("/me")
    ApiResponse<WalletResponse> me() {
        return ApiResponse.ok(wallets.getByUser(SecurityUtil.userId()));
    }

    @GetMapping("/account-number/status")
    ApiResponse<WalletSetupStatusResponse> accountNumberStatus() {
        return ApiResponse.ok(numbers.status(SecurityUtil.userId()));
    }

    @GetMapping("/account-number/check")
    ApiResponse<WalletNumberAvailabilityResponse> checkAccountNumber(@RequestParam("code") String code) {
        return ApiResponse.ok(numbers.check(code));
    }

    @PostMapping("/account-number/claim")
    ApiResponse<WalletResponse> claimAccountNumber(@Valid @RequestBody WalletNumberClaimRequest request) {
        return ApiResponse.ok(numbers.claimCustom(SecurityUtil.userId(), request.walletCode()));
    }

    @PostMapping("/account-number/random")
    ApiResponse<WalletResponse> assignRandomAccountNumber() {
        return ApiResponse.ok(numbers.assignRandom(SecurityUtil.userId()));
    }
}
