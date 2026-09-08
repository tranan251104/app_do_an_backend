package vn.anpay.backend.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.anpay.backend.common.exception.BusinessException;

import java.util.regex.Pattern;

@Component
public class PhoneNumberNormalizer {
    private static final Pattern FORMATTING = Pattern.compile("[\\s().-]");
    private static final Pattern VIETNAM_PHONE = Pattern.compile("^\\+84[1-9][0-9]{7,9}$");

    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid();
        }

        String value = FORMATTING.matcher(raw.trim()).replaceAll("");
        if (value.startsWith("00")) {
            value = "+" + value.substring(2);
        } else if (value.startsWith("0")) {
            value = "+84" + value.substring(1);
        } else if (value.startsWith("84")) {
            value = "+" + value;
        }

        if (!VIETNAM_PHONE.matcher(value).matches()) {
            throw invalid();
        }
        return value;
    }

    public String normalizeOptional(String raw) {
        return raw == null || raw.isBlank() ? null : normalize(raw);
    }

    private BusinessException invalid() {
        return new BusinessException(
                "PHONE_INVALID",
                "Số điện thoại Việt Nam không hợp lệ",
                HttpStatus.BAD_REQUEST
        );
    }
}
