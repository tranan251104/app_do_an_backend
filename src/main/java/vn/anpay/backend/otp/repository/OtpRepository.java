package vn.anpay.backend.otp.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.anpay.backend.otp.entity.OtpChallenge;
import java.util.*;

public interface OtpRepository extends JpaRepository<OtpChallenge,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)@Query("select o from OtpChallenge o where o.id=:id")Optional<OtpChallenge> lockById(@Param("id")UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpChallenge> findFirstByPhoneNormalizedAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String phoneNormalized,
            String purpose
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpChallenge> findFirstByEmailNormalizedAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String emailNormalized,
            String purpose
    );

}
