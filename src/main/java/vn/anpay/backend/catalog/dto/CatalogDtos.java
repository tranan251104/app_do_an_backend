package vn.anpay.backend.catalog.dto;

import java.time.Instant;
import java.util.UUID;

public final class CatalogDtos {
    private CatalogDtos() {

    }
    public record Provider(UUID id,String code,String name,String category) {

    }
    public record Product(UUID id,String code,UUID providerId,String category,String name,String description,long price,long discount,Instant validFrom,Instant validUntil) {

    }
    public record Promo(UUID id,String code,String title,String description,String category,Instant validFrom,Instant validUntil) {

    }
    public record Partner(UUID id,String name,String webUrl,String androidDeepLink,String iosDeepLink,String fallbackUrl) {

    }

}
