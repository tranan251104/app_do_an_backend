package vn.anpay.backend.notification.dto;

public record RegisterPushDeviceRequest(
        String token,
        String platform,
        String deviceId
) {
}
