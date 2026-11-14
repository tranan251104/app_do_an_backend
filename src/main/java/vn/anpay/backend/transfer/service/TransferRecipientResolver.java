package vn.anpay.backend.transfer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.anpay.backend.auth.service.PhoneNumberNormalizer;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.transfer.dto.PrepareTransferRequest;
import vn.anpay.backend.user.repository.UserRepository;
import vn.anpay.backend.wallet.entity.Wallet;
import vn.anpay.backend.wallet.repository.WalletRepository;

import java.util.Locale;

/** Resolves a local recipient to a wallet ID. External-bank transfers are NOT routed here. */
@Service
public class TransferRecipientResolver {
    private final WalletRepository wallets;
    private final UserRepository users;
    private final PhoneNumberNormalizer phoneNumbers;
    private final boolean allowUnverifiedDemoPhone;

    public TransferRecipientResolver(
            WalletRepository wallets,
            UserRepository users,
            PhoneNumberNormalizer phoneNumbers,
            @Value("${app.transfer.allow-unverified-phone-recipient:false}") boolean allowUnverifiedDemoPhone
    ) {
        this.wallets = wallets;
        this.users = users;
        this.phoneNumbers = phoneNumbers;
        this.allowUnverifiedDemoPhone = allowUnverifiedDemoPhone;
    }

    public Wallet resolve(PrepareTransferRequest request) {
        String phone = blankToNull(request.recipientPhone());
        String account = blankToNull(request.recipientWalletCode());
        if (phone != null && account != null) {
            throw bad("RECIPIENT_AMBIGUOUS", "Chỉ nhập số tài khoản hoặc số điện thoại người nhận");
        }
        if (phone != null) {
            return resolvePhone(phone);
        }
        if (account == null) {
            throw bad("RECIPIENT_REQUIRED", "Cần số tài khoản hoặc số điện thoại người nhận");
        }
        String compact = account.replaceAll("[\\s().-]", "");
        // Compatibility with the existing Flutter field `recipientWalletCode`.
        // Numeric 9-digit AnPay account numbers are handled separately below.
        if (compact.matches("0[0-9]{9}") || compact.matches("(?:\\+84|84|0084)[0-9]{9}")) {
            return resolvePhone(account);
        }
        String code = compact.toUpperCase(Locale.ROOT);
        if (code.matches("[0-9]{9}")) code = "ANP" + code;
        if (!code.matches("ANP[0-9]{9}")) {
            throw bad("RECIPIENT_INVALID", "Số tài khoản AnPay không hợp lệ");
        }
        return wallets.findByWalletCode(code)
                .orElseThrow(() -> bad("RECIPIENT_NOT_FOUND", "Không tìm thấy tài khoản nhận"));
    }

    private Wallet resolvePhone(String raw) {
        String phone = phoneNumbers.normalize(raw);
        var user = users.findByPhoneNormalized(phone)
                .orElseThrow(() -> bad("RECIPIENT_NOT_FOUND", "Không tìm thấy tài khoản nhận"));
        if (!"ACTIVE".equals(user.status)) {
            throw bad("RECIPIENT_NOT_FOUND", "Không tìm thấy tài khoản nhận");
        }
        if (!user.phoneVerified && !allowUnverifiedDemoPhone) {
            throw bad("RECIPIENT_PHONE_UNVERIFIED", "Số điện thoại nhận chưa được xác minh");
        }
        return wallets.findByUserId(user.id)
                .orElseThrow(() -> bad("RECIPIENT_WALLET_NOT_READY", "Người nhận chưa tạo số tài khoản AnPay"));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException bad(String code, String message) {
        return new BusinessException(code, message, HttpStatus.BAD_REQUEST);
    }
}
