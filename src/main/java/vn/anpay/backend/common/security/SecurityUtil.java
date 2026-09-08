package vn.anpay.backend.common.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.anpay.backend.common.exception.BusinessException;
import java.util.UUID;

public final class SecurityUtil {
    private SecurityUtil() {

    }
    public static UUID userId() {
        var a=SecurityContextHolder.getContext().getAuthentication();
        if(a==null||!(a.getPrincipal() instanceof CurrentUser u)) throw new BusinessException("UNAUTHORIZED","Chưa đăng nhập",HttpStatus.UNAUTHORIZED);
        return u.userId();

    }

}
