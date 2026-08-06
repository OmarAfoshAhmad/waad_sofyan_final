package com.waad.tba.modules.benefitpolicy.controller;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.benefitpolicy.dto.MemberBenefitUsageDto;
import com.waad.tba.modules.benefitpolicy.service.MemberBenefitUsageService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/unified-members")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF', 'MEDICAL_REVIEWER')")
public class MemberBenefitUsageController {
    private final MemberBenefitUsageService service;

    @GetMapping("/{memberId}/benefit-usage")
    public ResponseEntity<ApiResponse<MemberBenefitUsageDto>> get(
            @PathVariable Long memberId,
            @RequestParam(value = "asOfDate", required = false) LocalDate asOfDate) {
        return ResponseEntity.ok(ApiResponse.success("تم تحميل حالة السقوف", service.get(memberId, asOfDate)));
    }
}
