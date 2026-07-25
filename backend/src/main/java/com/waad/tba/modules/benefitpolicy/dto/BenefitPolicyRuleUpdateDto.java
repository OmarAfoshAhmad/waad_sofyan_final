package com.waad.tba.modules.benefitpolicy.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for updating an existing Benefit Policy Rule.
 * All fields are optional - only provided fields will be updated.
 * Note: Cannot change the target (category/service) after creation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenefitPolicyRuleUpdateDto {

    /**
     * Coverage percentage (0-100)
     */
    @Min(value = 0, message = "Coverage percent must be >= 0")
    @Max(value = 100, message = "Coverage percent must be <= 100")
    private Integer coveragePercent;


    @DecimalMin(value = "0.00", message = "Copay percentage must be >= 0")
    @DecimalMax(value = "100.00", message = "Copay percentage must be <= 100")
    private BigDecimal copayPercentage;


    /**
     * Waiting period in days
     */
    @Min(value = 0, message = "Waiting period must be >= 0")
    private Integer waitingPeriodDays;

    /**
     * Whether this benefit requires pre-approval
     */
    private Boolean requiresPreApproval;

    /**
     * Optional notes
     */
    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;

    private String encounterType;

    private Boolean inheritanceEnabled;

    @Min(value = 0, message = "Priority must be >= 0")
    private Integer priority;

    /**
     * Whether the rule is active
     */
    private Boolean active;
}
