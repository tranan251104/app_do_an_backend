package vn.anpay.backend.withdrawal.dto;

import java.time.Instant;
import java.util.UUID;

public record WithdrawalResponse(
        UUID transactionId, String reference, long amount, String status,
        UUID linkedBankAccountId, String bankBin, String bankName,
        String accountNumberMasked, String accountName,
        boolean simulation, boolean otpRequired, String otpChannel,
        Instant expiresAt, String providerReference
) { }
