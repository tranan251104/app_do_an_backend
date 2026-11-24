package vn.anpay.backend.catalog.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.*;
import java.util.*;

@Entity @Table(name="promotions") public class Promotion {
    @Id
    public UUID id;
    public String code;
    public String title;
    public String description;
    public String category;
    public boolean active;
    @Column(name="valid_from")public Instant validFrom;
    @Column(name="valid_until")public Instant validUntil;
    @Column(name = "demo_benefit", length = 16)
    public String demoBenefit;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition="jsonb")
    public String metadata;
    protected Promotion() {

    }

}
