package com.waad.tba.modules.claim.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * ClaimLine DTO (CANONICAL REBUILD 2026-01-16)
 * 
 * ARCHITECTURAL LAW:
 * - medicalServiceId is MANDATORY - NO free-text services
 * - unitPrice is AUTO-RESOLVED from Provider Contract (read-only in response)
 * - totalPrice is SERVER-CALCULATED (read-only)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimLineDto {

    private Long id;

    // ==================== INPUT (for create/update) ====================

    /**
     * Optional: Medical Service ID (from Provider Contract)
     * If null, pricingItemId or serviceName/Code must be used.
     */
    @Positive(message = "Medical Service ID must be positive")
    private Long medicalServiceId;

    /**
     * Optional: Pricing Item ID (from Provider Contract Pricing Items)
     */
    private Long pricingItemId;

    /**
     * REQUIRED: Quantity of service
     */
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Builder.Default
    private Integer quantity = 1;

    // ==================== OUTPUT (read-only in response) ====================

    /**
     * Service code (denormalized from MedicalService)
     */
    private String serviceCode;

    /**
     * Service name (denormalized from MedicalService)
     */
    private String serviceName;

    /**
     * Service category ID
     */
    private Long serviceCategoryId;

    /**
     * Service category name
     */
    private String serviceCategoryName;

    /**
     * Unit price from Provider Contract (READ-ONLY)
     */
    private BigDecimal unitPrice;

    /**
     * Total price (SERVER-CALCULATED: quantity × unitPrice) (READ-ONLY)
     */
    private BigDecimal totalPrice;

    /**
     * Whether service requires pre-authorization
     */
    private Boolean requiresPA;

    private Boolean rejected;
    private String rejectionReason;
    private String rejectionReasonCode;
    private String reviewerNotes;
    private BigDecimal refusedAmount;
    private BigDecimal manualRefusedAmount;

    // Financial Audit (READ-ONLY)
    private BigDecimal requestedUnitPrice;
    private BigDecimal approvedUnitPrice;
    private Integer requestedQuantity;
    private Integer approvedQuantity;

    // Financial Split (READ-ONLY)
    private Integer coveragePercent;
    private Integer patientSharePercent;
    private BigDecimal benefitLimit;
    private BigDecimal usedAmount;
    private BigDecimal remainingAmount;
    private BigDecimal companyShare;
    private BigDecimal patientShare;

    private Long appliedCategoryId;
    private String appliedCategoryName;
}
