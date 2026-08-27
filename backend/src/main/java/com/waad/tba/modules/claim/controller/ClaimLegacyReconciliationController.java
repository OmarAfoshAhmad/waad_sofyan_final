package com.waad.tba.modules.claim.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.claim.service.ClaimLegacyReconciliationService;
import com.waad.tba.modules.claim.service.ClaimLegacyReconciliationService.ReconciliationReport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Explicit, manual, admin-only repair for legacy claims predating the double-counting /
 * zero-approved-claim fix. Never runs automatically (not wired to any startup hook) —
 * a SUPER_ADMIN must call this endpoint deliberately, and the response reports exactly
 * how many claims were scanned, fixed, ledgered, and failed.
 */
@RestController
@RequestMapping("/api/v1/admin/claims/legacy-reconciliation")
@RequiredArgsConstructor
@PreAuthorize("@permissionGuard.has('DANGER_ZONE_EXECUTE')")
public class ClaimLegacyReconciliationController {

    private final ClaimLegacyReconciliationService reconciliationService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReconciliationReport>> reconcileLegacyClaims() {
        ReconciliationReport report = reconciliationService.reconcileLegacyClaims();
        return ResponseEntity.ok(ApiResponse.success(
                "اكتمل فحص وإصلاح المطالبات القديمة", report));
    }
}
