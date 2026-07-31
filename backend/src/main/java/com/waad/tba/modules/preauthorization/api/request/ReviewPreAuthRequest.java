package com.waad.tba.modules.preauthorization.api.request;

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
 * API Request: Review Pre-Authorization
 * 
 * SECURITY: For MEDICAL_REVIEWER and SUPER_ADMIN only.
 * Reviewers can ONLY change status, add comments, and set approved amount.
 * 
 * @since Provider Portal Security Fix (Phase 3)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewPreAuthRequest {
    
    @NotNull(message = "Status is required")
    private PreAuthStatus status;
    
    @Size(max = 1000, message = "Reviewer comment must not exceed 1000 characters")
    private String reviewerComment;
    
    @DecimalMin(value = "0.01", message = "Approved amount must be greater than 0")
    private BigDecimal approvedAmount;
    
    private BigDecimal copayPercentage;
}
