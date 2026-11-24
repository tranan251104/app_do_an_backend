package vn.anpay.backend.smartotp;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface SmartOtpChallengeRepository extends JpaRepository<SmartOtpChallenge,UUID> {
    Optional<SmartOtpChallenge> findByTransactionId(UUID transactionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SmartOtpChallenge c where c.transactionId=:transactionId")
    Optional<SmartOtpChallenge> lockByTransactionId(@Param("transactionId") UUID transactionId);
}
