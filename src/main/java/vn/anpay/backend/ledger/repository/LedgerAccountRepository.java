package vn.anpay.backend.ledger.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.anpay.backend.ledger.entity.LedgerAccount;
import java.util.*;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount,UUID> {
    Optional<LedgerAccount> findByOwnerTypeAndOwnerId(String ownerType,UUID ownerId);
    Optional<LedgerAccount> findByCode(String code);

}
