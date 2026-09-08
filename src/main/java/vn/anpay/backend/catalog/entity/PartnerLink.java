package vn.anpay.backend.catalog.entity;

import jakarta.persistence.*;
import java.util.*;

@Entity @Table(name="partner_links") public class PartnerLink {
    @Id
    public UUID id;
    public String name;
    @Column(name="web_url")public String webUrl;
    @Column(name="android_deep_link")public String androidDeepLink;
    @Column(name="ios_deep_link")public String iosDeepLink;
    @Column(name="fallback_url")public String fallbackUrl;
    public boolean active;
    protected PartnerLink() {

    }

}
