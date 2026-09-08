package vn.anpay.backend.catalog.controller;

import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;
import vn.anpay.backend.catalog.entity.*;
import vn.anpay.backend.catalog.dto.CatalogDtos;
import vn.anpay.backend.catalog.repository.*;
import vn.anpay.backend.common.api.ApiResponse;
import java.util.*;

@RestController @RequestMapping("/api/v1") public class CatalogController {
    private final CatalogProviderRepository providers;
    private final CatalogProductRepository products;
    private final PromotionRepository promos;
    private final PartnerLinkRepository partners;
    public CatalogController(CatalogProviderRepository a,CatalogProductRepository b,PromotionRepository c,PartnerLinkRepository d) {
        providers=a;
        products=b;
        promos=c;
        partners=d;

    }
    @GetMapping("/catalog/providers")ApiResponse<List<CatalogDtos.Provider>> providers() {
        return ApiResponse.ok(providers.findByActiveTrue().stream().map(x->new CatalogDtos.Provider(x.id,x.code,x.name,x.category)).toList());

    }
    @GetMapping("/catalog/products")ApiResponse<Page<CatalogDtos.Product>> products(@RequestParam(required=false)String category,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size) {
        var p=PageRequest.of(page,Math.min(size,100),Sort.by("name"));
        var pageData=category==null||category.isBlank()?products.findByActiveTrue(p):products.findByActiveTrueAndCategoryIgnoreCase(category,p);
        return ApiResponse.ok(pageData.map(x->new CatalogDtos.Product(x.id,x.code,x.providerId,x.category,x.name,x.description,x.price,x.discount,x.validFrom,x.validUntil)));

    }
    @GetMapping("/promotions")ApiResponse<List<CatalogDtos.Promo>> promotions() {
        return ApiResponse.ok(promos.findByActiveTrue().stream().map(x->new CatalogDtos.Promo(x.id,x.code,x.title,x.description,x.category,x.validFrom,x.validUntil)).toList());

    }
    @GetMapping("/partners")ApiResponse<List<CatalogDtos.Partner>> partners() {
        return ApiResponse.ok(partners.findByActiveTrue().stream().map(x->new CatalogDtos.Partner(x.id,x.name,x.webUrl,x.androidDeepLink,x.iosDeepLink,x.fallbackUrl)).toList());

    }

}
