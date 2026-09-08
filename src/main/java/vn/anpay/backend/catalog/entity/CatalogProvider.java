package vn.anpay.backend.catalog.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.*;

@Entity @Table(name="catalog_providers") public class CatalogProvider {
    @Id
    public UUID id;
    public String code;
    public String name;
    public String category;
    public boolean active;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition="jsonb")
    public String metadata;
    protected CatalogProvider() {

    }

}
