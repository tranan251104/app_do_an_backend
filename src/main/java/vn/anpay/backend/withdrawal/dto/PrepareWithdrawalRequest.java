package vn.anpay.backend.withdrawal.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record PrepareWithdrawalRequest(
        @NotNull UUID linkedBankAccountId,
        @Min(10000) long amount,
        @Size(max = 300) String note
) { }
