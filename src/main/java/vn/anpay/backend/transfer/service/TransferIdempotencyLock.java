package vn.anpay.backend.transfer.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class TransferIdempotencyLock {
    private final JdbcTemplate jdbc;

    public TransferIdempotencyLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Lock even when no request row exists. This uses the same PostgreSQL
     * transaction as JPA and releases automatically on commit or rollback.
     * A hash collision only serializes unrelated requests; the scoped unique
     * index and full payload comparison still decide request identity.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(UUID userId, String operation, UUID key) {
        jdbc.execute("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (PreparedStatementCallback<Void>) statement -> {
                    statement.setString(1, userId + ":" + operation + ":" + key);
                    statement.execute();
                    return null;
                });
    }
}
