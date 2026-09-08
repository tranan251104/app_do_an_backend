package vn.anpay.backend.auth.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.anpay.backend.auth.dto.*;
import vn.anpay.backend.auth.service.AuthService;
import vn.anpay.backend.auth.service.DemoPhoneOtpAuthService;
import vn.anpay.backend.auth.service.EmailOtpAuthService;
import vn.anpay.backend.auth.service.PhoneAuthService;
import vn.anpay.backend.common.security.SecurityUtil;
import vn.anpay.backend.common.api.ApiResponse;

@RestController @RequestMapping("/api/v1/auth") public class AuthController {
    private final AuthService service;
    private final EmailOtpAuthService emailOtp;
    private final PhoneAuthService phoneAuth;
    private final DemoPhoneOtpAuthService demoPhoneOtp;
    public AuthController(AuthService s,EmailOtpAuthService emailOtp,PhoneAuthService phoneAuth,DemoPhoneOtpAuthService demoPhoneOtp) {
        service=s;
        this.emailOtp=emailOtp;
        this.phoneAuth=phoneAuth;
        this.demoPhoneOtp=demoPhoneOtp;

    }
    @PostMapping("/register") ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest r) {
        return ApiResponse.ok(service.register(r));

    }
    @PostMapping("/login") ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest r) {
        return ApiResponse.ok(service.login(r));

    }
    @PostMapping("/email/send-otp") ApiResponse<EmailOtpSendResponse> sendEmailOtp(@Valid @RequestBody EmailOtpSendRequest r) {
        return ApiResponse.ok(emailOtp.send(r));

    }
    @PostMapping("/email/resend-otp") ApiResponse<EmailOtpSendResponse> resendEmailOtp(@Valid @RequestBody EmailOtpResendRequest r) {
        return ApiResponse.ok(emailOtp.resend(r));

    }
    @PostMapping("/email/verify-otp") ApiResponse<AuthResponse> verifyEmailOtp(@Valid @RequestBody EmailOtpVerifyRequest r) {
        return ApiResponse.ok(emailOtp.verify(r));

    }
    @PostMapping("/phone") ApiResponse<AuthResponse> phone(@Valid @RequestBody PhoneLoginRequest r) {
        return ApiResponse.ok(phoneAuth.login(r));

    }
    @PostMapping("/phone/send-otp") ApiResponse<PhoneOtpSendResponse> sendPhoneOtp(@Valid @RequestBody PhoneOtpSendRequest r) {
        return ApiResponse.ok(demoPhoneOtp.send(r));

    }
    @PostMapping("/phone/resend-otp") ApiResponse<PhoneOtpSendResponse> resendPhoneOtp(@Valid @RequestBody PhoneOtpResendRequest r) {
        return ApiResponse.ok(demoPhoneOtp.resend(r));

    }
    @PostMapping("/phone/verify-otp") ApiResponse<AuthResponse> verifyPhoneOtp(@Valid @RequestBody PhoneOtpVerifyRequest r) {
        return ApiResponse.ok(demoPhoneOtp.verify(r));

    }
    @PostMapping("/refresh") ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest r) {
        return ApiResponse.ok(service.refresh(r));

    }
    @PostMapping("/forgot-password/request") ApiResponse<Void> forgotRequest(@Valid @RequestBody ForgotPasswordRequest r) {
        service.forgotRequest(r);
        return ApiResponse.ok(null);

    }
    @PostMapping("/forgot-password/verify") ApiResponse<java.util.Map<String,String>> forgotVerify(@Valid @RequestBody ForgotPasswordVerifyRequest r) {
        return ApiResponse.ok(java.util.Map.of("resetToken",service.forgotVerify(r)));

    }
    @PostMapping("/forgot-password/reset") ApiResponse<Void> forgotReset(@Valid @RequestBody ForgotPasswordResetRequest r) {
        service.forgotReset(r);
        return ApiResponse.ok(null);

    }
    @PostMapping("/change-password") ApiResponse<Void> change(@Valid @RequestBody ChangePasswordRequest r) {
        service.changePassword(SecurityUtil.userId(),r);
        return ApiResponse.ok(null);

    }
    @PostMapping("/logout") ApiResponse<Void> logout(@RequestBody(required=false) RefreshRequest r) {
        service.logout(r==null?null:r.refreshToken());
        return ApiResponse.ok(null);

    }

}
