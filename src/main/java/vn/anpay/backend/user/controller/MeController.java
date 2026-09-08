package vn.anpay.backend.user.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import vn.anpay.backend.common.api.ApiResponse;
import vn.anpay.backend.common.security.SecurityUtil;
import vn.anpay.backend.auth.dto.LinkPhoneRequest;
import vn.anpay.backend.auth.dto.PhoneLinkResponse;
import vn.anpay.backend.auth.service.PhoneAuthService;
import vn.anpay.backend.user.dto.*;
import vn.anpay.backend.user.service.UserService;

@RestController @RequestMapping("/api/v1/me") public class MeController {
    private final UserService s;
    private final PhoneAuthService phoneAuth;
    public MeController(UserService s,PhoneAuthService phoneAuth) {
        this.s=s;
        this.phoneAuth=phoneAuth;

    }
    @GetMapping ApiResponse<MeResponse> me() {
        return ApiResponse.ok(s.me(SecurityUtil.userId()));

    }
    @PatchMapping ApiResponse<MeResponse> patch(@Valid @RequestBody UpdateMeRequest r) {
        return ApiResponse.ok(s.update(SecurityUtil.userId(),r));

    }
    @PostMapping("/phone/link") ApiResponse<PhoneLinkResponse> linkPhone(@Valid @RequestBody LinkPhoneRequest r) {
        return ApiResponse.ok(phoneAuth.link(SecurityUtil.userId(),r));

    }

}
