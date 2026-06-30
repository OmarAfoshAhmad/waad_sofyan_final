package com.waad.tba.modules.benefitpolicy.dto;

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * DTO for Benefit Policy responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BenefitPolicyResponseDto {

    private Long id;
    private String name;
    private String policyCode;
    private String description;

    // Employer info
    private Long employerOrgId;
    private String employerName;

    // Dates
    private LocalDate startDate;
    private LocalDate endDate;

    // Limits
    private BigDecimal annualLimit;
    private Integer defaultCoveragePercent;
    private BigDecimal perMemberLimit;
    private BigDecimal perFamilyLimit;

    // Status
    private BenefitPolicyStatus status;
    private String statusDisplay;
    private boolean effective;  // Is currently effective (active + within dates)

    // Stats
    private Integer coveredMembersCount;
    private Integer rulesCount;
    private Integer activeRulesCount;

    // Metadata
    private String notes;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Excluded Categories
    private Set<String> excludedCategoryCodes;

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTORY METHOD
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Convert entity to response DTO
     */
    public static BenefitPolicyResponseDto fromEntity(BenefitPolicy entity) {
        if (entity == null) {
            return null;
        }

        return BenefitPolicyResponseDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .policyCode(entity.getPolicyCode())
                .description(entity.getDescription())
                .employerOrgId(entity.getEmployer() != null 
                        ? entity.getEmployer().getId() : null)
                .employerName(entity.getEmployer() != null 
                        ? entity.getEmployer().getName() : null)
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .annualLimit(entity.getAnnualLimit())
                .defaultCoveragePercent(entity.getDefaultCoveragePercent())
                .perMemberLimit(entity.getPerMemberLimit())
                .perFamilyLimit(entity.getPerFamilyLimit())
                .status(entity.getStatus())
                .statusDisplay(getStatusDisplay(entity.getStatus()))
                .effective(entity.isEffective())
                .coveredMembersCount(entity.getCoveredMembersCount())
                .rulesCount(entity.getRules() != null ? entity.getRules().size() : 0)
                .activeRulesCount(entity.getRules() != null ? entity.getActiveRulesCount() : 0)
                .notes(entity.getNotes())
                .active(entity.isActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .excludedCategoryCodes(entity.getExcludedCategoryCodes())
                .build();
    }

    /**
     * Get localized status display text
     */
    private static String getStatusDisplay(BenefitPolicyStatus status) {
        if (status == null) return "";
        return switch (status) {
            case DRAFT -> "مسودة";
            case ACTIVE -> "نشط";
            case EXPIRED -> "منتهي";
            case SUSPENDED -> "موقوف";
            case CANCELLED -> "ملغي";
        };
    }
}
