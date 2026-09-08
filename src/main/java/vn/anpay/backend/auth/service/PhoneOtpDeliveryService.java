package vn.anpay.backend.auth.service;

public interface PhoneOtpDeliveryService {
    DeliveryReceipt deliver(String phoneNumber, String fcmToken, String otp);

    record DeliveryReceipt(String delivery, String demoOtp) {
    }
}
