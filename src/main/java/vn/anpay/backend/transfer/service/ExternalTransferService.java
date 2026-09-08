package vn.anpay.backend.transfer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
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
import vn.anpay.backend.otp.repository.OtpRepository;
import vn.anpay.backend.otp.service.OtpService;
import vn.anpay.backend.otp.service.OtpAttemptException;
import vn.anpay.backend.otp.service.OtpRateLimitService;
import vn.anpay.backend.outbox.service.OtpDeliveryService;
import vn.anpay.backend.outbox.service.OutboxService;
import vn.anpay.backend.payout.service.PayoutProvider;
import vn.anpay.backend.transfer.dto.ExternalTransferPrepareRequest;
import vn.anpay.backend.transfer.dto.ExternalTransferResponse;
import vn.anpay.backend.transfer.entity.TransferRequest;
import vn.anpay.backend.transfer.repository.TransferRequestRepository;
import vn.anpay.backend.user.repository.UserRepository;
import vn.anpay.backend.wallet.repository.WalletRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ExternalTransferService {
    private static final Logger log = LoggerFactory.getLogger(ExternalTransferService.class);
    private static final String PURPOSE = "EXTERNAL_TRANSFER";
    private static final String PROVIDER = "ANPAY_BANK_SIMULATOR";

    private final WalletRepository wallets;
    private final UserRepository users;
    private final LedgerAccountRepository accounts;
    private final LedgerTransactionRepository txs;
    private final LedgerEntryRepository entries;
    private final TransferRequestRepository requests;
    private final OtpRepository otpRepo;
    private final OtpService otp;
    private final OtpRateLimitService otpRateLimit;
    private final NotificationRepository notifications;
    private final NotificationPushQueue pushQueue;
    private final OutboxService outbox;
    private final OtpDeliveryService delivery;
    private final PayoutProvider payout;
    private final ObjectMapper mapper;
    private final TransferIdempotencyLock idempotencyLock;
    private final long min;
    private final long max;
    private final boolean enabled;

    public ExternalTransferService(
            WalletRepository wallets,
            UserRepository users,
            LedgerAccountRepository accounts,
            LedgerTransactionRepository txs,
            LedgerEntryRepository entries,
            TransferRequestRepository requests,
            OtpRepository otpRepo,
            OtpService otp,
            OtpRateLimitService otpRateLimit,
            NotificationRepository notifications,
            NotificationPushQueue pushQueue,
            OutboxService outbox,
            OtpDeliveryService delivery,
            PayoutProvider payout,
            ObjectMapper mapper,
            TransferIdempotencyLock idempotencyLock,
            @Value("${app.money.transfer-min}") long min,
            @Value("${app.money.transfer-max}") long max,
            @Value("${app.mock-external-transfer.enabled:true}") boolean enabled
    ) {
        this.wallets = wallets;
        this.users = users;
        this.accounts = accounts;
        this.txs = txs;
        this.entries = entries;
        this.requests = requests;
        this.otpRepo = otpRepo;
        this.otp = otp;
        this.otpRateLimit = otpRateLimit;
        this.notifications = notifications;
        this.pushQueue = pushQueue;
        this.outbox = outbox;
        this.delivery = delivery;
        this.payout = payout;
        this.mapper = mapper;
        this.idempotencyLock = idempotencyLock;
        this.min = min;
        this.max = max;
        this.enabled = enabled;

        if (enabled) {
            log.warn("ANPAY MOCK EXTERNAL BANK TRANSFER IS ENABLED - DEV/DEMO ONLY. No real bank receives money.");
        }
    }

    @Transactional
    public ExternalTransferResponse prepare(UUID userId, UUID idempotencyKey, ExternalTransferPrepareRequest request) {
        ensureEnabled();

        idempotencyLock.acquire(userId, "BANK", idempotencyKey);
        var existing = requests.findByUserIdAndRecipientTypeAndIdempotencyKey(userId, "BANK", idempotencyKey);
        if (existing.isPresent()) {
            var prior = existing.get();
            var priorTx = txs.findById(prior.transactionId).orElseThrow();
            var info = readMetadata(priorTx);
            if (priorTx.amount != request.amount()
                    || !Objects.equals(prior.note, request.note())
                    || !text(info, "bankBin").equals(request.bankBin().trim())
                    || !text(info, "bankName").equals(request.bankName().trim())
                    || !text(info, "accountNumber").equals(request.accountNumber().trim())
                    || !text(info, "accountName").equals(request.accountName().trim())) {
                throw new BusinessException(
                        "IDEMPOTENCY_CONFLICT",
                        "Idempotency-Key đã được dùng với nội dung khác",
                        HttpStatus.CONFLICT
                );
            }
            return response(prior);
        }

        if (request.amount() < min || request.amount() > max) {
            throw bad("AMOUNT_OUT_OF_RANGE", "Số tiền ngoài hạn mức");
        }

        var otpUser = requireOtpEmailUser(userId);

        var sender = wallets.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND", "Không tìm thấy ví", HttpStatus.NOT_FOUND));

        if (!"ACTIVE".equals(sender.status)) {
            throw bad("WALLET_UNAVAILABLE", "Ví không khả dụng");
        }
        if (sender.availableBalance < request.amount()) {
            throw bad("INSUFFICIENT_BALANCE", "Số dư không đủ");
        }

        var tx = LedgerTransaction.create("EXTERNAL_BANK_TRANSFER", request.amount());
        tx.status = "OTP_REQUIRED";
        tx.senderWalletId = sender.id;
        tx.receiverWalletId = null;
        tx.provider = PROVIDER;
        tx.description = buildDescription(request);
        tx.metadata = metadata(request);
        txs.save(tx);

        var issued = otp.issue(userId, PURPOSE, tx.id);
        delivery.stage(issued.challenge().id, issued.plaintextCode());

        var transfer = new TransferRequest();
        transfer.id = UUID.randomUUID();
        transfer.transactionId = tx.id;
        transfer.userId = userId;
        transfer.recipientType = "BANK";
        transfer.recipientReference = request.bankBin().trim() + ":" + request.accountNumber().trim();
        transfer.note = request.note();
        transfer.idempotencyKey = idempotencyKey;
        transfer.otpChallengeId = issued.challenge().id;
        transfer.expiresAt = issued.challenge().expiresAt;
        transfer.createdAt = Instant.now();
        requests.save(transfer);

        queueExternalTransferOtpEmail(
                otpUser.email,
                tx,
                issued.challenge().id,
                request.bankName(),
                request.accountNumber()
        );

        log.info(
                "MOCK EXTERNAL TRANSFER prepared userId={} txId={} bankBin={} account={} amount={} otpEmail={}",
                userId, tx.id, request.bankBin(), maskAccount(request.accountNumber()), request.amount(), maskEmail(otpUser.email)
        );

        return response(transfer);
    }

    @Transactional
    public ExternalTransferResponse resend(UUID userId, UUID txId) {
        ensureEnabled();

        var tx = txs.lockById(txId).orElseThrow(() -> bad("TRANSFER_NOT_FOUND", "Không tìm thấy giao dịch"));
        var transfer = bankTransfer(txId);
        requireSender(transfer, userId);
        if (!"OTP_REQUIRED".equals(tx.status)) {
            throw bad("TRANSFER_STATE_INVALID", "Giao dịch không còn chờ OTP");
        }

        var otpUser = requireOtpEmailUser(userId);
        var old = otpRepo.lockById(transfer.otpChallengeId).orElseThrow();
        if (old.resendAvailableAt.isAfter(Instant.now())) {
            throw new BusinessException(
                    "OTP_RESEND_TOO_SOON",
                    "Vui lòng chờ trước khi gửi lại OTP",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }

        int resendNumber = otpRateLimit.registerResend(txId);

        old.consumedAt = Instant.now();
        otpRepo.save(old);

        var issued = otp.issue(userId, PURPOSE, tx.id);
        transfer.otpChallengeId = issued.challenge().id;
        transfer.expiresAt = issued.challenge().expiresAt;
        requests.save(transfer);
        delivery.stage(issued.challenge().id, issued.plaintextCode());

        var info = readMetadata(tx);
        queueExternalTransferOtpEmail(
                otpUser.email,
                tx,
                issued.challenge().id,
                text(info, "bankName"),
                text(info, "accountNumber")
        );

        log.info(
                "[OTP_RESEND] External transfer OTP reissued userId={} txId={} resendNumber={} email={} expiresAt={}",
                userId, txId, resendNumber, maskEmail(otpUser.email), issued.challenge().expiresAt
        );

        return response(transfer);
    }

    @Transactional(noRollbackFor = OtpAttemptException.class)
    public ExternalTransferResponse confirm(UUID userId, UUID txId, String code) {
        ensureEnabled();

        var tx = txs.lockById(txId).orElseThrow(() -> bad("TRANSFER_NOT_FOUND", "Không tìm thấy giao dịch"));
        var transfer = bankTransfer(txId);
        requireSender(transfer, userId);

        if ("COMPLETED".equals(tx.status)) {
            return response(transfer);
        }

        if (!"OTP_REQUIRED".equals(tx.status)) {
            throw bad("TRANSFER_STATE_INVALID", "Trạng thái giao dịch không hợp lệ");
        }

        var sender = wallets.lockById(tx.senderWalletId).orElseThrow();
        if (!"ACTIVE".equals(sender.status)) {
            throw bad("WALLET_UNAVAILABLE", "Ví không khả dụng");
        }

        var challenge = otpRepo.lockById(transfer.otpChallengeId).orElseThrow();
        otp.verifyLocked(challenge, PURPOSE, tx.id, code);
        otpRateLimit.clear(tx.id);

        if (sender.availableBalance < tx.amount) {
            throw bad("INSUFFICIENT_BALANCE", "Số dư không đủ");
        }

        var info = readMetadata(tx);
        String bankBin = text(info, "bankBin");
        String bankName = text(info, "bankName");
        String accountNumber = text(info, "accountNumber");
        String accountName = text(info, "accountName");

        var payoutResult = payout.submit(new PayoutProvider.PayoutCommand(
                tx.reference,
                tx.amount,
                bankBin,
                accountNumber,
                tx.description
        ));

        if (!payoutResult.success()) {
            throw new BusinessException(
                    "MOCK_PAYOUT_FAILED",
                    payoutResult.message() == null ? "Không thể mô phỏng chuyển tiền ngân hàng" : payoutResult.message(),
                    HttpStatus.BAD_GATEWAY
            );
        }

        sender.availableBalance -= tx.amount;
        sender.updatedAt = Instant.now();
        wallets.save(sender);

        var userAccount = accounts.findByOwnerTypeAndOwnerId("USER", sender.id)
                .orElseThrow(() -> new BusinessException(
                        "LEDGER_ACCOUNT_NOT_FOUND",
                        "Không tìm thấy tài khoản sổ cái",
                        HttpStatus.INTERNAL_SERVER_ERROR
                ));

        String clearingCode = "BANK_CLEARING:" + bankBin;
        LedgerAccount clearing = accounts.findByCode(clearingCode)
                .orElseGet(() -> accounts.save(new LedgerAccount(clearingCode, "BANK_CLEARING", null)));

        entries.save(new LedgerEntry(tx.id, userAccount.id, -tx.amount, sender.availableBalance));
        entries.save(new LedgerEntry(tx.id, clearing.id, tx.amount, 0));

        tx.status = "COMPLETED";
        tx.provider = PROVIDER;
        tx.providerReference = payoutResult.providerReference();
        tx.completedAt = Instant.now();
        tx.updatedAt = tx.completedAt;
        txs.save(tx);

        var notification = notifications.save(Notification.balanceChange(
                userId,
                "EXTERNAL_TRANSFER_SENT",
                "Chuyển tiền ngân hàng thành công",
                "Bạn đã chuyển " + formatVnd(tx.amount) + " đến " + accountName + " - " + bankName + " " + maskAccount(accountNumber) + ".",
                tx.id,
                tx.amount,
                "OUT",
                sender.availableBalance
        ));
        pushQueue.enqueue(notification);

        log.info(
                "MOCK EXTERNAL TRANSFER completed userId={} txId={} reference={} bank={} account={} accountName={} amount={} providerRef={} balance={}",
                userId, tx.id, tx.reference, bankName, maskAccount(accountNumber), safeLogName(accountName),
                tx.amount, payoutResult.providerReference(), sender.availableBalance
        );

        return response(transfer);
    }

    public ExternalTransferResponse get(UUID userId, UUID txId) {
        ensureEnabled();

        var transfer = bankTransfer(txId);
        var tx = txs.findById(txId).orElseThrow();
        var sender = wallets.findByUserId(userId).orElseThrow();

        if (!sender.id.equals(tx.senderWalletId)) {
            throw new BusinessException("FORBIDDEN", "Không có quyền truy cập giao dịch", HttpStatus.FORBIDDEN);
        }
        return response(transfer);
    }

    private TransferRequest bankTransfer(UUID txId) {
        var transfer = requests.findByTransactionId(txId)
                .orElseThrow(() -> bad("TRANSFER_NOT_FOUND", "Không tìm thấy giao dịch"));
        if (!"BANK".equals(transfer.recipientType)) {
            throw bad("TRANSFER_TYPE_INVALID", "Giao dịch không phải chuyển ngân hàng");
        }
        return transfer;
    }

    private void requireSender(TransferRequest transfer, UUID userId) {
        if (!userId.equals(transfer.userId)) {
            throw new BusinessException("FORBIDDEN", "Không có quyền truy cập giao dịch", HttpStatus.FORBIDDEN);
        }
    }

    private ExternalTransferResponse response(TransferRequest transfer) {
        var tx = txs.findById(transfer.transactionId).orElseThrow();
        var info = readMetadata(tx);

        return new ExternalTransferResponse(
                tx.id,
                tx.reference,
                tx.amount,
                tx.status,
                text(info, "bankBin"),
                text(info, "bankName"),
                maskAccount(text(info, "accountNumber")),
                text(info, "accountName"),
                tx.provider,
                tx.providerReference,
                transfer.expiresAt,
                "OTP_REQUIRED".equals(tx.status),
                maskedEmailForTransaction(tx)
        );
    }

    private vn.anpay.backend.user.entity.User requireOtpEmailUser(UUID userId) {
        var user = users.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Không tìm thấy người dùng", HttpStatus.NOT_FOUND));
        if (user.email == null || user.email.isBlank()) {
            throw new BusinessException(
                    "OTP_EMAIL_NOT_AVAILABLE",
                    "Tài khoản AnPay chưa có email để nhận OTP",
                    HttpStatus.BAD_REQUEST
            );
        }
        return user;
    }

    private void queueExternalTransferOtpEmail(
            String email,
            LedgerTransaction tx,
            UUID challengeId,
            String bankName,
            String accountNumber
    ) {
        try {
            var payload = new LinkedHashMap<String, Object>();
            payload.put("challengeId", challengeId.toString());
            payload.put("email", email.trim());
            payload.put("purpose", PURPOSE);
            payload.put("reference", tx.reference);
            payload.put("amountFormatted", formatVnd(tx.amount));
            payload.put("bankName", bankName == null ? "" : bankName.trim());
            payload.put("accountNumberMasked", maskAccount(accountNumber));
            payload.put("expiresMinutes", 5);
            outbox.add(
                    "EXTERNAL_TRANSFER",
                    tx.id,
                    "SEND_OTP_EMAIL",
                    mapper.writeValueAsString(payload)
            );
            log.info(
                    "[OTP_MAIL_QUEUE] OTP email queued txId={} challengeId={} email={} bank={} amount={}",
                    tx.id, challengeId, maskEmail(email), bankName, tx.amount
            );
        } catch (Exception ex) {
            log.error(
                    "[OTP_MAIL_QUEUE_ERR] Could not queue OTP email txId={} challengeId={} email={} errorType={} message={}",
                    tx.id, challengeId, maskEmail(email), ex.getClass().getSimpleName(), ex.getMessage()
            );
            throw new BusinessException(
                    "OTP_EMAIL_QUEUE_FAILED",
                    "Không thể tạo yêu cầu gửi OTP qua email",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private String maskedEmailForTransaction(LedgerTransaction tx) {
        if (tx.senderWalletId == null) return "";
        return wallets.findById(tx.senderWalletId)
                .flatMap(wallet -> users.findById(wallet.userId))
                .map(user -> maskEmail(user.email))
                .orElse("");
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) return "***";
        String value = email.trim();
        int at = value.indexOf('@');
        if (at <= 0) return "***";
        String local = value.substring(0, at);
        String domain = value.substring(at);
        if (local.length() <= 2) return local.substring(0, 1) + "***" + domain;
        return local.substring(0, Math.min(3, local.length())) + "***" + domain;
    }

    private String formatVnd(long amount) {
        return String.format(Locale.US, "%,d", amount).replace(',', '.') + " VND";
    }

    private String metadata(ExternalTransferPrepareRequest request) {
        try {
            var value = new LinkedHashMap<String, Object>();
            value.put("simulation", true);
            value.put("bankBin", request.bankBin().trim());
            value.put("bankName", request.bankName().trim());
            value.put("accountNumber", request.accountNumber().trim());
            value.put("accountName", request.accountName().trim());
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(
                    "METADATA_BUILD_FAILED",
                    "Không thể tạo thông tin giao dịch",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMetadata(LedgerTransaction tx) {
        try {
            return mapper.readValue(tx.metadata, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : value.toString();
    }

    private String buildDescription(ExternalTransferPrepareRequest request) {
        String note = request.note() == null ? "" : request.note().trim();
        String base = "Chuyển đến " + request.bankName().trim()
                + " - " + maskAccount(request.accountNumber())
                + " - " + request.accountName().trim();
        String description = note.isEmpty() ? base : base + " | " + note;
        return description.length() <= 500 ? description : description.substring(0, 500);
    }

    private String maskAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) return "***";
        String value = accountNumber.trim();
        if (value.length() <= 4) return "****";
        return "****" + value.substring(value.length() - 4);
    }

    private String safeLogName(String name) {
        if (name == null || name.isBlank()) return "***";
        String value = name.trim().toUpperCase(Locale.ROOT);
        return value.length() <= 2 ? "**" : value.substring(0, 1) + "***";
    }

    private void ensureEnabled() {
        if (!enabled) {
            throw new BusinessException(
                    "MOCK_EXTERNAL_TRANSFER_DISABLED",
                    "Chuyển tiền ngân hàng mô phỏng đang tắt",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    private BusinessException bad(String code, String message) {
        return new BusinessException(code, message, HttpStatus.BAD_REQUEST);
    }
}
