package vn.anpay.backend.payment.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentIntentResponse(UUID paymentIntentId,long orderCode,long amount,String status,String checkoutUrl,Instant expiresAt) {

}
