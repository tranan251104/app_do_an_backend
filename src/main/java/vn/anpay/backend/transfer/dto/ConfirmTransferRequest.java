package vn.anpay.backend.transfer.dto;

import jakarta.validation.constraints.Pattern;

public record ConfirmTransferRequest(@Pattern(regexp="\\d{6}") String otp) {

}
