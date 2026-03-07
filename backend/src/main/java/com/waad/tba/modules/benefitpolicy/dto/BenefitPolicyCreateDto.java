package com.waad.tba.modules.benefitpolicy.dto;

import com.waad.tba.common.validation.ValidDateRange;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for creating a new Benefit Policy.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidDateRange(startField = "startDate", endField = "endDate")
public class BenefitPolicyCreateDto {

    @NotBlank(message = "Policy name is required")
    @Size(max = 255, message = "Policy name must not exceed 255 characters")
    private String name;

    @Size(max = 50, message = "Policy code must not exceed 50 characters")
    private String policyCode;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    @NotNull(message = "Employer organization ID is required")
    @Positive(message = "Employer organization ID must be positive")
    private Long employerOrgId;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @NotNull(message = "Annual limit is required")
    @DecimalMin(value = "0.00", message = "Annual limit must be >= 0")
    private BigDecimal annualLimit;

    @NotNull(message = "Default coverage percent is required")
    @Min(value = 0, message = "Coverage percent must be >= 0")
    @Max(value = 100, message = "Coverage percent must be <= 100")
    @Builder.Default
    private Integer defaultCoveragePercent = 80;

    @DecimalMin(value = "0.00", message = "Per-member limit must be >= 0")
    private BigDecimal perMemberLimit;

    @DecimalMin(value = "0.00", message = "Per-family limit must be >= 0")
    private BigDecimal perFamilyLimit;

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;

    /**
     * Initial status (defaults to DRAFT if not specified)
     */
    private String status;
}
