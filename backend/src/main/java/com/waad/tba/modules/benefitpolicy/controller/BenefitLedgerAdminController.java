package com.waad.tba.modules.benefitpolicy.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.benefitpolicy.service.BenefitBucketLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/benefit-ledger")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class BenefitLedgerAdminController {
    private final BenefitBucketLedgerService ledgerService;

    @PostMapping("/claims/{claimId}/reconcile")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reconcileApprovedClaim(
            @PathVariable Long claimId) {
        int createdEntries = ledgerService.reconcileApprovedClaim(claimId);
        return ResponseEntity.ok(ApiResponse.success(
                "Benefit ledger reconciliation completed",
                Map.of("claimId", claimId, "createdEntries", createdEntries)));
    }
}
