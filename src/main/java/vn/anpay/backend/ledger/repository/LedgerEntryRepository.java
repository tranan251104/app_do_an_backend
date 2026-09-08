package vn.anpay.backend.ledger.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.anpay.backend.ledger.entity.LedgerEntry;
import java.util.*;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry,UUID> {
    List<LedgerEntry> findByTransactionId(UUID transactionId);

}
