package vn.anpay.backend.auth.dto;
public record AuthResponse(String accessToken,String refreshToken,String tokenType,long expiresInSeconds) {

}
