package vn.anpay.backend.beneficiary.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.anpay.backend.beneficiary.entity.Beneficiary;
import java.util.*;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary,UUID> {
    Page<Beneficiary> findByUserId(UUID userId,Pageable p);

}
