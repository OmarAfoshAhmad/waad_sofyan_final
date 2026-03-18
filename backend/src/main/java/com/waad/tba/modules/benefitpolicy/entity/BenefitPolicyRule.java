package com.waad.tba.modules.benefitpolicy.entity;

import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * BenefitPolicyRule Entity - defines coverage rules within a Benefit Policy.
 * 
 * Each rule specifies coverage for either:
 * - A specific Medical Service (e.g., "X-Ray Chest" at 100% coverage)
 * - An entire Medical Category (e.g., "All Lab Tests" at 80% coverage)
 * 
 * Business Rules:
 * - A rule must target EITHER a category OR a service, NOT both
 * - No duplicate rules (same category or service) within one policy
 * - If coveragePercent is null, inherits from parent
 * BenefitPolicy.defaultCoveragePercent
 * - If no rule exists for a service/category, the benefit is NOT covered
 */
@Entity
@Table(name = "benefit_policy_rules", indexes = {
        @Index(name = "idx_bpr_policy", columnList = "benefit_policy_id"),
        @Index(name = "idx_bpr_category", columnList = "medical_category_id"),
        @Index(name = "idx_bpr_active", columnList = "active")
}, uniqueConstraints = {
        // Prevent duplicate category rules within same policy
        @UniqueConstraint(name = "uk_bpr_policy_category", columnNames = { "benefit_policy_id", "medical_category_id" })
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class BenefitPolicyRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ═══════════════════════════════════════════════════════════════════════════
    // RELATIONSHIPS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * The parent Benefit Policy this rule belongs to
     */
    @NotNull(message = "Benefit Policy is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "benefit_policy_id", nullable = false)
    private BenefitPolicy benefitPolicy;

    /**
     * Optional: Target Medical Category (e.g., "All Lab Tests")
     * If set, this rule applies to ALL services in this category
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medical_category_id")
    private MedicalCategory medicalCategory;

    // ═══════════════════════════════════════════════════════════════════════════
    // COVERAGE SETTINGS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Coverage percentage (0-100).
     * If null, inherits from parent BenefitPolicy.defaultCoveragePercent
     * 
     * Example: 80 means 80% of the cost is covered by insurance
     */
    @Min(value = 0, message = "Coverage percent must be >= 0")
    @Max(value = 100, message = "Coverage percent must be <= 100")
    @Column(name = "coverage_percent")
    private Integer coveragePercent;

    /**
     * Maximum amount limit per claim/service (in LYD)
     * If null, no specific amount limit (policy limit applies)
     * 
     * Example: 500.00 means max 500 LYD per service claim
     */
    @DecimalMin(value = "0.00", message = "Amount limit must be >= 0")
    @Column(name = "amount_limit", precision = 15, scale = 2)
    private BigDecimal amountLimit;

    /**
     * Maximum number of times this benefit can be used per period
     * If null, unlimited times (within policy limits)
     * 
     * Example: 12 means max 12 times per year
     */
    @Min(value = 0, message = "Times limit must be >= 0")
    @Column(name = "times_limit")
    private Integer timesLimit;

    /**
     * Waiting period in days before benefit becomes effective
     * If null or 0, no waiting period
     */
    @Min(value = 0, message = "Waiting period must be >= 0")
    @Column(name = "waiting_period_days")
    @Builder.Default
    private Integer waitingPeriodDays = 0;

    /**
     * Whether this benefit requires pre-approval before use
     */
    @Column(name = "requires_pre_approval", nullable = false)
    @Builder.Default
    private boolean requiresPreApproval = false;

    /**
     * Optional notes about this rule
     */
    @Size(max = 500, message = "Notes must not exceed 500 characters")
    @Column(length = 500)
    private String notes;

    /**
     * Whether this rule is active
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    // ═══════════════════════════════════════════════════════════════════════════
    // AUDIT FIELDS
    // ═══════════════════════════════════════════════════════════════════════════

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ═══════════════════════════════════════════════════════════════════════════
    // BUSINESS METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if this rule targets a category.
     * All rules are category rules since V228.
     */
    public boolean isCategoryRule() {
        return medicalCategory != null;
    }

    /**
     * Get the effective coverage percent (falls back to policy default)
     */
    public int getEffectiveCoveragePercent() {
        if (coveragePercent != null) {
            return coveragePercent;
        }
        if (benefitPolicy != null && benefitPolicy.getDefaultCoveragePercent() != null) {
            return benefitPolicy.getDefaultCoveragePercent();
        }
        return 80; // System default
    }

    /**
     * Check if this rule applies to a given category
     */
    public boolean appliesToCategory(MedicalCategory category) {
        if (!active) {
            return false;
        }
        return medicalCategory != null && medicalCategory.getId().equals(category.getId());
    }

    /**
     * Get a descriptive label for this rule
     */
    public String getLabel() {
        if (medicalCategory != null) {
            return "Category: " + medicalCategory.getName();
        }
        return "Rule #" + id;
    }

    /**
     * Validate that the rule targets a category (mandatory since V228)
     */
    @PrePersist
    @PreUpdate
    public void validateTarget() {
        if (medicalCategory == null) {
            throw new IllegalStateException(
                    "Rule must target a medical category (service-level rules removed in V228)");
        }
    }
}
