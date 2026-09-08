package vn.anpay.backend.auth.dto;

import java.time.Instant;

public record PhoneLinkResponse(String phone, boolean verified, Instant verifiedAt) {
}
