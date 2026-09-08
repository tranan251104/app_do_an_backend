package vn.anpay.backend.transfer.service;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.anpay.backend.common.exception.BusinessException;
import vn.anpay.backend.ledger.entity.*;
import vn.anpay.backend.ledger.repository.*;
import vn.anpay.backend.notification.entity.Notification;
import vn.anpay.backend.notification.repository.NotificationRepository;
import vn.anpay.backend.notification.service.NotificationPushQueue;
import vn.anpay.backend.otp.repository.OtpRepository;
import vn.anpay.backend.otp.service.OtpService;
import vn.anpay.backend.otp.service.OtpAttemptException;
import vn.anpay.backend.outbox.service.*;
import vn.anpay.backend.transfer.dto.*;
import vn.anpay.backend.transfer.entity.TransferRequest;
import vn.anpay.backend.transfer.repository.TransferRequestRepository;
import vn.anpay.backend.user.repository.UserRepository;
import vn.anpay.backend.wallet.entity.Wallet;
import vn.anpay.backend.wallet.repository.WalletRepository;
import java.time.*;
import java.util.*;

@Service
public class TransferService {
    private final WalletRepository wallets;
    private final UserRepository users;
    private final LedgerAccountRepository accounts;
    private final LedgerTransactionRepository txs;
    private final LedgerEntryRepository entries;
    private final TransferRequestRepository requests;
    private final OtpRepository otpRepo;
    private final OtpService otp;
    private final NotificationRepository notifications;
    private final NotificationPushQueue pushQueue;
    private final OutboxService outbox;
    private final OtpDeliveryService delivery;
    private final ObjectMapper mapper;
    private final TransferIdempotencyLock idempotencyLock;
    private final long min,max;
    public TransferService(WalletRepository w,UserRepository u,LedgerAccountRepository a,LedgerTransactionRepository t,LedgerEntryRepository e,TransferRequestRepository r,OtpRepository or,OtpService o,NotificationRepository n,NotificationPushQueue pushQueue,OutboxService out,OtpDeliveryService d,ObjectMapper mapper,TransferIdempotencyLock idempotencyLock,@Value("${app.money.transfer-min}")long min,@Value("${app.money.transfer-max}")long max) {
        wallets=w;
        users=u;
        accounts=a;
        txs=t;
        entries=e;
        requests=r;
        otpRepo=or;
        otp=o;
        notifications=n;
        this.pushQueue=pushQueue;
        outbox=out;
        delivery=d;
        this.mapper=mapper;
        this.idempotencyLock=idempotencyLock;
        this.min=min;
        this.max=max;

    }
    @Transactional
    public TransferResponse prepare(UUID userId,UUID idem,PrepareTransferRequest r) {
        idempotencyLock.acquire(userId,"ANPAY",idem);
        var existing=requests.findByUserIdAndRecipientTypeAndIdempotencyKey(userId,"ANPAY",idem);
        if(existing.isPresent()) {
            var prior=existing.get();
            var priorTx=txs.findById(prior.transactionId).orElseThrow();
            if(priorTx.amount!=r.amount()
                    ||!Objects.equals(prior.recipientReference,r.recipientWalletCode().trim())
                    ||!Objects.equals(prior.note,r.note())) {
                throw new BusinessException("IDEMPOTENCY_CONFLICT","Idempotency-Key đã được dùng với nội dung khác",HttpStatus.CONFLICT);
            }
            return response(prior);
        }
        if(r.amount()<min||r.amount()>max)throw bad("AMOUNT_OUT_OF_RANGE","Số tiền ngoài hạn mức");
        Wallet sender=wallets.findByUserId(userId).orElseThrow();
        Wallet receiver=wallets.findByWalletCode(r.recipientWalletCode().trim()).orElseThrow(()->bad("RECIPIENT_NOT_FOUND","Không tìm thấy tài khoản nhận"));
        if(sender.id.equals(receiver.id))throw bad("SELF_TRANSFER","Không thể chuyển tiền cho chính mình");
        if(!"ACTIVE".equals(sender.status)||!"ACTIVE".equals(receiver.status))throw bad("WALLET_UNAVAILABLE","Ví không khả dụng");
        if(sender.availableBalance<r.amount())throw bad("INSUFFICIENT_BALANCE","Số dư không đủ");
        var tx=LedgerTransaction.create("INTERNAL_TRANSFER",r.amount());
        tx.status="OTP_REQUIRED";
        tx.senderWalletId=sender.id;
        tx.receiverWalletId=receiver.id;
        tx.description=r.note();
        txs.save(tx);
        var issued=otp.issue(userId,"TRANSFER",tx.id);
        delivery.stage(issued.challenge().id,issued.plaintextCode());
        var tr=new TransferRequest();
        tr.id=UUID.randomUUID();
        tr.transactionId=tx.id;
        tr.userId=userId;
        tr.recipientType="ANPAY";
        tr.recipientReference=receiver.walletCode;
        tr.note=r.note();
        tr.idempotencyKey=idem;
        tr.otpChallengeId=issued.challenge().id;
        tr.expiresAt=issued.challenge().expiresAt;
        tr.createdAt=Instant.now();
        requests.save(tr);
        users.findById(userId).filter(x->x.email!=null).ifPresent(u-> {
            try {
                outbox.add("TRANSFER",tx.id,"SEND_OTP_EMAIL",mapper.writeValueAsString(Map.of("challengeId",issued.challenge().id.toString(),"email",u.email,"purpose","TRANSFER")));
            } catch(Exception ignored) {

            }

        });
        return response(tr);

    }
    @Transactional
    public TransferResponse resend(UUID userId,UUID txId) {
        var tx=txs.lockById(txId).orElseThrow(()->bad("TRANSFER_NOT_FOUND","Không tìm thấy giao dịch"));
        var tr=internalTransfer(txId);
        requireSender(tr,userId);
        if(!"OTP_REQUIRED".equals(tx.status))throw bad("TRANSFER_STATE_INVALID","Giao dịch không còn chờ OTP");
        var old=otpRepo.lockById(tr.otpChallengeId).orElseThrow();
        if(old.resendAvailableAt.isAfter(Instant.now()))throw new BusinessException("OTP_RESEND_TOO_SOON","Vui lòng chờ trước khi gửi lại OTP",HttpStatus.TOO_MANY_REQUESTS);
        old.consumedAt=Instant.now();
        otpRepo.save(old);
        var issued=otp.issue(userId,"TRANSFER",tx.id);
        tr.otpChallengeId=issued.challenge().id;
        tr.expiresAt=issued.challenge().expiresAt;
        requests.save(tr);
        delivery.stage(issued.challenge().id,issued.plaintextCode());
        users.findById(userId).filter(x->x.email!=null).ifPresent(u-> {
            try {
                outbox.add("TRANSFER",tx.id,"SEND_OTP_EMAIL",mapper.writeValueAsString(Map.of("challengeId",issued.challenge().id.toString(),"email",u.email,"purpose","TRANSFER")));
            } catch(Exception ignored) {

            }

        });
        return response(tr);

    }
    // A wrong code only changes attemptCount. All later failures still roll back
    // OTP consumption, balances, ledger entries and notifications together.
    @Transactional(noRollbackFor = OtpAttemptException.class)
    public TransferResponse confirm(UUID userId,UUID txId,String code) {
        // Read the transaction under lock first so concurrent confirms/resends
        // cannot use an old status or an obsolete OTP challenge from the JPA cache.
        var tx=txs.lockById(txId).orElseThrow(()->bad("TRANSFER_NOT_FOUND","Không tìm thấy giao dịch"));
        var tr=internalTransfer(txId);
        requireSender(tr,userId);
        if("COMPLETED".equals(tx.status))return response(tr);
        if(!"OTP_REQUIRED".equals(tx.status))throw bad("TRANSFER_STATE_INVALID","Trạng thái giao dịch không hợp lệ");
        UUID first=tx.senderWalletId.compareTo(tx.receiverWalletId)<0?tx.senderWalletId:tx.receiverWalletId;
        UUID second=first.equals(tx.senderWalletId)?tx.receiverWalletId:tx.senderWalletId;
        Wallet w1=wallets.lockById(first).orElseThrow();
        Wallet w2=wallets.lockById(second).orElseThrow();
        Wallet sender=w1.id.equals(tx.senderWalletId)?w1:w2;
        Wallet receiver=w1.id.equals(tx.receiverWalletId)?w1:w2;
        if(!"ACTIVE".equals(sender.status)||!"ACTIVE".equals(receiver.status))throw bad("WALLET_UNAVAILABLE","Ví không khả dụng");
        var challenge=otpRepo.lockById(tr.otpChallengeId).orElseThrow();
        otp.verifyLocked(challenge,"TRANSFER",tx.id,code);
        if(sender.availableBalance<tx.amount)throw bad("INSUFFICIENT_BALANCE","Số dư không đủ");
        sender.availableBalance-=tx.amount;
        receiver.availableBalance+=tx.amount;
        sender.updatedAt=Instant.now();
        receiver.updatedAt=Instant.now();
        wallets.save(sender);
        wallets.save(receiver);
        var sa=accounts.findByOwnerTypeAndOwnerId("USER",sender.id).orElseThrow();
        var ra=accounts.findByOwnerTypeAndOwnerId("USER",receiver.id).orElseThrow();
        entries.save(new LedgerEntry(tx.id,sa.id,-tx.amount,sender.availableBalance));
        entries.save(new LedgerEntry(tx.id,ra.id,tx.amount,receiver.availableBalance));
        tx.status="COMPLETED";
        tx.completedAt=Instant.now();
        tx.updatedAt=tx.completedAt;
        txs.save(tx);
        var senderUser = users.findById(sender.userId).orElse(null);
        var receiverUser = users.findById(receiver.userId).orElse(null);
        String senderName = senderUser == null ? "Người dùng AnPay" : senderUser.fullName;
        String receiverName = receiverUser == null ? "Người dùng AnPay" : receiverUser.fullName;
        String amountText = formatVnd(tx.amount);
        String noteText = tx.description == null || tx.description.isBlank() ? "" : " Nội dung: " + tx.description.trim();

        var senderNotification = notifications.save(Notification.balanceChange(
                userId,
                "TRANSFER_SENT",
                "Chuyển tiền thành công",
                "Bạn đã chuyển " + amountText + " đến " + receiverName + " (" + receiver.walletCode + ")." + noteText,
                tx.id,
                tx.amount,
                "OUT",
                sender.availableBalance
        ));
        pushQueue.enqueue(senderNotification);

        var receiverNotification = notifications.save(Notification.balanceChange(
                receiver.userId,
                "TRANSFER_RECEIVED",
                "Bạn vừa nhận được tiền",
                "Bạn vừa nhận " + amountText + " từ " + senderName + " (" + sender.walletCode + ")." + noteText,
                tx.id,
                tx.amount,
                "IN",
                receiver.availableBalance
        ));
        pushQueue.enqueue(receiverNotification);
        return response(tr);

    }
    public TransferResponse get(UUID userId,UUID txId) {
        var tr=requests.findByTransactionId(txId).orElseThrow(()->bad("TRANSFER_NOT_FOUND","Không tìm thấy giao dịch"));
        var tx=txs.findById(txId).orElseThrow();
        var sw=wallets.findByUserId(userId).orElseThrow();
        if(!sw.id.equals(tx.senderWalletId)&&!sw.id.equals(tx.receiverWalletId))throw new BusinessException("FORBIDDEN","Không có quyền truy cập giao dịch",HttpStatus.FORBIDDEN);
        return response(tr);

    }
    private TransferResponse response(TransferRequest tr) {
        var tx=txs.findById(tr.transactionId).orElseThrow();
        var rw=wallets.findById(tx.receiverWalletId).orElseThrow();
        var ru=users.findById(rw.userId).orElseThrow();
        return new TransferResponse(tx.id,tx.reference,tx.amount,tx.status,rw.walletCode,mask(ru.fullName),tr.expiresAt,"OTP_REQUIRED".equals(tx.status));

    }
    private TransferRequest internalTransfer(UUID txId) {
        var tr=requests.findByTransactionId(txId).orElseThrow(()->bad("TRANSFER_NOT_FOUND","Không tìm thấy giao dịch"));
        if(!"ANPAY".equals(tr.recipientType))throw bad("TRANSFER_TYPE_INVALID","Giao dịch không phải chuyển nội bộ");
        return tr;
    }

    private void requireSender(TransferRequest tr,UUID userId) {
        if(!userId.equals(tr.userId))throw new BusinessException("FORBIDDEN","Không có quyền truy cập giao dịch",HttpStatus.FORBIDDEN);
    }

    private String mask(String s) {
        if(s==null||s.isBlank())return "***";
        String[] p=s.trim().split("\\s+");
        String last=p[p.length-1];
        return p[0].toUpperCase(Locale.ROOT)+" *** "+last.substring(0,1).toUpperCase(Locale.ROOT);

    }
    private String formatVnd(long amount) {
        return String.format(Locale.US, "%,d", amount).replace(',', '.') + " VND";
    }

    private BusinessException bad(String c,String m) {
        return new BusinessException(c,m,HttpStatus.BAD_REQUEST);

    }

}
