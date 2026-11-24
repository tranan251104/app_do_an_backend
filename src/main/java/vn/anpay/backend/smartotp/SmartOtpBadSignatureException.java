package vn.anpay.backend.smartotp;
import org.springframework.http.HttpStatus;
import vn.anpay.backend.common.exception.BusinessException;
/** Bad signatures must persist the failed-attempt counter instead of rolling back. */
public class SmartOtpBadSignatureException extends BusinessException {
    public SmartOtpBadSignatureException(String code, String message) { super(code,message,HttpStatus.BAD_REQUEST); }
}
