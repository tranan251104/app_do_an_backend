package vn.anpay.backend.otp.service;

import org.springframework.http.HttpStatus;
import vn.anpay.backend.common.exception.BusinessException;

/**
 * Only a rejected code that has incremented the attempt counter uses this type.
 * Callers may commit this exception before any money or successful OTP changes.
 * Other business failures must still roll back the entire transaction.
 */
public final class OtpAttemptException extends BusinessException {
    public OtpAttemptException(String code, String message) {
        super(code, message, HttpStatus.BAD_REQUEST);
    }
}
