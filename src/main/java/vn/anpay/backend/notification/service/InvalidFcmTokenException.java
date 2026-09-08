package vn.anpay.backend.notification.service;

public class InvalidFcmTokenException extends RuntimeException {
    public InvalidFcmTokenException(String message) {
        super(message);
    }
}
