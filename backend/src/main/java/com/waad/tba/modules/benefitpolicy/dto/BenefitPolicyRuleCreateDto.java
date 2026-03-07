package com.waad.tba.modules.benefitpolicy.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for creating a new Benefit Policy Rule.
 * 
 * Either medicalCategoryId OR medicalServiceId must be provided, but not both.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenefitPolicyRuleCreateDto {

    /**
     * Target Medical Category ID (for category-level rules)
     * Mutually exclusive with medicalServiceId
     */
    @Positive(message = "Medical Category ID must be positive")
    private Long medicalCategoryId;

    /**
     * Target Medical Service ID (for service-specific rules)
     * Mutually exclusive with medicalCategoryId
     */
    @Positive(message = "Medical Service ID must be positive")
    private Long medicalServiceId;

    /**
     * Coverage percentage (0-100)
     * If null, inherits from parent policy's defaultCoveragePercent
     */
    @Min(value = 0, message = "Coverage percent must be >= 0")
    @Max(value = 100, message = "Coverage percent must be <= 100")
    private Integer coveragePercent;

    /**
     * Maximum amount limit per claim (in LYD)
     */
    @DecimalMin(value = "0.00", message = "Amount limit must be >= 0")
    private BigDecimal amountLimit;

    /**
     * Maximum times this benefit can be used per period
     */
    @Min(value = 0, message = "Times limit must be >= 0")
    private Integer timesLimit;

    /**
     * Waiting period in days before benefit is effective
     */
    @Min(value = 0, message = "Waiting period must be >= 0")
    @Builder.Default
    private Integer waitingPeriodDays = 0;

    /**
     * Whether this benefit requires pre-approval
     */
    @Builder.Default
    private Boolean requiresPreApproval = false;

    /**
     * Optional notes
     */
    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;

    /**
     * Whether the rule is active
     */
    @Builder.Default
    private Boolean active = true;
}
