package vn.anpay.backend.smartotp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.ledger.entity.LedgerTransaction;
import vn.anpay.backend.ledger.repository.LedgerTransactionRepository;
import vn.anpay.backend.otp.repository.OtpRepository;
import vn.anpay.backend.otp.service.OtpService;
import vn.anpay.backend.otp.service.OtpAttemptException;
import vn.anpay.backend.outbox.service.OtpDeliveryService;
import vn.anpay.backend.outbox.service.OutboxService;
import vn.anpay.backend.notification.repository.PushDeviceRepository;
import vn.anpay.backend.user.repository.UserRepository;
import vn.anpay.backend.wallet.repository.WalletRepository;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.*;
import java.util.*;
import java.util.HexFormat;

@Service
public class SmartOtpService {
    private final SmartOtpDeviceRepository devices;
    private final SmartOtpChallengeRepository challenges;
    private final LedgerTransactionRepository transactions;
    private final WalletRepository wallets;
    private final UserRepository users;
    private final OtpService otp;
    private final OtpRepository otpRepo;
    private final OtpDeliveryService delivery;
    private final OutboxService outbox;
    private final PushDeviceRepository pushDevices;
    private final boolean fcmEnabled;
    private final ObjectMapper mapper;
    private final SecureRandom random = new SecureRandom();

    public SmartOtpService(SmartOtpDeviceRepository devices, SmartOtpChallengeRepository challenges,
            LedgerTransactionRepository transactions, WalletRepository wallets, UserRepository users,
            OtpService otp, OtpRepository otpRepo, OtpDeliveryService delivery, OutboxService outbox,
            PushDeviceRepository pushDevices, @Value("${app.push.fcm.enabled:false}") boolean fcmEnabled,
            ObjectMapper mapper) {
        this.devices=devices; this.challenges=challenges; this.transactions=transactions;
        this.wallets=wallets; this.users=users; this.otp=otp; this.otpRepo=otpRepo;
        this.delivery=delivery; this.outbox=outbox; this.pushDevices=pushDevices;
        this.fcmEnabled=fcmEnabled; this.mapper=mapper;
    }

    public record Status(boolean active, UUID deviceId, String enrollmentChannel) {}
    public record Enrollment(UUID deviceId, String channel, Instant expiresAt) {}
    public record Authorization(UUID transactionId, UUID deviceId, String message,
                                Instant expiresAt, String transactionType, long amount, String reference, String recipient) {}

    @Transactional(readOnly=true)
    public Status status(UUID userId) {
        var active=devices.findFirstByUserIdAndStatusOrderByActivatedAtDesc(userId,"ACTIVE");
        return new Status(active.isPresent(), active.map(d->d.id).orElse(null), "EMAIL_OR_FCM_DEMO");
    }

