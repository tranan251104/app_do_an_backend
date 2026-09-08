package vn.anpay.backend.user.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateMeRequest(@Size(max=160) String fullName,LocalDate dateOfBirth,@Size(max=32) String gender,@Size(max=500) String address) {

}
