package com.waad.tba.modules.claim.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.claim.dto.engine.BulkCoverageEngineRequest;
import com.waad.tba.modules.claim.dto.engine.ClaimLineInput;
import com.waad.tba.modules.claim.dto.engine.CoverageResult;
import com.waad.tba.modules.claim.service.CoverageEngineService;
import com.waad.tba.security.ProviderContextGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Coverage Engine API
 *
 * جميع الحسابات المالية الخاصة بأسطر المطالبة تتم هنا في الـ Backend.
 * الـ Frontend يرسل بيانات الإدخال الخام فقط، ويعرض النتيجة المحسوبة.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/claims")
@RequiredArgsConstructor
@Tag(name = "Coverage Engine", description = "Backend-authoritative claim line financial calculations")
@PreAuthorize("isAuthenticated()")
public class CoverageEngineController {

    private final CoverageEngineService coverageEngineService;
    private final ProviderContextGuard providerContextGuard;

    @PostMapping("/calculate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','INSURANCE_ADMIN','MEDICAL_REVIEWER','PROVIDER_STAFF','EMPLOYER_ADMIN')")
    @Operation(summary = "Calculate single claim line", description = "Calculates one claim line using backend coverage engine")
    public ResponseEntity<ApiResponse<CoverageResult>> calculateSingle(
            @Valid @RequestBody BulkCoverageEngineRequest request) {
        providerContextGuard.getRequiredProviderId();

        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessRuleException("يجب إرسال سطر خدمة واحد على الأقل");
        }

        ClaimLineInput firstLine = request.getLines().get(0);
        CoverageResult result = coverageEngineService.calculateSingle(request, firstLine);

        return ResponseEntity.ok(ApiResponse.success("Coverage calculation completed", result));
    }

    @PostMapping("/calculate-bulk")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','INSURANCE_ADMIN','MEDICAL_REVIEWER','PROVIDER_STAFF','EMPLOYER_ADMIN')")
    @Operation(summary = "Calculate bulk claim lines", description = "Calculates all claim lines using backend coverage engine with batch context")
    public ResponseEntity<ApiResponse<List<CoverageResult>>> calculateBulk(
            @Valid @RequestBody BulkCoverageEngineRequest request) {

        providerContextGuard.getRequiredProviderId();

        log.info("[COVERAGE-ENGINE] bulk calculate policyId={}, memberId={}, lines={}, serviceYear={}, fullCoverage={}",
                request.getPolicyId(),
                request.getMemberId(),
                request.getLines() != null ? request.getLines().size() : 0,
                request.getServiceYear(),
                request.isFullCoverage());

        List<CoverageResult> results = coverageEngineService.calculateBulk(request);
        return ResponseEntity.ok(ApiResponse.success("Coverage bulk calculation completed", results));
    }
}
