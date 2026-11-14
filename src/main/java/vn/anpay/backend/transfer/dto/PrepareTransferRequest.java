package vn.anpay.backend.transfer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Exactly one recipient is preferred: recipientWalletCode OR recipientPhone.
 * For backwards compatibility, recipientWalletCode also accepts a Vietnamese
 * mobile number. Existing 3-argument Java callers remain supported.
 */
public record PrepareTransferRequest(
        String recipientWalletCode,
        String recipientPhone,
        @Min(10000) long amount,
        @Size(max = 500) String note
) {
    public PrepareTransferRequest(String recipientWalletCode, long amount, String note) {
        this(recipientWalletCode, null, amount, note);
    }
}
