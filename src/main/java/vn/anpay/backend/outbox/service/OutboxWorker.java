package vn.anpay.backend.outbox.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.anpay.backend.outbox.repository.OutboxRepository;
import vn.anpay.backend.notification.service.PushDeliveryService;

import java.util.LinkedHashMap;
import java.util.Map;

import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);

    private final OutboxRepository repo;
    private final OtpDeliveryService otp;
    private final JavaMailSender mail;
    private final ObjectMapper mapper;
    private final PushDeliveryService pushDelivery;
    private final String fromAddress;

    public OutboxWorker(
            OutboxRepository repo,
            OtpDeliveryService otp,
            JavaMailSender mail,
            ObjectMapper mapper,
            PushDeliveryService pushDelivery,
            @Value("${app.mail.from:}") String fromAddress
    ) {
        this.repo = repo;
        this.otp = otp;
        this.mail = mail;
        this.mapper = mapper;
        this.pushDelivery = pushDelivery;
        this.fromAddress = fromAddress;
    }

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void run() {
        for (var event : repo.lockPendingBatch(Instant.now())) {
            try {
                switch (event.eventType) {
                    case "SEND_OTP_EMAIL" -> sendOtpEmail(event.payload);
                    case "SEND_PUSH_NOTIFICATION" -> sendPushNotification(event.payload);
                    // Legacy domain events created by older service code had no consumer.
                    // Keep them harmless so already-persisted rows do not become FAILED.
                    case "TRANSFER_COMPLETED", "EXTERNAL_TRANSFER_COMPLETED" ->
                            log.debug("[OUTBOX_LEGACY] Ignoring legacy eventType={} eventId={}", event.eventType, event.id);
                    default -> throw new IllegalArgumentException("Unsupported outbox event type: " + event.eventType);
                }
                event.status = "PROCESSED";
                event.processedAt = Instant.now();
                event.lastError = null;
            } catch (Exception ex) {
                event.attempts++;
                event.lastError = abbreviate(ex.getClass().getSimpleName() + ": " + safeMessage(ex));
                if (event.attempts >= 5) {
                    event.status = "FAILED";
                } else {
                    event.availableAt = Instant.now().plusSeconds(30L * event.attempts);
                }
                log.error(
                        "[OUTBOX_ERR] Delivery failed eventId={} eventType={} attempt={} status={} errorType={} message={}",
                        event.id,
                        event.eventType,
                        event.attempts,
                        event.status,
                        ex.getClass().getSimpleName(),
                        safeMessage(ex)
                );
            }
        }
    }


    private void sendPushNotification(String payload) throws Exception {
        JsonNode node = mapper.readTree(payload);
        UUID userId = UUID.fromString(requiredText(node, "userId"));
        String title = requiredText(node, "title");
        String body = requiredText(node, "body");

        Map<String, String> data = new LinkedHashMap<>();
        copyText(node, data, "notificationId");
        copyText(node, data, "transactionId");
        copyText(node, data, "type");
        copyText(node, data, "category");

        log.info("[FCM_SEND] userId={} notificationId={} type={}",
                userId, data.get("notificationId"), data.get("type"));
        pushDelivery.sendToUser(userId, title, body, data);
    }

    private void copyText(JsonNode node, Map<String, String> target, String field) {
        String value = node.path(field).asText("");
        if (!value.isBlank()) {
            target.put(field, value);
        }
    }

    private void sendOtpEmail(String payload) throws Exception {
        JsonNode node = mapper.readTree(payload);
        UUID challengeId = UUID.fromString(requiredText(node, "challengeId"));
        String to = requiredText(node, "email").trim();
        String code = otp.peek(challengeId);

        if (code == null || code.isBlank()) {
            throw new IllegalStateException("OTP delivery code is no longer available in Redis");
        }

        String purpose = node.path("purpose").asText("GENERIC");
        String subject;
        String text;

        if ("EXTERNAL_TRANSFER".equals(purpose)) {
            subject = "AnPay - Mã OTP xác nhận chuyển tiền";
            text = externalTransferMessage(node, code);
        } else if ("PASSWORD_RESET".equals(purpose)) {
            subject = "AnPay - Mã OTP đặt lại mật khẩu";
            text = "Mã OTP AnPay của bạn là: " + code + "\n\n"
                    + "Mã có hiệu lực trong 5 phút. Không chia sẻ mã này cho bất kỳ ai.";
        } else if ("EMAIL_AUTH".equals(purpose)) {
            subject = "AnPay - Mã OTP xác thực email";
            text = "Mã OTP xác thực email AnPay của bạn là: " + code + "\n\n"
                    + "Mã có hiệu lực trong 5 phút. Không chia sẻ mã này cho bất kỳ ai.";
        } else {
            subject = "AnPay OTP";
            text = "Mã OTP AnPay của bạn là: " + code + "\n\n"
                    + "Mã có hiệu lực trong 5 phút. Không chia sẻ mã này cho bất kỳ ai.";
        }

        var message = new SimpleMailMessage();
        if (fromAddress != null && !fromAddress.isBlank()) {
            message.setFrom(fromAddress.trim());
        }
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);

        log.info(
                "[OTP_MAIL] Sending OTP email challengeId={} purpose={} to={}",
                challengeId, purpose, maskEmail(to)
        );
        mail.send(message);
        // Do not delete the delivery copy until PROCESSED has committed. A DB
        // rollback must leave the code available for retry (SMTP may redeliver).
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    otp.consume(challengeId);
                } catch (Exception ex) {
                    // Redis also expires this short-lived value; delivery is done.
                    log.warn("[OTP_CLEANUP_ERR] Could not clear delivered OTP challengeId={}", challengeId);
                }
            }
        });
        log.info(
                "[OTP_MAIL_OK] OTP email sent challengeId={} purpose={} to={}",
                challengeId, purpose, maskEmail(to)
        );
    }

    private String externalTransferMessage(JsonNode node, String code) {
        String amount = node.path("amountFormatted").asText("");
        String bankName = node.path("bankName").asText("");
        String account = node.path("accountNumberMasked").asText("");
        String reference = node.path("reference").asText("");
        String expiresMinutes = node.path("expiresMinutes").asText("5");

        StringBuilder sb = new StringBuilder();
        sb.append("Mã OTP xác nhận chuyển tiền AnPay của bạn là: ").append(code).append("\n\n");
        if (!amount.isBlank()) sb.append("Số tiền: ").append(amount).append("\n");
        if (!bankName.isBlank()) sb.append("Ngân hàng nhận: ").append(bankName).append("\n");
        if (!account.isBlank()) sb.append("Tài khoản nhận: ").append(account).append("\n");
        if (!reference.isBlank()) sb.append("Mã giao dịch: ").append(reference).append("\n");
        sb.append("\nMã có hiệu lực trong ").append(expiresMinutes).append(" phút.")
                .append("\nKhông chia sẻ mã OTP này cho bất kỳ ai.")
                .append("\n\nANPAY");
        return sb.toString();
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing outbox field: " + field);
        }
        return value;
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) return local.charAt(0) + "***" + domain;
        return local.substring(0, Math.min(3, local.length())) + "***" + domain;
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null ? "" : message.replaceAll("[\\r\\n]+", " ");
    }

    private String abbreviate(String value) {
        if (value == null) return null;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
