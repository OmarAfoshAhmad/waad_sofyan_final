package com.waad.tba.modules.preauthorization.dto;

import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for REVIEWER actions on pre-authorizations.
 * 
 * SECURITY: This DTO can ONLY be used by REVIEWER and INSURANCE_ADMIN roles.
 * Reviewers can ONLY change status, add comments, and set approved amount.
 * They CANNOT modify pre-auth data fields.
 * 
 * @since Provider Portal Security Fix (Phase 3)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreAuthReviewDto {
    
    /**
     * New status for the pre-authorization
     * Must follow state machine transitions
     */
    @NotNull(message = "Status is required")
    private PreAuthStatus status;
    
    /**
     * Reviewer comment
     * MANDATORY for REJECTED and NEEDS_CORRECTION statuses
     */
    @Size(max = 1000, message = "Reviewer comment must not exceed 1000 characters")
    private String reviewerComment;
    
    /**
     * Approved amount (for APPROVED status)
     * Optional - pre-authorizations no longer have strict financial matters
     */
    private BigDecimal approvedAmount;
    
    /**
     * Copay percentage (if different from policy default)
     */
    private BigDecimal copayPercentage;
}
