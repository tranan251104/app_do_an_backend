package vn.anpay.backend.withdrawal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.ledger.entity.*;
import vn.anpay.backend.ledger.repository.*;
import vn.anpay.backend.notification.entity.Notification;
import vn.anpay.backend.notification.repository.NotificationRepository;
import vn.anpay.backend.notification.service.NotificationPushQueue;
import vn.anpay.backend.otp.repository.OtpRepository;
import vn.anpay.backend.otp.service.*;
import vn.anpay.backend.outbox.service.OtpDeliveryService;
import vn.anpay.backend.outbox.service.OutboxService;
import vn.anpay.backend.payout.service.PayoutProvider;
import vn.anpay.backend.transfer.entity.TransferRequest;
import vn.anpay.backend.transfer.repository.TransferRequestRepository;
import vn.anpay.backend.transfer.service.InternalTransferOtpDispatcher;
import vn.anpay.backend.transfer.service.TransferIdempotencyLock;
import vn.anpay.backend.user.repository.UserRepository;
import vn.anpay.backend.wallet.repository.WalletRepository;
import vn.anpay.backend.withdrawal.dto.PrepareWithdrawalRequest;
import vn.anpay.backend.withdrawal.dto.WithdrawalResponse;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * End-to-end MOCK wallet withdrawal to a self-declared linked bank account.
 * No real bank is credited; never connect this service to a real payout rail.
 * Each debit/ledger/notification is committed atomically in PostgreSQL.
 */
