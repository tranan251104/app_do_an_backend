package vn.anpay.backend.outbox.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.anpay.backend.outbox.entity.OutboxEvent;
import java.time.*;
import java.util.*;

public interface OutboxRepository extends JpaRepository<OutboxEvent,UUID> {
    // The caller must hold the transaction open until delivery and status update.
    // Other workers skip these rows; rollback releases them for a later retry.
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING' AND available_at <= :now
            ORDER BY created_at, id
            LIMIT 20
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockPendingBatch(@Param("now") Instant now);

}
