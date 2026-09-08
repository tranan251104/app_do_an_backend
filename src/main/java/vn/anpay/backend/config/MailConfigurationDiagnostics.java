package vn.anpay.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class MailConfigurationDiagnostics {
    private static final Logger log = LoggerFactory.getLogger(MailConfigurationDiagnostics.class);

    @Value("${spring.mail.host:}")
    private String host;

    @Value("${spring.mail.port:0}")
    private int port;

    @Value("${spring.mail.username:}")
    private String username;

    @Value("${spring.mail.properties.mail.smtp.auth:false}")
    private boolean auth;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable:false}")
    private boolean startTls;

    @PostConstruct
    void logConfiguration() {
        boolean usernamePresent = username != null && !username.isBlank();
        log.info(
                "OTP mail configuration host={} port={} usernamePresent={} smtpAuth={} startTls={}",
                host, port, usernamePresent, auth, startTls
        );
        if (!usernamePresent) {
            log.warn("OTP mail username is not configured. Set MAIL_USERNAME and MAIL_PASSWORD before testing real email OTP delivery.");
        }
    }
}
