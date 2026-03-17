package com.waad.tba.modules.benefitpolicy.dto;

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for returning Benefit Policy Rule information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenefitPolicyRuleResponseDto {

    private Long id;

    // Parent policy info
    private Long benefitPolicyId;
    private String benefitPolicyName;

    // Target info
    private String ruleType; // "CATEGORY" or "SERVICE"

    // Category info (if category rule)
    private Long medicalCategoryId;
    private String medicalCategoryCode;
    private String medicalCategoryName;

    // Service info (if service rule)
    private Long medicalServiceId;
    private String medicalServiceCode;
    private String medicalServiceName;

    // Coverage settings
    private Integer coveragePercent;
    private Integer effectiveCoveragePercent; // Resolved value (including fallback)
    private BigDecimal amountLimit;
    private Integer timesLimit;
    private Integer waitingPeriodDays;
    private boolean requiresPreApproval;

    // Display label
    private String label;

    private String notes;
    private boolean active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Factory method to create DTO from entity
     */
    public static BenefitPolicyRuleResponseDto fromEntity(BenefitPolicyRule rule) {
        BenefitPolicyRuleResponseDtoBuilder builder = BenefitPolicyRuleResponseDto.builder()
                .id(rule.getId())
                .coveragePercent(rule.getCoveragePercent())
                .effectiveCoveragePercent(rule.getEffectiveCoveragePercent())
                .amountLimit(rule.getAmountLimit())
                .timesLimit(rule.getTimesLimit())
                .waitingPeriodDays(rule.getWaitingPeriodDays())
                .requiresPreApproval(rule.isRequiresPreApproval())
                .notes(rule.getNotes())
                .active(rule.isActive())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .label(rule.getLabel());

        // Parent policy
        if (rule.getBenefitPolicy() != null) {
            builder.benefitPolicyId(rule.getBenefitPolicy().getId())
                    .benefitPolicyName(rule.getBenefitPolicy().getName());
        }

        // Since V228, all rules are category-based
        if (rule.isCategoryRule()) {
            builder.ruleType("CATEGORY");
            if (rule.getMedicalCategory() != null) {
                builder.medicalCategoryId(rule.getMedicalCategory().getId())
                        .medicalCategoryCode(rule.getMedicalCategory().getCode())
                        .medicalCategoryName(rule.getMedicalCategory().getName());
            }
        }

        return builder.build();
    }
}
