package vn.anpay.backend.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.anpay.backend.catalog.entity.CatalogProvider;
import java.util.*;

public interface CatalogProviderRepository extends JpaRepository<CatalogProvider,UUID> {
    List<CatalogProvider> findByActiveTrue();

}
