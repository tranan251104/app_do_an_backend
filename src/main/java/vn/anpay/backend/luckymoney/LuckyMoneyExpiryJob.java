package vn.anpay.backend.luckymoney;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class LuckyMoneyExpiryJob {
    private static final Logger log=LoggerFactory.getLogger(LuckyMoneyExpiryJob.class);
    private final LuckyMoneyRepository envelopes;
    private final LuckyMoneyService service;
    public LuckyMoneyExpiryJob(LuckyMoneyRepository envelopes, LuckyMoneyService service) {
        this.envelopes=envelopes; this.service=service;
    }
    @Scheduled(fixedDelayString="${app.lucky-money.expiry-delay-ms:60000}")
    public void refundExpired() {
        for(var id:envelopes.due(Instant.now())) {
            try { service.expire(id); }
            catch(RuntimeException error) { log.error("Cannot expire lucky money {}",id,error); }
        }
    }
}
