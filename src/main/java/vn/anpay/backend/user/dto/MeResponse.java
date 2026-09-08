package vn.anpay.backend.user.dto;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

public record MeResponse(UUID id,String email,String phone,boolean phoneVerified,Instant phoneVerifiedAt,String fullName,LocalDate dateOfBirth,String gender,String address,String status) {

}
