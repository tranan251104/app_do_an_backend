package vn.anpay.backend.withdrawal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.anpay.backend.withdrawal.entity.LinkedBankAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LinkedBankAccountRepository extends JpaRepository<LinkedBankAccount, UUID> {
    List<LinkedBankAccount> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, String status);
    Optional<LinkedBankAccount> findByUserIdAndBankBinAndAccountNumberAndStatus(
            UUID userId, String bankBin, String accountNumber, String status);
}
