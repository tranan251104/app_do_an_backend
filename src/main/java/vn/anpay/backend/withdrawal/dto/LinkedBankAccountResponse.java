package vn.anpay.backend.withdrawal.dto;

import java.util.UUID;

public record LinkedBankAccountResponse(
        UUID id, String bankBin, String bankName,
        String accountNumberMasked, String accountName,
        String status, boolean simulatedOwnership
) { }
