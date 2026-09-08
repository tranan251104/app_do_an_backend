package vn.anpay.backend.beneficiary.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record BeneficiaryDto(UUID id,@NotBlank String type,String bankBin,String bankName,@NotBlank String accountNumber,String accountName,String nickname) {

}
