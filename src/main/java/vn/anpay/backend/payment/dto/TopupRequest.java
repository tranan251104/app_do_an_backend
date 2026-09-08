package vn.anpay.backend.payment.dto;

import jakarta.validation.constraints.Min;

public record TopupRequest(@Min(10000) long amount) {

}
