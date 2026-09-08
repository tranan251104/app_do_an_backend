package vn.anpay.backend.catalog.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.*;
import java.util.*;

@Entity @Table(name="catalog_products") public class CatalogProduct {
    @Id
    public UUID id;
    public String code;
    @Column(name="provider_id")public UUID providerId;
    public String category;
    public String name;
    public String description;
    public long price;
    public long discount;
    public boolean active;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition="jsonb")
    public String metadata;
    @Column(name="valid_from")public Instant validFrom;
    @Column(name="valid_until")public Instant validUntil;
    protected CatalogProduct() {

    }

}
