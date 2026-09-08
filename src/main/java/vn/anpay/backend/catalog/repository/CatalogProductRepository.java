package vn.anpay.backend.catalog.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.anpay.backend.catalog.entity.CatalogProduct;
import java.util.*;

public interface CatalogProductRepository extends JpaRepository<CatalogProduct,UUID> {
    Page<CatalogProduct> findByActiveTrue(Pageable p);
    Page<CatalogProduct> findByActiveTrueAndCategoryIgnoreCase(String category,Pageable p);

}
