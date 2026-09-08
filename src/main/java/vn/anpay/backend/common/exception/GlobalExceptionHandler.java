package vn.anpay.backend.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import vn.anpay.backend.common.api.*;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class) ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
        String traceId = traceId();
        log.warn("Business request rejected status={} code={} traceId={}", e.status().value(), e.code(), traceId);
        return ResponseEntity.status(e.status()).body(ApiResponse.error(new ApiError(e.code(), e.getMessage(), List.of(), traceId)));

    }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException e) {
        var fields=e.getBindingResult().getFieldErrors().stream().map(x->new ApiError.FieldError(x.getField(), safe(x))).toList();
        String traceId = traceId();
        log.warn("Request validation failed status=400 fields={} traceId={}",
                fields.stream().map(ApiError.FieldError::field).toList(), traceId);
        return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("VALIDATION_ERROR","Dữ liệu không hợp lệ",fields,traceId)));

    }
    @ExceptionHandler(ConstraintViolationException.class) ResponseEntity<ApiResponse<Void>> constraint(ConstraintViolationException e) {
        String traceId = traceId();
        log.warn("Request constraint validation failed status=400 traceId={}", traceId);
        return ResponseEntity.badRequest().body(ApiResponse.error(new ApiError("VALIDATION_ERROR","Dữ liệu không hợp lệ",List.of(),traceId)));

    }
    @ExceptionHandler(Exception.class) ResponseEntity<ApiResponse<Void>> unknown(Exception e) {
        String traceId = traceId();
        log.error("Unhandled request exception traceId={}", traceId, e);
        return ResponseEntity.status(500).body(ApiResponse.error(new ApiError("INTERNAL_ERROR","Có lỗi xảy ra. Vui lòng thử lại.",List.of(),traceId)));

    }
    private String safe(FieldError x) {
        return x.getDefaultMessage()==null?"Không hợp lệ":x.getDefaultMessage();

    }
    private String traceId() {
        var t=MDC.get("traceId");
        return t==null?"":t;

    }

}
