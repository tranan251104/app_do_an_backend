package vn.anpay.backend.transaction.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.ledger.entity.LedgerTransaction;
import vn.anpay.backend.ledger.repository.LedgerTransactionRepository;
import vn.anpay.backend.payment.repository.PaymentIntentRepository;
import vn.anpay.backend.transaction.dto.TransactionResponse;
import vn.anpay.backend.user.repository.UserRepository;
import vn.anpay.backend.wallet.entity.Wallet;
import vn.anpay.backend.wallet.repository.WalletRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class TransactionService {
    private final LedgerTransactionRepository txs;
    private final WalletRepository wallets;
    private final UserRepository users;
    private final PaymentIntentRepository paymentIntents;
    private final ObjectMapper mapper;
    private final int defaultHistoryDays;
    private final int maxHistoryRangeDays;

    public TransactionService(
            LedgerTransactionRepository txs,
            WalletRepository wallets,
            UserRepository users,
            PaymentIntentRepository paymentIntents,
            ObjectMapper mapper,
            @Value("${app.history.default-days:30}") int defaultHistoryDays,
            @Value("${app.history.max-range-days:366}") int maxHistoryRangeDays
    ) {
        this.txs = txs;
        this.wallets = wallets;
        this.users = users;
        this.paymentIntents = paymentIntents;
        this.mapper = mapper;
        this.defaultHistoryDays = Math.max(1, defaultHistoryDays);
        this.maxHistoryRangeDays = Math.max(this.defaultHistoryDays, maxHistoryRangeDays);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> list(
            UUID userId,
            Pageable pageable,
            String type,
            String direction,
            String status,
            String keyword,
            Instant from,
            Instant to
    ) {
        var wallet = requireWallet(userId);
        String normalizedKeyword = normalize(keyword);
        TimeRange range = resolveTimeRange(from, to);
        return txs.searchForUser(
                userId,
                wallet.id,
                normalize(type),
                normalize(direction),
                normalize(status),
                normalizedKeyword,
                range.from(),
                range.to(),
                pageable
        ).map(tx -> dto(userId, wallet, tx));
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(UUID userId, UUID id) {
        var wallet = requireWallet(userId);
        var tx = txs.findById(id).orElseThrow(() -> new BusinessException(
                "TRANSACTION_NOT_FOUND",
                "Không tìm thấy giao dịch",
                HttpStatus.NOT_FOUND
        ));

        if (!canView(userId, wallet.id, tx)) {
            throw new BusinessException("FORBIDDEN", "Không có quyền truy cập", HttpStatus.FORBIDDEN);
        }
        return dto(userId, wallet, tx);
    }


    private TimeRange resolveTimeRange(Instant requestedFrom, Instant requestedTo) {
        Instant effectiveTo = requestedTo == null ? Instant.now() : requestedTo;
        Instant effectiveFrom = requestedFrom == null
                ? effectiveTo.minus(Duration.ofDays(defaultHistoryDays))
                : requestedFrom;

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessException(
                    "INVALID_DATE_RANGE",
                    "Ngày bắt đầu không được sau ngày kết thúc",
                    HttpStatus.BAD_REQUEST
            );
        }

        Duration range = Duration.between(effectiveFrom, effectiveTo);
        if (range.compareTo(Duration.ofDays(maxHistoryRangeDays)) > 0) {
            throw new BusinessException(
                    "DATE_RANGE_TOO_LARGE",
                    "Mỗi lần tra cứu lịch sử tối đa " + maxHistoryRangeDays + " ngày",
                    HttpStatus.BAD_REQUEST
            );
        }
        return new TimeRange(effectiveFrom, effectiveTo);
    }

    private record TimeRange(Instant from, Instant to) {
    }

    private Wallet requireWallet(UUID userId) {
        return wallets.findByUserId(userId).orElseThrow(() -> new BusinessException(
                "WALLET_NOT_FOUND",
                "Không tìm thấy ví",
                HttpStatus.NOT_FOUND
        ));
    }

    private boolean canView(UUID userId, UUID walletId, LedgerTransaction tx) {
        if (walletId.equals(tx.senderWalletId) || walletId.equals(tx.receiverWalletId)) return true;
        return paymentIntents.findByTransactionId(tx.id)
                .map(pi -> userId.equals(pi.userId))
                .orElse(false);
    }

    private TransactionResponse dto(UUID userId, Wallet wallet, LedgerTransaction tx) {
        String direction = direction(userId, wallet.id, tx);
        String title = title(tx.type, direction);
        String counterpartyName = null;
        String counterpartyAccount = null;
        String bankName = null;
        String bankCode = null;
        boolean simulated = false;

        if ("INTERNAL_TRANSFER".equals(tx.type)) {
            UUID otherWalletId = "OUT".equals(direction) ? tx.receiverWalletId : tx.senderWalletId;
            if (otherWalletId != null) {
                var otherWallet = wallets.findById(otherWalletId).orElse(null);
                if (otherWallet != null) {
                    counterpartyAccount = otherWallet.walletCode;
                    counterpartyName = users.findById(otherWallet.userId)
                            .map(u -> u.fullName)
                            .orElse("Người dùng AnPay");
                }
            }
        } else if ("EXTERNAL_BANK_TRANSFER".equals(tx.type)) {
            var metadata = readMetadata(tx.metadata);
            bankName = text(metadata, "bankName");
            bankCode = text(metadata, "bankBin");
            counterpartyName = text(metadata, "accountName");
            counterpartyAccount = maskAccount(text(metadata, "accountNumber"));
            simulated = bool(metadata, "simulation") || "ANPAY_BANK_SIMULATOR".equals(tx.provider);
        } else if ("TOP_UP".equals(tx.type)) {
            counterpartyName = "ANPAY Payment Gateway";
            counterpartyAccount = tx.provider == null ? "ANPAY" : tx.provider;
            simulated = "MOCK".equalsIgnoreCase(tx.provider);
        }

        String counterparty = firstNonBlank(counterpartyName, counterpartyAccount, bankName);
        return new TransactionResponse(
                tx.id,
                tx.reference,
                tx.type,
                tx.status,
                tx.amount,
                tx.fee,
                "VND",
                direction,
                title,
                counterparty,
                blankToNull(counterpartyName),
                blankToNull(counterpartyAccount),
                blankToNull(bankName),
                blankToNull(bankCode),
                tx.description,
                tx.provider,
                tx.providerReference,
                simulated,
                tx.createdAt,
                tx.completedAt
        );
    }

    private String direction(UUID userId, UUID walletId, LedgerTransaction tx) {
        if (walletId.equals(tx.senderWalletId)) return "OUT";
        if (walletId.equals(tx.receiverWalletId)) return "IN";
        if ("TOP_UP".equals(tx.type) && paymentIntents.findByTransactionId(tx.id)
                .map(pi -> userId.equals(pi.userId))
                .orElse(false)) return "IN";
        return "UNKNOWN";
    }

    private String title(String type, String direction) {
        if ("INTERNAL_TRANSFER".equals(type)) {
            return "IN".equals(direction) ? "Nhận tiền" : "Chuyển tiền";
        }
        if ("EXTERNAL_BANK_TRANSFER".equals(type)) return "Chuyển ngân hàng";
        if ("TOP_UP".equals(type)) return "Nạp tiền";
        return "Giao dịch AnPay";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMetadata(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return mapper.readValue(json, Map.class);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private boolean bool(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(value));
    }

    private String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) return null;
        String value = accountNumber.trim();
        if (value.length() <= 4) return "****";
        return "****" + value.substring(value.length() - 4);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
