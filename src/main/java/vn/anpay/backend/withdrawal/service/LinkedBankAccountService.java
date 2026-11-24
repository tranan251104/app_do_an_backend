package vn.anpay.backend.withdrawal.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.user.repository.UserRepository;
import vn.anpay.backend.withdrawal.dto.LinkBankAccountRequest;
import vn.anpay.backend.withdrawal.dto.LinkedBankAccountResponse;
import vn.anpay.backend.withdrawal.entity.LinkedBankAccount;
import vn.anpay.backend.withdrawal.repository.LinkedBankAccountRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class LinkedBankAccountService {
    private final LinkedBankAccountRepository banks;
    private final UserRepository users;

    public LinkedBankAccountService(LinkedBankAccountRepository banks, UserRepository users) {
        this.banks = banks;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<LinkedBankAccountResponse> list(UUID userId) {
        return banks.findByUserIdAndStatusOrderByCreatedAtDesc(userId, "DEMO_LINKED")
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public LinkedBankAccountResponse link(UUID userId, LinkBankAccountRequest request) {
        if (!request.confirmOwnAccount()) {
            throw bad("BANK_OWNERSHIP_CONFIRMATION_REQUIRED", "Bạn cần xác nhận đây là tài khoản của chính mình");
        }
        var user = users.findById(userId).orElseThrow(() -> bad("USER_NOT_FOUND", "Không tìm thấy tài khoản"));
        if (!"ACTIVE".equals(user.status)) {
            throw bad("USER_UNAVAILABLE", "Tài khoản không khả dụng");
        }
        String number = request.accountNumber().trim();
        String bankBin = request.bankBin().trim();
        if (banks.findByUserIdAndBankBinAndAccountNumberAndStatus(userId, bankBin, number, "DEMO_LINKED").isPresent()) {
            throw new BusinessException("BANK_ALREADY_LINKED", "Tài khoản ngân hàng này đã được liên kết", HttpStatus.CONFLICT);
        }
        // Self-declaration only. No real bank ownership check is performed.
        var bank = new LinkedBankAccount(userId, bankBin, request.bankName().trim(),
                number, request.accountName().trim());
        return toResponse(banks.save(bank));
    }

    @Transactional
    public void unlink(UUID userId, UUID bankId) {
        var bank = ownedActive(userId, bankId);
        bank.status = "REMOVED";
        bank.updatedAt = Instant.now();
        banks.save(bank);
    }

    @Transactional(readOnly = true)
    public LinkedBankAccount ownedActive(UUID userId, UUID bankId) {
        var bank = banks.findById(bankId)
                .orElseThrow(() -> bad("LINKED_BANK_NOT_FOUND", "Không tìm thấy tài khoản ngân hàng liên kết"));
        if (!userId.equals(bank.userId) || !"DEMO_LINKED".equals(bank.status)) {
            throw bad("LINKED_BANK_NOT_FOUND", "Không tìm thấy tài khoản ngân hàng liên kết");
        }
        return bank;
    }

    private LinkedBankAccountResponse toResponse(LinkedBankAccount bank) {
        return new LinkedBankAccountResponse(bank.id, bank.bankBin, bank.bankName,
                mask(bank.accountNumber), bank.accountName, bank.status, true);
    }

    public static String mask(String number) {
        if (number == null || number.length() <= 4) return "****";
        return "****" + number.substring(number.length() - 4);
    }

    private BusinessException bad(String code, String message) {
        return new BusinessException(code, message, HttpStatus.BAD_REQUEST);
    }
}
