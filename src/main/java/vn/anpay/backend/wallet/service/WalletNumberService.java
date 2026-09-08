package vn.anpay.backend.wallet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.ledger.entity.LedgerAccount;
import vn.anpay.backend.ledger.repository.LedgerAccountRepository;
import vn.anpay.backend.notification.entity.Notification;
import vn.anpay.backend.notification.repository.NotificationRepository;
import vn.anpay.backend.notification.service.NotificationPushQueue;
import vn.anpay.backend.user.repository.UserRepository;
import vn.anpay.backend.wallet.dto.WalletNumberAvailabilityResponse;
import vn.anpay.backend.wallet.dto.WalletResponse;
import vn.anpay.backend.wallet.dto.WalletSetupStatusResponse;
import vn.anpay.backend.wallet.entity.Wallet;
import vn.anpay.backend.wallet.repository.WalletRepository;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

@Service
public class WalletNumberService {
    private static final Logger log = LoggerFactory.getLogger(WalletNumberService.class);
    private static final String PREFIX = "ANP";
    private static final int DIGITS = 9;
    private static final int RANDOM_RETRIES = 30;

    private final WalletRepository wallets;
    private final UserRepository users;
    private final LedgerAccountRepository accounts;
    private final NotificationRepository notifications;
    private final NotificationPushQueue pushQueue;
    private final SecureRandom random = new SecureRandom();

    public WalletNumberService(
            WalletRepository wallets,
            UserRepository users,
            LedgerAccountRepository accounts,
            NotificationRepository notifications,
            NotificationPushQueue pushQueue
    ) {
        this.wallets = wallets;
        this.users = users;
        this.accounts = accounts;
        this.notifications = notifications;
        this.pushQueue = pushQueue;
    }

    public WalletSetupStatusResponse status(UUID userId) {
        return wallets.findByUserId(userId)
                .map(w -> new WalletSetupStatusResponse(true, w.walletCode))
                .orElseGet(() -> new WalletSetupStatusResponse(false, null));
    }

    public WalletNumberAvailabilityResponse check(String requestedCode) {
        String code = normalizeAndValidate(requestedCode);
        return new WalletNumberAvailabilityResponse(code, wallets.findByWalletCode(code).isEmpty());
    }

    @Transactional
    public WalletResponse claimCustom(UUID userId, String requestedCode) {
        String code = normalizeAndValidate(requestedCode);
        lockActiveUser(userId);

        var existing = wallets.findByUserId(userId);
        if (existing.isPresent()) {
            throw new BusinessException(
                    "WALLET_ALREADY_CREATED",
                    "Tài khoản AnPay đã có số tài khoản",
                    HttpStatus.CONFLICT
            );
        }

        if (wallets.findByWalletCode(code).isPresent()) {
            throw taken();
        }

        try {
            Wallet wallet = createWallet(userId, code);
            log.info("[WALLET_NUMBER] Custom account number claimed userId={} walletCode={}", userId, code);
            return dto(wallet);
        } catch (DataIntegrityViolationException ex) {
            // The UNIQUE constraint is the final protection if two users try to
            // claim the same beautiful number at the same instant.
            throw taken();
        }
    }

    @Transactional
    public WalletResponse assignRandom(UUID userId) {
        lockActiveUser(userId);

        var existing = wallets.findByUserId(userId);
        if (existing.isPresent()) {
            return dto(existing.get());
        }

        for (int i = 0; i < RANDOM_RETRIES; i++) {
            String code = randomCode();
            if (wallets.findByWalletCode(code).isPresent()) {
                continue;
            }

            // The database UNIQUE constraint on wallet_code is still the final
            // protection against an extremely unlikely concurrent collision.
            // We deliberately avoid retrying after a Hibernate constraint error
            // inside the same transaction because that transaction is no longer
            // safe to reuse. A client retry will generate a fresh number.
            try {
                Wallet wallet = createWallet(userId, code);
                log.info("[WALLET_NUMBER] Random account number assigned userId={} walletCode={}", userId, code);
                return dto(wallet);
            } catch (DataIntegrityViolationException ex) {
                throw new BusinessException(
                        "WALLET_NUMBER_GENERATION_FAILED",
                        "Xung đột khi cấp số tài khoản AnPay, vui lòng thử lại",
                        HttpStatus.CONFLICT
                );
            }
        }

        throw new BusinessException(
                "WALLET_NUMBER_GENERATION_FAILED",
                "Không thể cấp số tài khoản AnPay lúc này, vui lòng thử lại",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    private Wallet createWallet(UUID userId, String code) {
        Wallet wallet = wallets.saveAndFlush(new Wallet(userId, code));
        accounts.save(new LedgerAccount("WALLET:" + wallet.id, "USER", wallet.id));
        var notification = notifications.save(new Notification(
                userId,
                "WELCOME",
                "Chào mừng đến AnPay",
                "Số tài khoản AnPay của bạn là " + code + ".",
                null
        ));
        pushQueue.enqueue(notification);
        return wallet;
    }

    private void lockActiveUser(UUID userId) {
        var user = users.lockById(userId)
                .orElseThrow(() -> new BusinessException(
                        "USER_NOT_FOUND",
                        "Không tìm thấy tài khoản",
                        HttpStatus.NOT_FOUND
                ));
        if (!"ACTIVE".equals(user.status)) {
            throw new BusinessException(
                    "ACCOUNT_UNAVAILABLE",
                    "Tài khoản không khả dụng",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private String normalizeAndValidate(String input) {
        if (input == null) {
            throw invalid();
        }

        String value = input.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        String digits = value.startsWith(PREFIX) ? value.substring(PREFIX.length()) : value;

        if (!digits.matches("\\d{" + DIGITS + "}") || "000000000".equals(digits)) {
            throw invalid();
        }
        return PREFIX + digits;
    }

    private String randomCode() {
        // Random default numbers intentionally start from 100,000,000 so every
        // generated code is exactly 9 digits. Beautiful/custom numbers may have
        // a leading zero and are still accepted by normalizeAndValidate().
        int number = 100_000_000 + random.nextInt(900_000_000);
        return PREFIX + String.format(Locale.ROOT, "%09d", number);
    }

    private WalletResponse dto(Wallet w) {
        return new WalletResponse(w.id, w.walletCode, w.currency, w.availableBalance, w.heldBalance, w.status);
    }

    private BusinessException invalid() {
        return new BusinessException(
                "WALLET_NUMBER_INVALID",
                "Số tài khoản AnPay phải gồm ANP và đúng 9 chữ số",
                HttpStatus.BAD_REQUEST
        );
    }

    private BusinessException taken() {
        return new BusinessException(
                "WALLET_NUMBER_TAKEN",
                "Số tài khoản AnPay này đã có người sử dụng",
                HttpStatus.CONFLICT
        );
    }
}
