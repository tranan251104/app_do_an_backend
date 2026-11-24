package vn.anpay.backend.smartotp;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import vn.anpay.backend.common.api.ApiResponse;
import vn.anpay.backend.common.security.SecurityUtil;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/smart-otp")
public class SmartOtpController {
    private final SmartOtpService service;
    public SmartOtpController(SmartOtpService service) { this.service=service; }
    public record EnrollStart(@NotBlank String publicKey) {}
    public record EnrollConfirm(@NotNull UUID deviceId,@Pattern(regexp="\\d{6}") String otp) {}
    public record SignedEnrollConfirm(
            @NotNull UUID deviceId,
            @NotBlank @Size(max = 88) String signature,
            @NotNull @Pattern(regexp = "[0-9]{6}") String smartOtpCode) {}
    @GetMapping("/status") public ApiResponse<SmartOtpService.Status> status() {
        return ApiResponse.ok(service.status(SecurityUtil.userId()));
    }
    @PostMapping("/enroll/start") public ApiResponse<SmartOtpService.Enrollment> start(@Valid @RequestBody EnrollStart r) {
        return ApiResponse.ok(service.startEnrollment(SecurityUtil.userId(),r.publicKey()));
    }
    @PostMapping("/enroll/confirm") public ApiResponse<SmartOtpService.Status> finish(@Valid @RequestBody EnrollConfirm r) {
        return ApiResponse.ok(service.confirmEnrollment(SecurityUtil.userId(),r.deviceId(),r.otp()));
    }
    @PostMapping("/enroll/signed/start")
    public ApiResponse<SmartOtpService.SignedEnrollment> startSigned(@Valid @RequestBody EnrollStart request) {
        return ApiResponse.ok(service.startSignedEnrollment(SecurityUtil.userId(), request.publicKey()));
    }

    @PostMapping("/enroll/signed/confirm")
    public ApiResponse<SmartOtpService.Status> finishSigned(@Valid @RequestBody SignedEnrollConfirm request) {
        return ApiResponse.ok(service.confirmSignedEnrollment(
                SecurityUtil.userId(), request.deviceId(), request.signature(), request.smartOtpCode()));
    }
    @DeleteMapping("/devices/{deviceId}") public ApiResponse<Void> revoke(@PathVariable UUID deviceId) {
        service.revoke(SecurityUtil.userId(),deviceId);
        return ApiResponse.ok(null);
    }
    @GetMapping("/transactions/{txId}") public ApiResponse<SmartOtpService.Authorization> transaction(@PathVariable UUID txId) {
        return ApiResponse.ok(service.authorization(SecurityUtil.userId(),txId));
    }
}
