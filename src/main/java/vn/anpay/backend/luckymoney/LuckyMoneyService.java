package vn.anpay.backend.luckymoney;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
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
import vn.anpay.backend.smartotp.SmartOtpService;
import vn.anpay.backend.smartotp.SmartOtpBadSignatureException;
import vn.anpay.backend.otp.repository.OtpRepository;
import vn.anpay.backend.otp.service.*;
import vn.anpay.backend.outbox.service.*;
import vn.anpay.backend.transfer.service.TransferIdempotencyLock;
import vn.anpay.backend.user.repository.UserRepository;
import vn.anpay.backend.wallet.entity.Wallet;
import vn.anpay.backend.wallet.repository.WalletRepository;
import java.time.*;
import java.util.*;

@Service
public class LuckyMoneyService {
    private final LuckyMoneyRepository envelopes;
    private final LedgerTransactionRepository txs;
    private final WalletRepository wallets;
    private final UserRepository users;
    private final LedgerAccountRepository accounts;
    private final LedgerEntryRepository entries;
    private final SmartOtpService smart;
    private final OtpRepository challenges;
    private final OtpService otp;
    private final OtpDeliveryService delivery;
    private final OutboxService outbox;
    private final NotificationRepository notifications;
    private final NotificationPushQueue push;
    private final TransferIdempotencyLock keys;
    private final ObjectMapper mapper;
    private final long min, max;

    public LuckyMoneyService(LuckyMoneyRepository envelopes, LedgerTransactionRepository txs,
            WalletRepository wallets, UserRepository users, LedgerAccountRepository accounts,
            LedgerEntryRepository entries, OtpRepository challenges, OtpService otp,
            OtpDeliveryService delivery, OutboxService outbox, NotificationRepository notifications,
            NotificationPushQueue push, TransferIdempotencyLock keys, ObjectMapper mapper, SmartOtpService smart,
            @Value("${app.money.transfer-min:10000}") long min,
            @Value("${app.money.transfer-max:50000000}") long max) {
        this.envelopes=envelopes; this.txs=txs; this.wallets=wallets; this.users=users;
        this.smart=smart;
        this.accounts=accounts; this.entries=entries; this.challenges=challenges; this.otp=otp;
        this.delivery=delivery; this.outbox=outbox; this.notifications=notifications;
        this.push=push; this.keys=keys; this.mapper=mapper; this.min=min; this.max=max;
    }

    public record View(UUID id, String reference, String status, Long amount, String template,
                       String greeting, String senderName, String senderWalletCode,
                       String receiverName, String receiverWalletCode, boolean isSender,
                       Instant otpExpiresAt, Instant expiresAt, Instant openedAt, Instant createdAt) {}

    @Transactional
    public View prepare(UUID user, UUID key, LuckyMoneyController.Prepare body) {
        keys.acquire(user,"LUCKY_MONEY",key);
        String code=body.recipientWalletCode().trim().toUpperCase(Locale.ROOT);
        String greeting=body.greeting().trim();
        var prior=envelopes.findBySenderUserIdAndIdempotencyKey(user,key);
        if(prior.isPresent()) {
            var e=prior.get(); var t=txs.findById(e.id).orElseThrow();
            if(t.amount!=body.amount() || !e.recipientWalletCode.equals(code)
                    || !e.template.equals(body.template()) || !e.greeting.equals(greeting))
                throw new BusinessException("IDEMPOTENCY_CONFLICT","Yêu cầu đã được dùng với nội dung khác",HttpStatus.CONFLICT);
            return view(user,e,t);
        }
        if(body.amount()<min || body.amount()>max) throw bad("AMOUNT_OUT_OF_RANGE","Số tiền ngoài hạn mức: " + min + "–" + max + " ₫");
        if(!Set.of("PROSPERITY","PEACE","JOY").contains(body.template()) || greeting.isBlank())
            throw bad("INVALID_ENVELOPE","Mẫu phong bao hoặc lời chúc không hợp lệ");
        smart.requireActiveDevice(user);
        var sender=wallets.findByUserId(user).orElseThrow(()->bad("WALLET_NOT_FOUND","Không tìm thấy ví"));
        var receiver=wallets.findByWalletCode(code).orElseThrow(()->bad("RECIPIENT_NOT_FOUND","Không tìm thấy ví nhận"));
        if(sender.id.equals(receiver.id)) throw bad("SELF_TRANSFER","Không thể gửi lì xì cho chính mình");
        active(sender); active(receiver);
        if(sender.availableBalance<body.amount()) throw bad("INSUFFICIENT_BALANCE","Số dư không đủ");
        var t=LedgerTransaction.create("LUCKY_MONEY",body.amount());
        t.status="AUTH_REQUIRED"; t.senderWalletId=sender.id; t.receiverWalletId=receiver.id;
        t.description=greeting; txs.save(t);
        var e=new LuckyMoneyEnvelope();
        e.id=t.id; e.senderUserId=user; e.receiverUserId=receiver.userId; e.idempotencyKey=key;
        e.recipientWalletCode=code; e.template=body.template(); e.greeting=greeting; e.createdAt=t.createdAt;
        e.otpExpiresAt=smart.issue(user,t); envelopes.save(e);
        return view(user,e,t);
    }

