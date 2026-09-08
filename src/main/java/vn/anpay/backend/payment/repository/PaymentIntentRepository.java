package vn.anpay.backend.payment.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.anpay.backend.payment.entity.PaymentIntent;
import java.util.*;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent,UUID> {
    Optional<PaymentIntent> findByOrderCode(long orderCode);
    Optional<PaymentIntent> findByUserIdAndIdempotencyKey(UUID userId,UUID idempotencyKey);
    Optional<PaymentIntent> findByTransactionId(UUID transactionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)@Query("select p from PaymentIntent p where p.orderCode=:code")Optional<PaymentIntent> lockByOrderCode(@Param("code")long code);
    @Lock(LockModeType.PESSIMISTIC_WRITE)@Query("select p from PaymentIntent p where p.id=:id")Optional<PaymentIntent> lockById(@Param("id")UUID id);

}
