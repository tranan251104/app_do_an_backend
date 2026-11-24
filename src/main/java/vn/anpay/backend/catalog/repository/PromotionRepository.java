package vn.anpay.backend.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.anpay.backend.catalog.entity.Promotion;
import java.time.Instant;
import java.util.*;

public interface PromotionRepository extends JpaRepository<Promotion,UUID> {
    List<Promotion> findByActiveTrue();

    @Query("""
            select p from Promotion p
            where p.active = true
              and (p.validFrom is null or p.validFrom <= :now)
              and (p.validUntil is null or p.validUntil >= :now)
            order by p.validFrom desc nulls last, p.code
            """)
    List<Promotion> findAvailableAt(@Param("now") Instant now);

}
