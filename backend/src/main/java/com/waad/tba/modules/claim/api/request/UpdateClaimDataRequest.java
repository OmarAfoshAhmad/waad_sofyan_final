package com.waad.tba.modules.claim.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * API v1 Request: Update Claim Data
 * 
 * SECURITY: This endpoint is for PROVIDER and EMPLOYER_ADMIN only.
 * Allowed ONLY when claim status is DRAFT or NEEDS_CORRECTION.
 * 
 * @since Provider Portal Security Fix (Phase 0)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateClaimDataRequest {

    @Size(max = 255, message = "Doctor name must not exceed 255 characters")
    private String doctorName;

    @Size(max = 20, message = "Diagnosis code must not exceed 20 characters")
    private String diagnosisCode;

    @Size(max = 500, message = "Diagnosis description must not exceed 500 characters")
    private String diagnosisDescription;

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;

    private String complaint;
    private String rejectionReason;

    private Long preAuthorizationId;

    /**
     * Draft lines update (allowed only in DRAFT/NEEDS_CORRECTION)
     */
    @Valid
    private List<ClaimLineRequest> lines;

    /**
     * Manual category selection — provider can override the derived category
     */
    private String primaryCategoryCode;
    private Boolean manualCategoryEnabled;

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

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;

        private Long serviceCategoryId;

        @Size(max = 255, message = "Service category name must not exceed 255 characters")
        private String serviceCategoryName;
        private java.math.BigDecimal unitPrice;
        private java.math.BigDecimal grossAmount;
        private java.math.BigDecimal coveredAmount;
        private java.math.BigDecimal refusedAmount;
        private String serviceCode;
        private String serviceName;
        private Boolean rejected;
        private String rejectionReason;
    }
}