    @Transactional(noRollbackFor=SmartOtpBadSignatureException.class)
    public View confirm(UUID user, UUID id, UUID deviceId, String signature, String smartOtpCode) {
        var t=locked(id); var e=envelope(id); sender(user,e);
        if(Set.of("WAITING_OPEN","COMPLETED","EXPIRED").contains(t.status)) return view(user,e,t);
        awaitingOtp(t);
        var pair=lockedWallets(t); var sw=pair[0]; var rw=pair[1]; active(sw); active(rw);
        if(sw.availableBalance<t.amount) throw bad("INSUFFICIENT_BALANCE","Số dư không đủ");
        smart.verifyAndConsume(user,t,deviceId,signature,smartOtpCode);
        sw.availableBalance-=t.amount; sw.heldBalance+=t.amount; sw.updatedAt=Instant.now(); wallets.save(sw);
        e.expiresAt=Instant.now().plus(Duration.ofHours(24)); envelopes.save(e);
        t.status="WAITING_OPEN"; t.updatedAt=Instant.now(); txs.save(t);
        notice(e.receiverUserId,"LUCKY_MONEY_ARRIVED","Bạn có lì xì mới",
                name(e.senderUserId)+" gửi bạn một phong bao. Mở để nhận lời chúc và may mắn!",e);
        notice(user,"LUCKY_MONEY_SENT","Đã gửi phong bao lì xì",
                "Phong bao đang chờ mở. Tiền được giữ tối đa 24 giờ và tự hoàn nếu chưa nhận.",e);
        return view(user,e,t);
    }

    @Transactional
    public View resend(UUID user, UUID id) {
        var t=locked(id); var e=envelope(id); sender(user,e); awaitingOtp(t);
        e.otpExpiresAt=smart.renew(user,t);
        envelopes.save(e);
        return view(user,e,t);
    }

    @Transactional
    public View open(UUID user, UUID id) {
        var t=locked(id); var e=envelope(id);
        if(!user.equals(e.receiverUserId)) throw forbidden();
        if("COMPLETED".equals(t.status) || "EXPIRED".equals(t.status)) return view(user,e,t);
        if(!"WAITING_OPEN".equals(t.status)) throw bad("ENVELOPE_NOT_SENT","Phong bao chưa được gửi");
        if(expireLocked(e,t)) return view(user,e,t);
        var pair=lockedWallets(t); var sw=pair[0]; var rw=pair[1]; active(sw); active(rw);
        if(sw.heldBalance<t.amount) throw bad("HOLD_INCONSISTENT","Không thể xử lý số tiền đang giữ");
        sw.heldBalance-=t.amount; rw.availableBalance+=t.amount;
        sw.updatedAt=Instant.now(); rw.updatedAt=sw.updatedAt; wallets.save(sw); wallets.save(rw);
        var sa=accounts.findByOwnerTypeAndOwnerId("USER",sw.id).orElseThrow();
        var ra=accounts.findByOwnerTypeAndOwnerId("USER",rw.id).orElseThrow();
        entries.save(new LedgerEntry(id,sa.id,-t.amount,sw.availableBalance));
        entries.save(new LedgerEntry(id,ra.id,t.amount,rw.availableBalance));
        e.openedAt=Instant.now(); envelopes.save(e);
        t.status="COMPLETED"; t.completedAt=e.openedAt; t.updatedAt=e.openedAt; txs.save(t);
        balanceNotice(e.senderUserId,"LUCKY_MONEY_OPENED","Lì xì đã được nhận",name(e.receiverUserId)+" đã mở phong bao của bạn.",t,"OUT",sw.availableBalance);
        balanceNotice(user,"LUCKY_MONEY_RECEIVED","Nhận lì xì thành công","Bạn đã nhận lì xì từ "+name(e.senderUserId)+".",t,"IN",rw.availableBalance);
        return view(user,e,t);
    }

    @Transactional
    public View get(UUID user, UUID id) {
        var t=locked(id); var e=envelope(id); participant(user,e,t); expireLocked(e,t);
        return view(user,e,t);
    }

