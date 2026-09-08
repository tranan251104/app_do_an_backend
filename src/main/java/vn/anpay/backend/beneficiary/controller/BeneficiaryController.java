package vn.anpay.backend.beneficiary.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;
import vn.anpay.backend.beneficiary.dto.BeneficiaryDto;
import vn.anpay.backend.beneficiary.service.BeneficiaryService;
import vn.anpay.backend.common.api.ApiResponse;
import vn.anpay.backend.common.security.SecurityUtil;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/beneficiaries") public class BeneficiaryController {
    private final BeneficiaryService s;
    public BeneficiaryController(BeneficiaryService s) {
        this.s=s;

    }
    @GetMapping ApiResponse<Page<BeneficiaryDto>> list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size) {
        return ApiResponse.ok(s.list(SecurityUtil.userId(),PageRequest.of(page,Math.min(size,100),Sort.by(Sort.Direction.DESC,"createdAt"))));

    }
    @PostMapping ApiResponse<BeneficiaryDto> create(@Valid @RequestBody BeneficiaryDto d) {
        return ApiResponse.ok(s.create(SecurityUtil.userId(),d));

    }
    @PatchMapping("/{id}")ApiResponse<BeneficiaryDto> update(@PathVariable UUID id,@Valid @RequestBody BeneficiaryDto d) {
        return ApiResponse.ok(s.update(SecurityUtil.userId(),id,d));

    }
    @DeleteMapping("/{id}")ApiResponse<Void> del(@PathVariable UUID id) {
        s.delete(SecurityUtil.userId(),id);
        return ApiResponse.ok(null);

    }

}
