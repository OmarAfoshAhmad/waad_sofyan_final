package com.waad.tba.modules.claim.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * API v1 Contract: Create Claim Request
 * ════════════════════════════════════════════════════════════════════════════
 * 
 * FINANCIAL SAFETY GUARANTEE:
 * This contract does NOT accept any monetary values from the frontend.
 * All amounts are calculated by the backend from:
 * - Provider Contract pricing
 * - Medical service cost breakdown
 * - Benefit policy rules
 * 
 * ARCHITECTURAL LAWS:
 * 1. visitId is MANDATORY - claims can only be created from existing visits
 * 2. lines (services) are MANDATORY - no empty claims
 * 3. providerId is AUTO-FILLED from JWT security context
 * 4. All prices are AUTO-RESOLVED from Provider Contract
 * 5. If service requires PA, preAuthorizationId is MANDATORY
 * 
 * @since API v1.0
 * @version 2026-02-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateClaimRequest {

    // ═══════════════════════════════════════════════════════════════════════════
    // MANDATORY FIELDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * REQUIRED: Visit ID that this claim is linked to.
     * ARCHITECTURAL LAW: Claims can ONLY be created from an existing Visit.
     */
    @NotNull(message = "Visit ID is required - Claims must originate from a Visit")
    @Positive(message = "Visit ID must be positive")
    private Long visitId;

    /**
     * REQUIRED: Claim lines with medical services
     * ARCHITECTURAL LAW: At least one service line is required - NO empty claims
     */
    @NotEmpty(message = "At least one claim line (service) is required")
    @Valid
    private List<ClaimLineRequest> lines;

    // ═══════════════════════════════════════════════════════════════════════════
    // OPTIONAL REFERENCE FIELDS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Member ID - AUTO-DERIVED from Visit (can be provided for validation)
     */
    private Long memberId;

    /**
     * Provider ID - AUTO-FILLED from JWT security context
     * ARCHITECTURAL LAW: Provider CANNOT override their identity
     */
    private Long providerId;

    // ═══════════════════════════════════════════════════════════════════════════
    // DIAGNOSIS (Selected from dropdown, not free-text)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Diagnosis ICD-10 code (selected from dropdown)
     */
    @Size(max = 20, message = "Diagnosis code must not exceed 20 characters")
    private String diagnosisCode;

    /**
     * Diagnosis description (auto-populated from code)
     */
    @Size(max = 500, message = "Diagnosis description must not exceed 500 characters")
    private String diagnosisDescription;

    // ═══════════════════════════════════════════════════════════════════════════
    // PRE-AUTHORIZATION LINK
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Pre-authorization ID (REQUIRED if any service requires PA)
     */
    private Long preAuthorizationId;

    // ═══════════════════════════════════════════════════════════════════════════
    // OPTIONAL METADATA
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Doctor name who performed the service
     */
    @Size(max = 255, message = "Doctor name must not exceed 255 characters")
    private String doctorName;

    /**
     * Service date (defaults to today)
     */
    private LocalDate serviceDate;

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;

    /**
     * Optional: Target status for the claim (e.g., SETTLED, REJECTED)
     * Defaults to SETTLED if not specified to bypass DRAFT.
     */
    private com.waad.tba.modules.claim.entity.ClaimStatus status;

    /**
     * Patient complaint or clinical notes (for reviewer context)
     */
    private String complaint;

    /**
     * Optional: Global rejection reason for the entire claim
     */
    private String rejectionReason;

    /**
     * Whether the user manually selected a coverage category context.
     * If FALSE (default), coverage is determined from each service's own category.
     * If TRUE, every service in the claim uses the rule from primaryCategoryCode.
     */
    private Boolean manualCategoryEnabled;

    /**
     * The primary category code used for coverage context.
     * Always send this so unmapped services get appliedCategoryId set correctly.
     * Example: "CAT-OUTPAT" (عيادات خارجية), "CAT-OPER" (عمليات)
     */
    @Size(max = 50, message = "Primary category code must not exceed 50 characters")
    private String primaryCategoryCode;

    /**
     * Full coverage override: 100% coverage, bypasses all limits.
     */
    private Boolean fullCoverage;

    // ═══════════════════════════════════════════════════════════════════════════
    // ⛔ FORBIDDEN FIELDS - FINANCIAL SAFETY
    // ═══════════════════════════════════════════════════════════════════════════
    // The following fields are EXPLICITLY FORBIDDEN in API v1 contracts:
    //
    // ❌ requestedAmount - Calculated from contract pricing
    // ❌ approvedAmount - Calculated during approval workflow
    // ❌ totalAmount - Calculated from lines
    // ❌ netProviderAmount - Calculated by cost breakdown engine
    // ❌ patientCoPay - Calculated by benefit policy rules
    // ❌ deductibleApplied - Calculated by benefit policy rules
    //
    // Backend calculates ALL financial values to ensure integrity.
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Nested request for claim line items
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimLineRequest {

        /**
         * Medical service ID (from MedicalTaxonomy)
         * Optional if pricingItemId is provided
         */
        @Positive(message = "Medical service ID must be positive")
        private Long medicalServiceId;

        /**
         * Optional: Specific Pricing Item ID from Provider Contract
         * Used for items that are verified in contract but not yet mapped to taxonomy
         */
        private Long pricingItemId;

        /**
         * Quantity of service (e.g., number of sessions)
         */
        @NotNull(message = "Quantity is required")
        @Positive(message = "Quantity must be positive")
        private Integer quantity;

        /**
         * Optional: Service category ID (for coverage resolution)
         */
        private Long serviceCategoryId;

        /**
         * Optional: Service category name (denormalized)
         */
        private String serviceCategoryName;

        /**
         * Optional: Unit Price (REQUIRED for LEGACY_BACKLOG claims)
         * ARCHITECTURAL LAW: For regular claims, this is ignored and resolved from
         * contract.
         * For backlog claims, this is used as the source of truth.
         */
        private java.math.BigDecimal unitPrice;

        /**
         * Whether this specific line is rejected by the provider
         */
        private Boolean rejected;

        /**
         * Reason for rejection (if rejected)
         */
        private String rejectionReason;

        @Size(max = 50, message = "Service code must not exceed 50 characters")
        private String serviceCode;

        @Size(max = 255, message = "Service name must not exceed 255 characters")
        private String serviceName;

        // ❌ NO totalPrice - Calculated as quantity * unitPrice
        // ❌ NO approvedPrice - Calculated during approval
    }
}