    @Transactional(readOnly=true)
    public Page<View> list(UUID user, String box, int page) {
        if(page<0 || !Set.of("sent","received").contains(box)) throw bad("INVALID_PAGE","Danh sách không hợp lệ");
        var paging=PageRequest.of(page,20);
        var rows="sent".equals(box) ? envelopes.findBySenderUserIdOrderByCreatedAtDesc(user,paging) : envelopes.received(user,paging);
        return rows.map(e->view(user,e,txs.findById(e.id).orElseThrow()));
    }

    @Transactional
    public void expire(UUID id) { var t=locked(id); expireLocked(envelope(id),t); }

    private boolean expireLocked(LuckyMoneyEnvelope e, LedgerTransaction t) {
        if(!"WAITING_OPEN".equals(t.status) || e.expiresAt.isAfter(Instant.now())) return false;
        var sw=wallets.lockById(t.senderWalletId).orElseThrow();
        if(sw.heldBalance<t.amount) throw bad("HOLD_INCONSISTENT","Không thể hoàn số tiền đang giữ");
        sw.heldBalance-=t.amount; sw.availableBalance+=t.amount; sw.updatedAt=Instant.now(); wallets.save(sw);
        t.status="EXPIRED"; t.updatedAt=Instant.now(); txs.save(t);
        notice(e.senderUserId,"LUCKY_MONEY_REFUNDED","Lì xì hết hạn, đã hoàn tiền","Phong bao chưa được mở sau 24 giờ. Tiền đã trở lại số dư khả dụng.",e);
        return true;
    }

    private Wallet[] lockedWallets(LedgerTransaction t) {
        UUID first=t.senderWalletId.compareTo(t.receiverWalletId)<0?t.senderWalletId:t.receiverWalletId;
        UUID second=first.equals(t.senderWalletId)?t.receiverWalletId:t.senderWalletId;
        var a=wallets.lockById(first).orElseThrow(); var b=wallets.lockById(second).orElseThrow();
        return a.id.equals(t.senderWalletId)?new Wallet[]{a,b}:new Wallet[]{b,a};
    }
    private LedgerTransaction locked(UUID id) {
        var t=txs.lockById(id).orElseThrow(()->bad("ENVELOPE_NOT_FOUND","Không tìm thấy phong bao"));
        if(!"LUCKY_MONEY".equals(t.type)) throw bad("ENVELOPE_NOT_FOUND","Không tìm thấy phong bao");
        return t;
    }
    private LuckyMoneyEnvelope envelope(UUID id) { return envelopes.findById(id).orElseThrow(()->bad("ENVELOPE_NOT_FOUND","Không tìm thấy phong bao")); }
    private void sender(UUID user, LuckyMoneyEnvelope e) { if(!user.equals(e.senderUserId)) throw forbidden(); }
    private void participant(UUID user, LuckyMoneyEnvelope e, LedgerTransaction t) {
        if(!user.equals(e.senderUserId) && (!user.equals(e.receiverUserId) || "AUTH_REQUIRED".equals(t.status))) throw forbidden();
    }
    private void awaitingOtp(LedgerTransaction t) { if(!"AUTH_REQUIRED".equals(t.status)) throw bad("ENVELOPE_STATE_INVALID","Phong bao không còn chờ Smart OTP"); }
    private void active(Wallet w) { if(!"ACTIVE".equals(w.status)) throw bad("WALLET_UNAVAILABLE","Ví không khả dụng"); }
    private View view(UUID user, LuckyMoneyEnvelope e, LedgerTransaction t) {
        boolean isSender=user.equals(e.senderUserId);
        boolean reveal=isSender || "COMPLETED".equals(t.status);
        return new View(e.id,t.reference,t.status,reveal?t.amount:null,e.template,e.greeting,
                name(e.senderUserId),wallets.findById(t.senderWalletId).orElseThrow().walletCode,
                name(e.receiverUserId),e.recipientWalletCode,isSender,
                isSender?e.otpExpiresAt:null,e.expiresAt,e.openedAt,e.createdAt);
    }
    private String name(UUID user) { return users.findById(user).map(u->u.fullName).orElse("Người dùng AnPay"); }
    private void notice(UUID user, String type, String title, String body, LuckyMoneyEnvelope e) {
        var n=Notification.proactive(user,type,"MY",title,body,"NORMAL","OPEN_LUCKY_MONEY",e.id.toString(),null);
        push.enqueue(notifications.save(n));
    }
    private void balanceNotice(UUID user, String type, String title, String body, LedgerTransaction t, String direction, long balance) {
        push.enqueue(notifications.save(Notification.balanceChange(user,type,title,body,t.id,t.amount,direction,balance)));
    }
    private BusinessException bad(String code, String message) { return new BusinessException(code,message,HttpStatus.BAD_REQUEST); }
    private BusinessException forbidden() { return new BusinessException("FORBIDDEN","Không có quyền truy cập phong bao",HttpStatus.FORBIDDEN); }
}
