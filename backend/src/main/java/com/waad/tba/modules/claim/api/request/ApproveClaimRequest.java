package com.waad.tba.modules.claim.api.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import com.waad.tba.modules.claim.dto.ClaimLineReviewDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * API v1 Contract: Approve Claim Request
 * ════════════════════════════════════════════════════════════════════════════
 * 
 * ⚠️⚠️⚠️ CRITICAL FINANCIAL SAFETY CONTRACT ⚠️⚠️⚠️
 * 
 * This contract EXPLICITLY FORBIDS all amount fields.
 * The approvedAmount is CALCULATED by the backend from:
 * 
 * 1. Provider Contract Pricing (ProviderContract.pricingItem.price)
 * 2. Benefit Policy Rules (BenefitPolicy.coveragePercent, limits)
 * 3. Cost Breakdown Engine (CostCalculationService)
 * 4. Member Usage Tracking (remaining limits, deductibles)
 * 
 * Frontend CANNOT influence the approval amount under any circumstances.
 * 
 * BACKEND AUTHORITY:
 * The backend service layer is the SINGLE SOURCE OF TRUTH for all financial
 * calculations. The approval workflow guarantees:
 * 
 * - Approved amount is calculated from contract pricing
 * - Coverage limits are enforced
 * - Deductibles are correctly applied
 * - Co-pay percentages match benefit policy
 * - Member limits are respected
 * 
 * WORKFLOW:
 * 1. Reviewer calls POST /api/v1/claims/{id}/approve with this request
 * 2. Backend retrieves claim entity
 * 3. Backend calculates approved amount using CostCalculationService
 * 4. Backend validates coverage limits
 * 5. Backend saves approved claim with calculated amount
 * 6. Backend returns ClaimResponse with read-only financial fields
 * 
 * @since API v1.0
 * @version 2026-02-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveClaimRequest {

    @Valid
    @NotNull(message = "قرارات بنود المطالبة مطلوبة")
    private List<LineDecisionRequest> lineDecisions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LineDecisionRequest {
        @NotNull(message = "معرف بند المطالبة مطلوب")
        private Long lineId;

        @NotNull(message = "قرار بند المطالبة مطلوب")
        private ClaimLineReviewDecision decision;

        @Size(max = 500, message = "سبب قرار البند يجب ألا يتجاوز 500 حرف")
        private String reason;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ALLOWED FIELDS (Non-financial metadata only)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Optional approval notes from the reviewer.
     * Used to document the approval decision.
     */
    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;
    
    // ═══════════════════════════════════════════════════════════════════════════
    // ⛔⛔⛔ FORBIDDEN FIELDS - FINANCIAL SAFETY CRITICAL ⛔⛔⛔
    // ═══════════════════════════════════════════════════════════════════════════
    // 
    // The following fields are EXPLICITLY AND PERMANENTLY FORBIDDEN:
    // 
    // ❌ approvedAmount - CALCULATED by backend from cost breakdown
    // ❌ coveredAmount - CALCULATED by backend from benefit policy
    // ❌ deductions - CALCULATED by backend from deductible rules
    // ❌ totalAmount - CALCULATED by backend from contract pricing
    // ❌ netProviderAmount - CALCULATED by backend (approved - patient share)
    // ❌ patientCoPay - CALCULATED by backend from co-pay percentage
    // ❌ deductibleApplied - CALCULATED by backend from member usage
    // 
    // WHY THIS IS CRITICAL:
    // - Allows frontend-supplied approval amounts = SEVERE FINANCIAL RISK
    // - Claims module feeds Settlement module (already secured)
    // - Wrong approval amounts → Settlement errors → Provider overpayment
    // - Legal disputes, financial losses, compliance violations
    // 
    // ENFORCEMENT:
    // - This contract has NO amount fields (compile-time safety)
    // - Backend services NEVER read amount fields from requests
    // - CostCalculationService is the ONLY source of approved amounts
    // - Database constraints prevent invalid financial states
    // 
    // CALCULATION FLOW:
    // ```java
    // // Backend service layer (ApproveClaimUseCase)
    // Claim claim = claimRepository.findById(claimId);
    // 
    // // Calculate approved amount (BACKEND AUTHORITY)
    // CostBreakdown breakdown = costCalculationService.calculateApprovedAmount(
    //     claim.getRequestedAmount(),
    //     claim.getMember(),
    //     claim.getProvider(),
    //     claim.getBenefitPolicy()
    // );
    // 
    // // Set calculated values (NOT from request)
    // claim.setApprovedAmount(breakdown.getApprovedAmount());
    // claim.setPatientCoPay(breakdown.getPatientCoPay());
    // claim.setNetProviderAmount(breakdown.getNetProviderAmount());
    // claim.setStatus(ClaimStatus.APPROVED);
    // 
    // claimRepository.save(claim);
    // ```
    // 
    // FRONTEND CANNOT BYPASS THIS FLOW.
    // ═══════════════════════════════════════════════════════════════════════════
}