@Service
public class WithdrawalService {
    private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);
    private static final String TYPE = "WITHDRAWAL";
    private static final String PURPOSE = "WITHDRAWAL";
    private static final String PROVIDER = "ANPAY_BANK_SIMULATOR";

    private final LinkedBankAccountService linkedBanks;
    private final WalletRepository wallets;
    private final UserRepository users;
    private final LedgerTransactionRepository txs;
    private final LedgerAccountRepository accounts;
    private final LedgerEntryRepository entries;
    private final TransferRequestRepository requests;
    private final OtpService otp;
    private final OtpRepository otpRepo;
    private final OtpRateLimitService rateLimit;
    private final OtpDeliveryService otpDelivery;
    private final InternalTransferOtpDispatcher otpDispatcher;
    private final OutboxService outbox;
    private final NotificationRepository notifications;
    private final NotificationPushQueue pushQueue;
    private final TransferIdempotencyLock idemLock;
    private final PayoutProvider payout;
    private final ObjectMapper mapper;
    private final long min;
    private final long max;
    private final boolean demoEnabled;

    public WithdrawalService(
            LinkedBankAccountService linkedBanks,
            WalletRepository wallets, UserRepository users,
            LedgerTransactionRepository txs, LedgerAccountRepository accounts,
            LedgerEntryRepository entries, TransferRequestRepository requests,
            OtpService otp, OtpRepository otpRepo, OtpRateLimitService rateLimit,
            OtpDeliveryService otpDelivery, InternalTransferOtpDispatcher otpDispatcher,
            OutboxService outbox, NotificationRepository notifications,
            NotificationPushQueue pushQueue, TransferIdempotencyLock idemLock,
            PayoutProvider payout, ObjectMapper mapper,
            @Value("${app.withdrawal.min:10000}") long min,
            @Value("${app.withdrawal.max:50000000}") long max,
            @Value("${app.withdrawal.demo-enabled:false}") boolean demoEnabled
    ) {
        this.linkedBanks = linkedBanks;
        this.wallets = wallets;
        this.users = users;
        this.txs = txs;
        this.accounts = accounts;
        this.entries = entries;
        this.requests = requests;
        this.otp = otp;
        this.otpRepo = otpRepo;
        this.rateLimit = rateLimit;
        this.otpDelivery = otpDelivery;
        this.otpDispatcher = otpDispatcher;
        this.outbox = outbox;
        this.notifications = notifications;
        this.pushQueue = pushQueue;
        this.idemLock = idemLock;
        this.payout = payout;
        this.mapper = mapper;
        this.min = min;
        this.max = max;
        this.demoEnabled = demoEnabled;
        if (demoEnabled) log.warn("DEMO WITHDRAWAL ENABLED: BANK IS NOT CREDITED; VIRTUAL FUNDS ONLY");
    }

    @Transactional
    public WithdrawalResponse prepare(UUID userId, UUID key, PrepareWithdrawalRequest request) {
        ensureEnabled();
        idemLock.acquire(userId, TYPE, key);
        var prior = requests.findByUserIdAndRecipientTypeAndIdempotencyKey(userId, TYPE, key);
        if (prior.isPresent()) {
            var p = prior.get();
            var tx = txs.findById(p.transactionId).orElseThrow();
            if (tx.amount != request.amount()
                    || !Objects.equals(p.recipientReference, request.linkedBankAccountId().toString())
                    || !Objects.equals(clean(p.note), clean(request.note()))) {
                throw new BusinessException("IDEMPOTENCY_CONFLICT",
                        "Yêu cầu này đã được sử dụng với nội dung khác", HttpStatus.CONFLICT);
            }
            return response(p, tx);
        }
        if (request.amount() < min || request.amount() > max) {
            throw bad("WITHDRAWAL_AMOUNT_INVALID", "Số tiền rút ngoài hạn mức");
        }
        var bank = linkedBanks.ownedActive(userId, request.linkedBankAccountId());
        var channel = otpDispatcher.channelFor(userId);
        var wallet = wallets.findByUserId(userId)
                .orElseThrow(() -> bad("WALLET_NOT_FOUND", "Không tìm thấy ví"));
        if (!"ACTIVE".equals(wallet.status)) throw bad("WALLET_UNAVAILABLE", "Ví không khả dụng");
        if (wallet.availableBalance < request.amount()) throw bad("INSUFFICIENT_BALANCE", "Số dư không đủ");

        var tx = LedgerTransaction.create(TYPE, request.amount());
        tx.status = "OTP_REQUIRED";
        tx.senderWalletId = wallet.id;
        tx.provider = PROVIDER;
        tx.description = "Rút tiền mô phỏng về " + bank.bankName + " " + LinkedBankAccountService.mask(bank.accountNumber);
        if (request.note() != null && !request.note().isBlank()) {
            tx.description = (tx.description + " | " + request.note().trim()).substring(0,
                    Math.min(500, (tx.description + " | " + request.note().trim()).length()));
        }
        tx.metadata = metadata(bank.id, bank.bankBin, bank.bankName, bank.accountNumber,
                bank.accountName, channel.name());
        txs.save(tx);

        var issued = otp.issue(userId, PURPOSE, tx.id);
        otpDelivery.stage(issued.challenge().id, issued.plaintextCode());
        var transfer = new TransferRequest();
        transfer.id = UUID.randomUUID();
        transfer.userId = userId;
        transfer.transactionId = tx.id;
        transfer.recipientType = TYPE;
        transfer.recipientReference = bank.id.toString();
        transfer.note = clean(request.note());
        transfer.idempotencyKey = key;
        transfer.otpChallengeId = issued.challenge().id;
        transfer.expiresAt = issued.challenge().expiresAt;
        transfer.createdAt = Instant.now();
        requests.save(transfer);
        queueOtp(userId, tx, issued.challenge().id, channel, bank.bankName, bank.accountNumber);
        log.info("[WITHDRAWAL_PREPARED] userId={} txId={} amount={} channel={}", userId, tx.id, tx.amount, channel);
        return response(transfer, tx);
    }

    @Transactional
    public WithdrawalResponse resend(UUID userId, UUID txId) {
        ensureEnabled();
        var tx = txs.lockById(txId).orElseThrow(() -> bad("WITHDRAWAL_NOT_FOUND", "Không tìm thấy giao dịch rút tiền"));
        var request = ownedRequest(userId, txId);
        if (!"OTP_REQUIRED".equals(tx.status)) throw bad("WITHDRAWAL_STATE_INVALID", "Giao dịch không chờ OTP");
        linkedBanks.ownedActive(userId, UUID.fromString(request.recipientReference));
        var info = readMetadata(tx);
        var channel = metadataChannel(info);
        if (channel != otpDispatcher.channelFor(userId)) {
            throw bad("OTP_CHANNEL_CHANGED", "Phương thức OTP đã thay đổi; vui lòng tạo giao dịch mới");
        }
        var previous = otpRepo.lockById(request.otpChallengeId).orElseThrow();
        if (previous.resendAvailableAt.isAfter(Instant.now())) {
            throw new BusinessException("OTP_RESEND_TOO_SOON", "Vui lòng chờ trước khi gửi lại OTP", HttpStatus.TOO_MANY_REQUESTS);
        }
        rateLimit.registerResend(tx.id);
        previous.consumedAt = Instant.now();
        otpRepo.save(previous);
        var issued = otp.issue(userId, PURPOSE, tx.id);
        otpDelivery.stage(issued.challenge().id, issued.plaintextCode());
        request.otpChallengeId = issued.challenge().id;
        request.expiresAt = issued.challenge().expiresAt;
        requests.save(request);
        queueOtp(userId, tx, issued.challenge().id, channel, value(info, "bankName"), value(info, "accountNumber"));
        return response(request, tx);
    }

    @Transactional(noRollbackFor = OtpAttemptException.class)
    public WithdrawalResponse confirm(UUID userId, UUID txId, String code) {
        ensureEnabled();
        var tx = txs.lockById(txId).orElseThrow(() -> bad("WITHDRAWAL_NOT_FOUND", "Không tìm thấy giao dịch rút tiền"));
        var request = ownedRequest(userId, txId);
        if ("COMPLETED".equals(tx.status) || "FAILED".equals(tx.status)) return response(request, tx);
        if (!"OTP_REQUIRED".equals(tx.status)) throw bad("WITHDRAWAL_STATE_INVALID", "Giao dịch không chờ OTP");

        // Lock the source wallet and validate again, after OTP and before any debit.
        var wallet = wallets.lockById(tx.senderWalletId).orElseThrow();
        if (!userId.equals(wallet.userId) || !"ACTIVE".equals(wallet.status)) {
            throw bad("WALLET_UNAVAILABLE", "Ví không khả dụng");
        }
        var bank = linkedBanks.ownedActive(userId, UUID.fromString(request.recipientReference));
        var challenge = otpRepo.lockById(request.otpChallengeId).orElseThrow();
        otp.verifyLocked(challenge, PURPOSE, tx.id, code);
        rateLimit.clear(tx.id);
        if (wallet.availableBalance < tx.amount) throw bad("INSUFFICIENT_BALANCE", "Số dư không đủ");

        // This is an in-process fake provider. Never reuse its synchronous behavior
        // for a real external bank, where reconciliation/timeout states are mandatory.
        var result = payout.submit(new PayoutProvider.PayoutCommand(
                tx.reference, tx.amount, bank.bankBin, bank.accountNumber, tx.description));
        if (!result.success()) {
            tx.status = "FAILED";
            tx.updatedAt = Instant.now();
            tx.providerReference = result.providerReference();
            txs.save(tx);
            var failed = notifications.save(new Notification(userId, "WITHDRAWAL_FAILED",
                    "Rút tiền mô phỏng thất bại", "Ví AnPay không bị trừ tiền.", tx.id));
            pushQueue.enqueue(failed);
            return response(request, tx);
        }
        wallet.availableBalance -= tx.amount;
        wallet.updatedAt = Instant.now();
        wallets.save(wallet);
        var userAccount = accounts.findByOwnerTypeAndOwnerId("USER", wallet.id)
                .orElseThrow(() -> new BusinessException("LEDGER_ACCOUNT_NOT_FOUND",
                        "Không tìm thấy tài khoản sổ cái", HttpStatus.INTERNAL_SERVER_ERROR));
        String codeKey = "BANK_CLEARING:" + bank.bankBin;
        var clearing = accounts.findByCode(codeKey)
                .orElseGet(() -> accounts.save(new LedgerAccount(codeKey, "BANK_CLEARING", null)));
        entries.save(new LedgerEntry(tx.id, userAccount.id, -tx.amount, wallet.availableBalance));
        entries.save(new LedgerEntry(tx.id, clearing.id, tx.amount, 0));
        tx.status = "COMPLETED";
        tx.providerReference = result.providerReference();
        tx.completedAt = Instant.now();
        tx.updatedAt = tx.completedAt;
        txs.save(tx);
        var notification = notifications.save(Notification.balanceChange(userId,
                "WITHDRAWAL_COMPLETED", "Rút tiền mô phỏng thành công",
                "Đã rút " + String.format(Locale.US, "%,d", tx.amount) + " VND về "
                        + bank.bankName + " " + LinkedBankAccountService.mask(bank.accountNumber)
                        + " (mô phỏng, ngân hàng không nhận tiền thật).",
                tx.id, tx.amount, "OUT", wallet.availableBalance));
        pushQueue.enqueue(notification);
        log.info("[WITHDRAWAL_COMPLETED] userId={} txId={} amount={}", userId, tx.id, tx.amount);
        return response(request, tx);
    }

    @Transactional
    public WithdrawalResponse cancel(UUID userId, UUID txId) {
        ensureEnabled();
        var tx = txs.lockById(txId)
                .orElseThrow(() -> bad("WITHDRAWAL_NOT_FOUND", "Không tìm thấy giao dịch rút tiền"));
        var request = ownedRequest(userId, txId);
        if ("CANCELLED".equals(tx.status)) return response(request, tx);
        if (!"OTP_REQUIRED".equals(tx.status)) {
            throw bad("WITHDRAWAL_STATE_INVALID", "Không thể hủy giao dịch đã xử lý");
        }
        var challenge = otpRepo.lockById(request.otpChallengeId).orElseThrow();
        challenge.consumedAt = Instant.now();
        otpRepo.save(challenge);
        tx.status = "CANCELLED";
        tx.updatedAt = Instant.now();
        txs.save(tx);
        return response(request, tx);
    }

    @Transactional(readOnly = true)
    public WithdrawalResponse get(UUID userId, UUID txId) {
        ensureEnabled();
        var request = ownedRequest(userId, txId);
        var tx = txs.findById(txId).orElseThrow();
        return response(request, tx);
    }

    private TransferRequest ownedRequest(UUID userId, UUID txId) {
        var request = requests.findByTransactionId(txId)
                .orElseThrow(() -> bad("WITHDRAWAL_NOT_FOUND", "Không tìm thấy giao dịch rút tiền"));
        if (!TYPE.equals(request.recipientType) || !userId.equals(request.userId)) {
            throw new BusinessException("FORBIDDEN", "Không có quyền xem giao dịch", HttpStatus.FORBIDDEN);
        }
        return request;
    }

    private void queueOtp(UUID userId, LedgerTransaction tx, UUID challengeId,
                          InternalTransferOtpDispatcher.Channel channel,
                          String bankName, String accountNumber) {
        if (channel == InternalTransferOtpDispatcher.Channel.FCM_DEMO) {
            otpDispatcher.enqueueFcmDemo(userId, tx.id, challengeId, "WITHDRAWAL");
            return;
        }
        String email = users.findById(userId).orElseThrow().email;
        if (email == null || email.isBlank()) throw bad("OTP_EMAIL_NOT_AVAILABLE", "Chưa có email nhận OTP");
        try {
            outbox.add(TYPE, tx.id, "SEND_OTP_EMAIL", mapper.writeValueAsString(Map.of(
                    "challengeId", challengeId.toString(), "email", email,
                    "purpose", PURPOSE, "reference", tx.reference,
                    "amountFormatted", String.format(Locale.US, "%,d VND", tx.amount),
                    "bankName", bankName, "accountNumberMasked", LinkedBankAccountService.mask(accountNumber))));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not enqueue withdrawal OTP", ex);
        }
    }

    private WithdrawalResponse response(TransferRequest request, LedgerTransaction tx) {
        var info = readMetadata(tx);
        return new WithdrawalResponse(tx.id, tx.reference, tx.amount, tx.status,
                UUID.fromString(request.recipientReference), value(info, "bankBin"),
                value(info, "bankName"), LinkedBankAccountService.mask(value(info, "accountNumber")),
                value(info, "accountName"), true, "OTP_REQUIRED".equals(tx.status),
                metadataChannel(info).name(), request.expiresAt, tx.providerReference);
    }

    private InternalTransferOtpDispatcher.Channel metadataChannel(Map<String, Object> info) {
        return "FCM_DEMO".equals(value(info, "otpChannel"))
                ? InternalTransferOtpDispatcher.Channel.FCM_DEMO
                : InternalTransferOtpDispatcher.Channel.EMAIL;
    }

    private String metadata(UUID bankId, String bin, String bankName, String account,
                            String accountName, String channel) {
        try {
            var data = new LinkedHashMap<String, Object>();
            data.put("simulation", true);
            data.put("linkedBankAccountId", bankId.toString());
            data.put("bankBin", bin);
            data.put("bankName", bankName);
            data.put("accountNumber", account);
            data.put("accountName", accountName);
            data.put("otpChannel", channel);
            return mapper.writeValueAsString(data);
        } catch (Exception ex) { throw new IllegalStateException("Could not serialize withdrawal metadata", ex); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMetadata(LedgerTransaction tx) {
        try { return mapper.readValue(tx.metadata, Map.class); }
        catch (Exception ex) { throw new IllegalStateException("Invalid withdrawal metadata", ex); }
    }

    private String value(Map<String, Object> info, String key) {
        Object found = info.get(key);
        return found == null ? "" : found.toString();
    }

    private String clean(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    private BusinessException bad(String code, String message) {
        return new BusinessException(code, message, HttpStatus.BAD_REQUEST);
    }

    private void ensureEnabled() {
        if (!demoEnabled) throw new BusinessException("WITHDRAWAL_DEMO_DISABLED",
                "Rút tiền giả lập chưa được bật trên Backend", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
