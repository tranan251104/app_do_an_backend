package vn.anpay.backend.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.ledger.entity.LedgerAccount;
import vn.anpay.backend.ledger.entity.LedgerEntry;
import vn.anpay.backend.ledger.entity.LedgerTransaction;
import vn.anpay.backend.ledger.repository.LedgerAccountRepository;
import vn.anpay.backend.ledger.repository.LedgerEntryRepository;
import vn.anpay.backend.ledger.repository.LedgerTransactionRepository;
import vn.anpay.backend.notification.entity.Notification;
import vn.anpay.backend.notification.repository.NotificationRepository;
import vn.anpay.backend.notification.service.NotificationPushQueue;
import vn.anpay.backend.payment.dto.MockPaymentConfirmRequest;
import vn.anpay.backend.payment.dto.MockPaymentDetailsResponse;
import vn.anpay.backend.payment.dto.PaymentIntentResponse;
import vn.anpay.backend.payment.entity.PaymentIntent;
import vn.anpay.backend.payment.repository.PaymentIntentRepository;
import vn.anpay.backend.wallet.repository.WalletRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class MockPaymentService {
    private static final Logger log = LoggerFactory.getLogger(MockPaymentService.class);
    private static final Set<String> ALLOWED_METHODS = Set.of("BANK", "QR", "WALLET");

    private final PaymentIntentRepository intents;
    private final WalletRepository wallets;
    private final LedgerTransactionRepository txs;
    private final LedgerAccountRepository accounts;
    private final LedgerEntryRepository entries;
    private final NotificationRepository notifications;
    private final NotificationPushQueue pushQueue;
    private final boolean enabled;
    private final String checkoutBaseUrl;
    private final long min;
    private final long max;
    private final SecureRandom random = new SecureRandom();

    public MockPaymentService(
            PaymentIntentRepository intents,
            WalletRepository wallets,
            LedgerTransactionRepository txs,
            LedgerAccountRepository accounts,
            LedgerEntryRepository entries,
            NotificationRepository notifications,
            NotificationPushQueue pushQueue,
            @Value("${app.mock-payment.enabled:false}") boolean enabled,
            @Value("${app.mock-payment.checkout-url:http://192.168.1.12:8080/mock-payment.html}") String checkoutBaseUrl,
            @Value("${app.money.topup-min}") long min,
            @Value("${app.money.topup-max}") long max
    ) {
        this.intents = intents;
        this.wallets = wallets;
        this.txs = txs;
        this.accounts = accounts;
        this.entries = entries;
        this.notifications = notifications;
        this.pushQueue = pushQueue;
        this.enabled = enabled;
        this.checkoutBaseUrl = checkoutBaseUrl;
        this.min = min;
        this.max = max;

        if (enabled) {
            log.warn("ANPAY MOCK PAYMENT IS ENABLED - DEV/LOCAL USE ONLY. Never enable this mode in production.");
        } else {
            log.info("ANPAY mock payment is disabled");
        }
    }

    @Transactional
    public PaymentIntentResponse create(UUID userId, UUID idempotencyKey, long amount) {
        ensureEnabled();
        if (amount < min || amount > max) {
            throw new BusinessException("AMOUNT_OUT_OF_RANGE", "Số tiền nạp ngoài hạn mức", HttpStatus.BAD_REQUEST);
        }

        var prior = intents.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (prior.isPresent()) {
            var p = prior.get();
            if (!"MOCK".equals(p.provider)) {
                throw new BusinessException("IDEMPOTENCY_CONFLICT", "Idempotency-Key đã được dùng cho cổng thanh toán khác", HttpStatus.CONFLICT);
            }
            return response(p);
        }

        var tx = LedgerTransaction.create("TOP_UP", amount);
        tx.provider = "MOCK";
        tx.description = "ANPAY mock top-up";
        txs.save(tx);

        var pi = new PaymentIntent();
        pi.id = UUID.randomUUID();
        pi.userId = userId;
        pi.transactionId = tx.id;
        pi.provider = "MOCK";
        pi.orderCode = uniqueOrderCode();
        pi.idempotencyKey = idempotencyKey;
        pi.amount = amount;
        pi.currency = "VND";
        pi.status = "PENDING";
        pi.createdAt = Instant.now();
        pi.updatedAt = pi.createdAt;
        pi.expiresAt = pi.createdAt.plus(Duration.ofMinutes(15));

        // Reuse payment_link_id as a one-time checkout capability token for DEV mock payment.
        // It is deliberately not returned as a separate API field; only embedded in checkoutUrl.
        pi.paymentLinkId = randomToken();
        pi.checkoutUrl = buildCheckoutUrl(pi.id, pi.paymentLinkId);
        intents.save(pi);

        log.info(
                "MOCK TOPUP created userId={} paymentIntentId={} orderCode={} amount={} expiresAt={}",
                userId, pi.id, pi.orderCode, amount, pi.expiresAt
        );
        return response(pi);
    }

    @Transactional
    public MockPaymentDetailsResponse details(UUID id, String token) {
        ensureEnabled();
        var pi = loadAndAuthorize(id, token, false);
        expireIfNeeded(pi);
        return detailsResponse(pi, null, null);
    }

    @Transactional
    public MockPaymentDetailsResponse confirm(UUID id, String token, MockPaymentConfirmRequest request) {
        ensureEnabled();
        var pi = loadAndAuthorize(id, token, true);
        expireIfNeeded(pi);

        if ("PAID".equals(pi.status)) {
            log.info("MOCK TOPUP confirm replay paymentIntentId={} already PAID", pi.id);
            return detailsResponse(pi, extractMethod(pi.providerReference), extractBank(pi.providerReference));
        }
        if (!"PENDING".equals(pi.status)) {
            throw new BusinessException("PAYMENT_NOT_PENDING", "Giao dịch không còn ở trạng thái chờ thanh toán", HttpStatus.CONFLICT);
        }

        String method = normalizeMethod(request == null ? null : request.method());
        String bankCode = normalizeBank(request == null ? null : request.bankCode());
        if ("BANK".equals(method) && bankCode == null) {
            throw new BusinessException("BANK_REQUIRED", "Vui lòng chọn ngân hàng", HttpStatus.BAD_REQUEST);
        }

        var walletRef = wallets.findByUserId(pi.userId)
                .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Không tìm thấy ví", HttpStatus.NOT_FOUND));
        var wallet = wallets.lockById(walletRef.id)
                .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Không tìm thấy ví", HttpStatus.NOT_FOUND));

        wallet.availableBalance += pi.amount;
        wallet.updatedAt = Instant.now();
        wallets.save(wallet);

        var clearing = accounts.findByCode("SYSTEM:MOCK_CLEARING")
                .orElseGet(() -> accounts.save(new LedgerAccount("SYSTEM:MOCK_CLEARING", "BANK_CLEARING", null)));
        var userAccount = accounts.findByOwnerTypeAndOwnerId("USER", wallet.id)
                .orElseThrow(() -> new BusinessException("LEDGER_ACCOUNT_NOT_FOUND", "Không tìm thấy tài khoản sổ cái", HttpStatus.INTERNAL_SERVER_ERROR));

        entries.save(new LedgerEntry(pi.transactionId, clearing.id, -pi.amount, 0));
        entries.save(new LedgerEntry(pi.transactionId, userAccount.id, pi.amount, wallet.availableBalance));

        String providerRef = buildProviderReference(method, bankCode);
        var tx = txs.findById(pi.transactionId)
                .orElseThrow(() -> new BusinessException("TRANSACTION_NOT_FOUND", "Không tìm thấy giao dịch", HttpStatus.NOT_FOUND));
        tx.status = "COMPLETED";
        tx.completedAt = Instant.now();
        tx.updatedAt = tx.completedAt;
        tx.provider = "MOCK";
        tx.providerReference = providerRef;
        txs.save(tx);

        pi.status = "PAID";
        pi.paidAt = Instant.now();
        pi.updatedAt = pi.paidAt;
        pi.providerReference = providerRef;
        intents.save(pi);

        var notification = notifications.save(Notification.balanceChange(
                pi.userId,
                "TOPUP_SUCCESS",
                "Nạp tiền thành công",
                "Ví AnPay đã được cộng " + formatVnd(pi.amount) + " qua cổng thanh toán ANPAY.",
                tx.id,
                pi.amount,
                "IN",
                wallet.availableBalance
        ));
        pushQueue.enqueue(notification);

        log.info(
                "MOCK TOPUP completed paymentIntentId={} orderCode={} amount={} method={} bank={} walletBalance={}",
                pi.id, pi.orderCode, pi.amount, method, bankCode, wallet.availableBalance
        );
        return detailsResponse(pi, method, bankCode);
    }

    @Transactional
    public MockPaymentDetailsResponse cancel(UUID id, String token) {
        ensureEnabled();
        var pi = loadAndAuthorize(id, token, true);

        if ("PAID".equals(pi.status)) {
            throw new BusinessException("PAYMENT_ALREADY_PAID", "Giao dịch đã được thanh toán", HttpStatus.CONFLICT);
        }
        if ("CANCELLED".equals(pi.status)) {
            return detailsResponse(pi, null, null);
        }

        pi.status = "CANCELLED";
        pi.updatedAt = Instant.now();
        intents.save(pi);

        txs.findById(pi.transactionId).ifPresent(tx -> {
            if (!"COMPLETED".equals(tx.status)) {
                tx.status = "FAILED";
                tx.updatedAt = Instant.now();
                txs.save(tx);
            }
        });

        log.info("MOCK TOPUP cancelled paymentIntentId={} orderCode={}", pi.id, pi.orderCode);
        return detailsResponse(pi, null, null);
    }

    private PaymentIntent loadAndAuthorize(UUID id, String token, boolean lock) {
        PaymentIntent pi = (lock ? intents.lockById(id) : intents.findById(id))
                .orElseThrow(() -> paymentNotFound());
        if (!"MOCK".equals(pi.provider) || !secureEquals(pi.paymentLinkId, token)) {
            throw paymentNotFound();
        }
        return pi;
    }

    private void expireIfNeeded(PaymentIntent pi) {
        if ("PENDING".equals(pi.status) && pi.expiresAt != null && pi.expiresAt.isBefore(Instant.now())) {
            pi.status = "EXPIRED";
            pi.updatedAt = Instant.now();
            intents.save(pi);
            txs.findById(pi.transactionId).ifPresent(tx -> {
                if (!"COMPLETED".equals(tx.status)) {
                    tx.status = "FAILED";
                    tx.updatedAt = Instant.now();
                    txs.save(tx);
                }
            });
            throw new BusinessException("PAYMENT_EXPIRED", "Phiên thanh toán đã hết hạn", HttpStatus.GONE);
        }
    }

    private BusinessException paymentNotFound() {
        // Do not reveal whether the ID or the capability token was wrong.
        return new BusinessException("PAYMENT_NOT_FOUND", "Không tìm thấy phiên thanh toán", HttpStatus.NOT_FOUND);
    }

    private String normalizeMethod(String value) {
        String method = value == null ? "BANK" : value.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_METHODS.contains(method)) {
            throw new BusinessException("INVALID_PAYMENT_METHOD", "Phương thức thanh toán không hợp lệ", HttpStatus.BAD_REQUEST);
        }
        return method;
    }

    private String normalizeBank(String value) {
        if (value == null || value.isBlank()) return null;
        String bank = value.trim().toUpperCase(Locale.ROOT);
        if (!bank.matches("[A-Z0-9_-]{2,16}")) {
            throw new BusinessException("INVALID_BANK_CODE", "Mã ngân hàng không hợp lệ", HttpStatus.BAD_REQUEST);
        }
        return bank;
    }

    private String buildProviderReference(String method, String bank) {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return "MOCK:" + method + ":" + (bank == null ? "NA" : bank) + ":" + suffix;
    }

    private String extractMethod(String providerReference) {
        if (providerReference == null) return null;
        String[] p = providerReference.split(":");
        return p.length >= 2 ? p[1] : null;
    }

    private String extractBank(String providerReference) {
        if (providerReference == null) return null;
        String[] p = providerReference.split(":");
        if (p.length < 3 || "NA".equals(p[2])) return null;
        return p[2];
    }

    private String buildCheckoutUrl(UUID paymentIntentId, String token) {
        String separator = checkoutBaseUrl.contains("?") ? "&" : "?";
        return checkoutBaseUrl + separator + "paymentIntentId=" + paymentIntentId + "&token=" + token;
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean secureEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private long uniqueOrderCode() {
        long code = System.currentTimeMillis() / 1000;
        while (intents.findByOrderCode(code).isPresent()) code++;
        return code;
    }

    private PaymentIntentResponse response(PaymentIntent p) {
        return new PaymentIntentResponse(p.id, p.orderCode, p.amount, p.status, p.checkoutUrl, p.expiresAt);
    }

    private MockPaymentDetailsResponse detailsResponse(PaymentIntent p, String method, String bank) {
        return new MockPaymentDetailsResponse(
                p.id,
                p.orderCode,
                p.amount,
                p.currency,
                p.status,
                "Ví ANPAY",
                p.expiresAt,
                method,
                bank
        );
    }

    private String formatVnd(long amount) {
        return String.format(Locale.US, "%,d", amount).replace(',', '.') + " VND";
    }

    private void ensureEnabled() {
        if (!enabled) {
            throw new BusinessException(
                    "MOCK_PAYMENT_DISABLED",
                    "Cổng thanh toán thử nghiệm đang tắt",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }
}