    // Enrollment requires an independently delivered OTP (email, or FCM demo on a phone-only account).
    @Transactional
    public Enrollment startEnrollment(UUID userId, String key) {
        validatePublicKey(key);
        var current=devices.findFirstByUserIdAndStatusOrderByActivatedAtDesc(userId,"ACTIVE");
        if(current.isPresent()) throw fail("SMART_OTP_ALREADY_ACTIVE","Thiết bị Smart OTP đã được kích hoạt; hãy thu hồi thiết bị cũ trước");
        for(var old: devices.findByUserIdAndStatus(userId,"PENDING")) {
            if(old.enrollmentExpiresAt.isAfter(Instant.now())) throw fail("SMART_OTP_ENROLL_PENDING","Đã có yêu cầu kích hoạt đang chờ OTP");
        }
        // Enrollment has its own OTP delivery policy: do not depend on the
        // legacy TRANSFER_DEMO_PHONE_OTP_ENABLED switch (transaction OTPs are
        // replaced by transaction-bound Smart OTP signatures).
        var user=users.findById(userId).orElseThrow(()->fail("USER_NOT_FOUND","Không tìm thấy tài khoản"));
        if (!"ACTIVE".equals(user.status)) throw fail("ACCOUNT_UNAVAILABLE","Tài khoản không khả dụng");
        var channel = (user.email!=null && !user.email.isBlank()) ? "EMAIL" : "FCM_DEMO";
        if ("FCM_DEMO".equals(channel)) {
            if (user.phone==null || user.phone.isBlank())
                throw fail("SMART_OTP_CHANNEL_REQUIRED","Tài khoản chưa có phương thức nhận OTP");
            if (!fcmEnabled)
                throw fail("SMART_OTP_FCM_DISABLED","Cần bật FCM để xác minh kích hoạt Smart OTP demo");
            if (pushDevices.findByUserIdAndEnabledTrue(userId).isEmpty())
                throw fail("OTP_FCM_DEVICE_REQUIRED","Cần đăng ký thiết bị nhận OTP kích hoạt Smart OTP");
        }
        var d=new SmartOtpDevice();
        d.id=UUID.randomUUID(); d.userId=userId; d.publicKey=key; d.status="PENDING";
        d.createdAt=Instant.now(); d.enrollmentExpiresAt=d.createdAt.plus(Duration.ofMinutes(5));
        var issued=otp.issue(userId,"SMART_OTP_ACTIVATE",d.id);
        d.enrollmentOtpId=issued.challenge().id;
        devices.save(d);
        delivery.stage(issued.challenge().id, issued.plaintextCode());
        try {
            if("EMAIL".equals(channel)) {
                outbox.add("SMART_OTP",d.id,"SEND_OTP_EMAIL",mapper.writeValueAsString(Map.of(
                    "challengeId",issued.challenge().id.toString(),"email",user.email,"purpose","SMART_OTP_ACTIVATE")));
            } else {
                outbox.add("SMART_OTP",d.id,"SEND_OTP_FCM_DEMO",mapper.writeValueAsString(Map.of(
                    "challengeId",issued.challenge().id.toString(),"userId",userId.toString(),
                    "transactionId",d.id.toString(),"purpose","SMART_OTP_ACTIVATE")));
            }
        } catch(Exception ex) { throw new IllegalStateException("Failed to enqueue enrollment verification",ex); }
        return new Enrollment(d.id,channel,d.enrollmentExpiresAt);
    }

    @Transactional(noRollbackFor=OtpAttemptException.class)
    public Status confirmEnrollment(UUID userId, UUID deviceId, String code) {
        var d=devices.findByIdAndUserId(deviceId,userId).orElseThrow(()->fail("SMART_OTP_DEVICE_NOT_FOUND","Không tìm thấy thiết bị"));
        if(!"PENDING".equals(d.status) || !d.enrollmentExpiresAt.isAfter(Instant.now()))
            throw fail("SMART_OTP_ENROLL_EXPIRED","Yêu cầu kích hoạt đã hết hạn");
        var challenge=otpRepo.lockById(d.enrollmentOtpId).orElseThrow();
        otp.verifyLocked(challenge,"SMART_OTP_ACTIVATE",d.id,code);
        d.status="ACTIVE"; d.activatedAt=Instant.now(); devices.save(d);
        return status(userId);
    }

    @Transactional
    public void revoke(UUID userId, UUID deviceId) {
        var d=devices.findByIdAndUserId(deviceId,userId).orElseThrow(()->fail("SMART_OTP_DEVICE_NOT_FOUND","Không tìm thấy thiết bị"));
        if("ACTIVE".equals(d.status)) { d.status="REVOKED"; d.revokedAt=Instant.now(); devices.save(d); }
    }

    @Transactional(readOnly=true)
    public SmartOtpDevice requireActiveDevice(UUID userId) {
        return devices.findFirstByUserIdAndStatusOrderByActivatedAtDesc(userId,"ACTIVE")
                .orElseThrow(()->fail("SMART_OTP_REQUIRED","Hãy kích hoạt Smart OTP trước khi giao dịch"));
    }

