package vn.anpay.backend.luckymoney;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.*;

public interface LuckyMoneyRepository extends JpaRepository<LuckyMoneyEnvelope, UUID> {
    Optional<LuckyMoneyEnvelope> findBySenderUserIdAndIdempotencyKey(UUID sender, UUID key);
    Page<LuckyMoneyEnvelope> findBySenderUserIdOrderByCreatedAtDesc(UUID user, Pageable page);
    @Query("select e from LuckyMoneyEnvelope e, LedgerTransaction t where e.id=t.id " +
           "and e.receiverUserId=:user and t.status<>'OTP_REQUIRED' order by e.createdAt desc")
    Page<LuckyMoneyEnvelope> received(UUID user, Pageable pageable);
    @Query(value = "select e.id from lucky_money_envelopes e join ledger_transactions t on t.id=e.id " +
            "where t.status='WAITING_OPEN' and e.expires_at<=:now order by e.expires_at limit 100", nativeQuery = true)
    List<UUID> due(Instant now);
}
