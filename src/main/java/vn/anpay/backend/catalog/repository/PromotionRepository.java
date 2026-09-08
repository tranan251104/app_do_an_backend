package vn.anpay.backend.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.anpay.backend.catalog.entity.Promotion;
import java.util.*;

public interface PromotionRepository extends JpaRepository<Promotion,UUID> {
    List<Promotion> findByActiveTrue();

}