    @Transactional
    public Instant issue(UUID userId, LedgerTransaction tx) {
        var d=requireActiveDevice(userId);
        var c=new SmartOtpChallenge();
        c.id=UUID.randomUUID(); c.transactionId=tx.id; c.userId=userId; c.deviceId=d.id;
        c.nonce=randomNonce(); c.transactionDigest=digest(tx);
        c.createdAt=Instant.now(); c.expiresAt=c.createdAt.plus(Duration.ofMinutes(5));
        challenges.save(c);
        return c.expiresAt;
    }

    @Transactional
    public Instant renew(UUID userId, LedgerTransaction tx) {
        var c=challenges.lockByTransactionId(tx.id).orElseThrow(()->fail("SMART_OTP_NOT_FOUND","Không tìm thấy yêu cầu xác thực"));
        checkOwnerAndPending(userId,tx,c);
        if(c.attemptCount>=5) throw fail("SMART_OTP_LOCKED","Giao dịch bị khóa do xác thực sai quá nhiều lần");
        var d=requireActiveDevice(userId);
        c.deviceId=d.id; c.nonce=randomNonce(); c.transactionDigest=digest(tx);
        c.expiresAt=Instant.now().plus(Duration.ofMinutes(5));
        challenges.save(c);
        return c.expiresAt;
    }

    @Transactional(readOnly=true)
    public Authorization authorization(UUID userId, UUID txId) {
        var tx=transactions.findById(txId).orElseThrow(()->fail("SMART_OTP_NOT_FOUND","Giao dịch không tồn tại"));
        var c=challenges.findByTransactionId(txId).orElseThrow(()->fail("SMART_OTP_NOT_FOUND","Không tìm thấy yêu cầu xác thực"));
        checkOwnerAndPending(userId,tx,c);
        if(c.expiresAt.isBefore(Instant.now())) throw fail("SMART_OTP_EXPIRED","Yêu cầu xác thực đã hết hạn; hãy làm mới");
        if(!digest(tx).equals(c.transactionDigest)) throw fail("SMART_OTP_TRANSACTION_CHANGED","Thông tin giao dịch đã thay đổi");
        return new Authorization(txId,c.deviceId,message(c),c.expiresAt,tx.type,tx.amount,tx.reference,recipientOf(tx));
    }

    // Derived from the immutable transaction the user is about to sign, not UI input.
    private String recipientOf(LedgerTransaction tx) {
        if (tx.receiverWalletId != null) {
            return wallets.findById(tx.receiverWalletId)
                    .map(w -> "Ví AnPay: " + w.walletCode).orElse("Ví nhận không xác định");
        }
        if (tx.metadata == null || tx.metadata.isBlank()) return "Tài khoản ngoài hệ thống";
        try {
            var data = mapper.readTree(tx.metadata);
            String bank = data.path("bankName").asText("Ngân hàng");
            String number = data.path("accountNumber").asText("");
            String holder = data.path("accountName").asText("");
            return bank + " · " + holder + " · " + number;
        } catch(Exception ignored) { return "Tài khoản ngoài hệ thống"; }
    }

    // Must be invoked under the caller's ledger transaction lock. Its row lock prevents replay.
    @Transactional(noRollbackFor=SmartOtpBadSignatureException.class)
    public void verifyAndConsume(UUID userId, LedgerTransaction tx, UUID deviceId, String signatureBase64, String smartOtpCode) {
        var c=challenges.lockByTransactionId(tx.id).orElseThrow(()->fail("SMART_OTP_NOT_FOUND","Không tìm thấy yêu cầu xác thực"));
        checkOwnerAndPending(userId,tx,c);
        if(!Objects.equals(c.deviceId,deviceId)) throw fail("SMART_OTP_DEVICE_MISMATCH","Thiết bị Smart OTP không hợp lệ");
        if(c.consumedAt!=null) throw fail("SMART_OTP_USED","Mã xác thực đã được sử dụng");
        if(!c.expiresAt.isAfter(Instant.now())) throw fail("SMART_OTP_EXPIRED","Mã xác thực hết hạn");
        if(c.attemptCount>=5) throw fail("SMART_OTP_LOCKED","Quá số lần xác thực sai");
        if(!digest(tx).equals(c.transactionDigest)) throw fail("SMART_OTP_TRANSACTION_CHANGED","Thông tin giao dịch thay đổi");
        var device=devices.findByIdAndUserId(deviceId,userId)
            .orElseThrow(()->fail("SMART_OTP_DEVICE_NOT_FOUND","Thiết bị không hợp lệ"));
        if(!"ACTIVE".equals(device.status)) throw fail("SMART_OTP_REVOKED","Thiết bị đã bị thu hồi");
        boolean valid=verifySignature(device.publicKey,message(c),signatureBase64)
                && codeFromSignature(signatureBase64).equals(smartOtpCode);
        if(!valid) {
            c.attemptCount++; challenges.save(c);
            throw new SmartOtpBadSignatureException(c.attemptCount>=5?"SMART_OTP_LOCKED":"SMART_OTP_INVALID",
                    "Chữ ký Smart OTP không hợp lệ");
        }
        c.consumedAt=Instant.now(); challenges.save(c);
    }

