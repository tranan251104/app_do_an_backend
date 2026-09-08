package vn.anpay.backend.ledger.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import vn.anpay.backend.ledger.entity.LedgerTransaction;

import java.time.Instant;
import java.util.*;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from LedgerTransaction t where t.id = :id")
    Optional<LedgerTransaction> lockById(@Param("id") UUID id);

    Page<LedgerTransaction> findBySenderWalletIdOrReceiverWalletId(UUID sender, UUID receiver, Pageable p);

    @Query(value = """
        SELECT lt.*
        FROM ledger_transactions lt
        LEFT JOIN payment_intents pi ON pi.transaction_id = lt.id
        LEFT JOIN wallets sw ON sw.id = lt.sender_wallet_id
        LEFT JOIN users su ON su.id = sw.user_id
        LEFT JOIN wallets rw ON rw.id = lt.receiver_wallet_id
        LEFT JOIN users ru ON ru.id = rw.user_id
        WHERE (
            lt.sender_wallet_id = :walletId
            OR lt.receiver_wallet_id = :walletId
            OR pi.user_id = :userId
        )
        AND (
            :txType IS NULL OR :txType = '' OR UPPER(:txType) = 'ALL'
            OR (UPPER(:txType) = 'TRANSFER' AND lt.type IN ('INTERNAL_TRANSFER', 'EXTERNAL_BANK_TRANSFER'))
            OR (UPPER(:txType) = 'TOPUP' AND lt.type = 'TOP_UP')
            OR UPPER(lt.type) = UPPER(:txType)
        )
        AND (
            :direction IS NULL OR :direction = '' OR UPPER(:direction) = 'ALL'
            OR (UPPER(:direction) = 'OUT' AND lt.sender_wallet_id = :walletId)
            OR (UPPER(:direction) = 'IN' AND (lt.receiver_wallet_id = :walletId OR pi.user_id = :userId))
        )
        AND (
            :status IS NULL OR :status = '' OR UPPER(:status) = 'ALL'
            OR UPPER(lt.status) = UPPER(:status)
        )
        AND lt.created_at >= :fromAt
        AND lt.created_at <= :toAt
        AND (
            :keyword IS NULL OR :keyword = '' OR
            LOWER(lt.reference) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(lt.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(lt.provider, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(lt.provider_reference, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(sw.wallet_code, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(rw.wallet_code, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(su.full_name, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(ru.full_name, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(lt.metadata ->> 'bankName', '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(lt.metadata ->> 'accountName', '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(lt.metadata ->> 'accountNumber', '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
        )
        ORDER BY lt.created_at DESC, lt.id DESC
        """,
            countQuery = """
        SELECT COUNT(DISTINCT lt.id)
        FROM ledger_transactions lt
        LEFT JOIN payment_intents pi ON pi.transaction_id = lt.id
        LEFT JOIN wallets sw ON sw.id = lt.sender_wallet_id
        LEFT JOIN users su ON su.id = sw.user_id
        LEFT JOIN wallets rw ON rw.id = lt.receiver_wallet_id
        LEFT JOIN users ru ON ru.id = rw.user_id
        WHERE (
            lt.sender_wallet_id = :walletId
            OR lt.receiver_wallet_id = :walletId
            OR pi.user_id = :userId
        )
        AND (
            :txType IS NULL OR :txType = '' OR UPPER(:txType) = 'ALL'
            OR (UPPER(:txType) = 'TRANSFER' AND lt.type IN ('INTERNAL_TRANSFER', 'EXTERNAL_BANK_TRANSFER'))
            OR (UPPER(:txType) = 'TOPUP' AND lt.type = 'TOP_UP')
            OR UPPER(lt.type) = UPPER(:txType)
        )
        AND (
            :direction IS NULL OR :direction = '' OR UPPER(:direction) = 'ALL'
            OR (UPPER(:direction) = 'OUT' AND lt.sender_wallet_id = :walletId)
            OR (UPPER(:direction) = 'IN' AND (lt.receiver_wallet_id = :walletId OR pi.user_id = :userId))
        )
        AND (
            :status IS NULL OR :status = '' OR UPPER(:status) = 'ALL'
            OR UPPER(lt.status) = UPPER(:status)
        )
        AND lt.created_at >= :fromAt
        AND lt.created_at <= :toAt
        AND (
            :keyword IS NULL OR :keyword = '' OR
            LOWER(lt.reference) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(lt.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(lt.provider, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(lt.provider_reference, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(sw.wallet_code, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(rw.wallet_code, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(su.full_name, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(ru.full_name, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(lt.metadata ->> 'bankName', '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(lt.metadata ->> 'accountName', '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(lt.metadata ->> 'accountNumber', '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
        )
        """,
            nativeQuery = true)
    Page<LedgerTransaction> searchForUser(
            @Param("userId") UUID userId,
            @Param("walletId") UUID walletId,
            @Param("txType") String txType,
            @Param("direction") String direction,
            @Param("status") String status,
            @Param("keyword") String keyword,
            @Param("fromAt") Instant fromAt,
            @Param("toAt") Instant toAt,
            Pageable pageable
    );
}
