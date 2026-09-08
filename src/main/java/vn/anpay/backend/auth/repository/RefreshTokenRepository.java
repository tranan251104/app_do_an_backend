package vn.anpay.backend.auth.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.anpay.backend.auth.entity.RefreshToken;
import java.time.*;
import java.util.*;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken,UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    @Modifying @Query("update RefreshToken r set r.revokedAt=:now where r.familyId=:family and r.revokedAt is null") int revokeFamily(@Param("family") UUID family,@Param("now") Instant now);

}
