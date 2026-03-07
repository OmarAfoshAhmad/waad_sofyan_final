package com.waad.tba.modules.claim.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for approving a claim.
 * 
 * Used by: POST /api/claims/{id}/approve
 * 
 * Business Rules:
 * - approvedAmount must be > 0
 * - approvedAmount must not exceed requestedAmount
 * - Cost breakdown will be calculated and validated automatically
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimApproveDto {
    
    /**
     * The amount approved by the reviewer.
     * Must be positive and not exceed requested amount.
     * If null, system will calculate based on coverage.
     */
    @DecimalMin(value = "0.01", message = "المبلغ المعتمد يجب أن يكون أكبر من صفر")
    private BigDecimal approvedAmount;
    
    /**
     * Optional notes from the reviewer.
     */
    private String notes;
    
    /**
     * Whether to use system-calculated amount instead of manual.
     * If true, approvedAmount is ignored and calculated from cost breakdown.
     */
    @Builder.Default
    private Boolean useSystemCalculation = false;
}
