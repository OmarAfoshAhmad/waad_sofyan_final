package com.waad.tba.modules.settlement.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.common.guard.FeatureGuard;
import com.waad.tba.modules.settlement.dto.AdjustProviderAccountRequest;
import com.waad.tba.modules.settlement.dto.ProviderAccountAdjustmentResultDto;
import com.waad.tba.modules.settlement.dto.ProviderReconciliationDto;
import com.waad.tba.modules.settlement.service.ProviderAccountAdjustmentService;
import com.waad.tba.modules.settlement.service.ProviderAccountReconciliationService;
import com.waad.tba.security.AuthorizationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Diagnosis (always available, never writes) and the one sanctioned correction
 * (gated by {@link FeatureGuard#requireProviderPaymentPosting()} — see
 * {@link ProviderPaymentController} for why writes and reads are split here).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/provider-accounts/reconciliation")
@RequiredArgsConstructor
@Tag(name = "Settlement - Reconciliation (v2)")
@PreAuthorize("isAuthenticated()")
public class ProviderAccountReconciliationController {

    private final ProviderAccountReconciliationService reconciliation;
    private final ProviderAccountAdjustmentService adjustment;
    private final AuthorizationService authorizationService;
    private final FeatureGuard featureGuard;

    @GetMapping("/by-provider/{providerId}")
    public ResponseEntity<ApiResponse<ProviderReconciliationDto>> reconcile(@PathVariable Long providerId) {
        return ResponseEntity.ok(ApiResponse.success(reconciliation.reconcile(providerId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProviderReconciliationDto>>> reconcileAll() {
        return ResponseEntity.ok(ApiResponse.success(reconciliation.reconcileAll()));
    }

    @GetMapping("/discrepancies")
    @Operation(summary = "Only providers needing attention — the operational work list")
    public ResponseEntity<ApiResponse<List<ProviderReconciliationDto>>> discrepancies() {
        return ResponseEntity.ok(ApiResponse.success(reconciliation.findDiscrepancies()));
    }

    @PostMapping("/by-provider/{providerId}/adjust")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Align totalPaid with the ledger", description = "Gated by PROVIDER_PAYMENT_POSTING_ENABLED")
    public ResponseEntity<ApiResponse<ProviderAccountAdjustmentResultDto>> adjust(
            @PathVariable Long providerId, @RequestBody AdjustProviderAccountRequest request) {
        featureGuard.requireProviderPaymentPosting();
        var user = authorizationService.getCurrentUser();
        String username = user != null ? user.getUsername() : "system";
        Long userId = user != null ? user.getId() : null;
        ProviderAccountAdjustmentResultDto result = adjustment.alignPaidTotalWithLedger(
                providerId, request.getReason(), request.getExpectedAccountVersion(), username, userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
