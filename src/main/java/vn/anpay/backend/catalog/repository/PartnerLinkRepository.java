package vn.anpay.backend.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.anpay.backend.catalog.entity.PartnerLink;
import java.util.*;

public interface PartnerLinkRepository extends JpaRepository<PartnerLink,UUID> {
    List<PartnerLink> findByActiveTrue();

}
