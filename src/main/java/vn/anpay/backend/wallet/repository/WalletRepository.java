package vn.anpay.backend.wallet.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.anpay.backend.wallet.entity.Wallet;
import java.util.*;

public interface WalletRepository extends JpaRepository<Wallet,UUID> {
    Optional<Wallet> findByUserId(UUID userId);
    Optional<Wallet> findByWalletCode(String walletCode);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select w from Wallet w where w.id=:id") Optional<Wallet> lockById(@Param("id") UUID id);

}