    /** Readable six-digit code derived from the signed one-time transaction challenge.
     * The server ALWAYS verifies the Ed25519 signature in addition to this code.
     */
    public static String codeFromSignature(String signatureBase64) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(signatureBase64));
            long n=((bytes[0]&255L)<<24)|((bytes[1]&255L)<<16)|((bytes[2]&255L)<<8)|(bytes[3]&255L);
            return "%06d".formatted(n%1_000_000);
        } catch(Exception e) { return ""; }
    }

    private void checkOwnerAndPending(UUID userId, LedgerTransaction tx, SmartOtpChallenge c) {
        var wallet=wallets.findByUserId(userId).orElseThrow(()->fail("WALLET_NOT_FOUND","Không có ví"));
        if(!userId.equals(c.userId) || !tx.id.equals(c.transactionId) || !wallet.id.equals(tx.senderWalletId))
            throw new BusinessException("FORBIDDEN","Không có quyền với giao dịch",HttpStatus.FORBIDDEN);
        if(!"AUTH_REQUIRED".equals(tx.status)) throw fail("SMART_OTP_STATE_INVALID","Giao dịch không chờ xác thực");
    }

    // Immutable transaction details + metadata are hashed, and nonce binds each authorization to the transaction.
    private String digest(LedgerTransaction tx) {
        String input=String.join("\n",tx.id.toString(),tx.type,Long.toString(tx.amount),
                Objects.toString(tx.senderWalletId,""),Objects.toString(tx.receiverWalletId,""),
                Objects.toString(tx.description,""),Objects.toString(tx.metadata,"{}"));
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    private String message(SmartOtpChallenge c) {
        return "ANPAY-SMARTOTP-V1\n"+c.transactionId+"\n"+c.nonce+"\n"+c.transactionDigest;
    }
    private String randomNonce() {
        byte[] b=new byte[32]; random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
    private void validatePublicKey(String pub) {
        try {
            if(pub==null || Base64.getDecoder().decode(pub).length!=32) throw new IllegalArgumentException();
        } catch(IllegalArgumentException ex) { throw fail("SMART_OTP_KEY_INVALID","Khóa thiết bị Smart OTP không hợp lệ"); }
    }
    public static boolean verifySignature(String publicKeyBase64, String message, String signatureBase64) {
        try {
            byte[] pub=Base64.getDecoder().decode(publicKeyBase64);
            byte[] sig=Base64.getDecoder().decode(signatureBase64);
            if(pub.length!=32 || sig.length!=64) return false;
            // SubjectPublicKeyInfo DER prefix for Ed25519 public key (RFC 8410).
            byte[] header=HexFormat.of().parseHex("302a300506032b6570032100");
            byte[] encoded=Arrays.copyOf(header,header.length+pub.length);
            System.arraycopy(pub,0,encoded,header.length,pub.length);
            var key=KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
            var verifier=Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(sig);
        } catch(Exception ex) { return false; }
    }
    private BusinessException fail(String code,String message) {
        return new BusinessException(code,message,HttpStatus.BAD_REQUEST);
    }
}
