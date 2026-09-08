package vn.anpay.backend.payout.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
public class MockPayoutProvider implements PayoutProvider {
    private static final Logger log = LoggerFactory.getLogger(MockPayoutProvider.class);

    @Override
    public PayoutResult submit(PayoutCommand command) {
        String providerReference = "MOCKBANK-"
                + command.bankBin()
                + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);

        log.info(
                "MOCK BANK payout accepted reference={} amount={} bankBin={} account={} providerReference={}",
                command.reference(),
                command.amount(),
                command.bankBin(),
                mask(command.accountNumber()),
                providerReference
        );

        return new PayoutResult(
                true,
                providerReference,
                "ANPAY simulated bank transfer completed"
        );
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) return "***";
        String v = value.trim();
        if (v.length() <= 4) return "****";
        return "****" + v.substring(v.length() - 4);
    }
}
