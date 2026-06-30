package com.waad.tba.modules.benefitpolicy.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * DTO for updating an existing Benefit Policy.
 * All fields are optional - only provided fields will be updated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenefitPolicyUpdateDto {

    @Size(max = 255, message = "Policy name must not exceed 255 characters")
    private String name;

    @Size(max = 50, message = "Policy code must not exceed 50 characters")
    private String policyCode;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    // NOTE: employerOrgId is IMMUTABLE per contract
    // Cannot be changed after policy creation - set at creation only

    private LocalDate startDate;

    private LocalDate endDate;

    @DecimalMin(value = "0.00", message = "Annual limit must be >= 0")
    private BigDecimal annualLimit;

    @Min(value = 0, message = "Coverage percent must be >= 0")
    @Max(value = 100, message = "Coverage percent must be <= 100")
    private Integer defaultCoveragePercent;

    @DecimalMin(value = "0.00", message = "Per-member limit must be >= 0")
    private BigDecimal perMemberLimit;

    @DecimalMin(value = "0.00", message = "Per-family limit must be >= 0")
    private BigDecimal perFamilyLimit;

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;

    /**
     * Update status directly (use activate/deactivate endpoints for status changes)
     */
    private String status;

    private Set<String> excludedCategoryCodes;
}
