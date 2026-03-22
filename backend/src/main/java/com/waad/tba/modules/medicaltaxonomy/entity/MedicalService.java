package com.waad.tba.modules.medicaltaxonomy.entity;

import com.waad.tba.modules.medicaltaxonomy.enums.MedicalServiceStatus;
import com.waad.tba.common.exception.ArchitecturalViolationException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Medical Service Entity (Reference Data)
 * 
 * ══════════════════════════════════════════════════════════════════════════════
 * ARCHITECTURAL RULES (NON-NEGOTIABLE)
 * ══════════════════════════════════════════════════════════════════════════════
 * 
 * 1. CATEGORY IS MANDATORY FOR ACTIVE SERVICES
 * - Draft services can exist without category (Name-Only Import Safe Mode)
 * - Active services MUST have a category
 * 
 * 2. BASE PRICE IS REFERENCE ONLY
 * - basePrice is for estimation and reporting
 * - Actual price comes from ProviderContract.contractPrice
 * - NEVER use basePrice for claim calculation
 * 
 * 3. PA REQUIREMENT COMES FROM POLICY
 * - requiresPA field is DEPRECATED
 * - Actual PA requirement is determined by BenefitPolicyRule
 * - This field remains for backward compatibility only
 * 
 * ══════════════════════════════════════════════════════════════════════════════
 * 
 * Scope: Pure reference data (NO coverage, claim, provider, or network logic)
 * 
 * Examples:
 * - SRV-CARDIO-001: "Comprehensive Cardiac Exam"
 * - SRV-LAB-CBC: "Complete Blood Count"
 * - SRV-IMAGING-XRAY: "Chest X-Ray"
 */
@Entity
@Table(name = "medical_services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique business identifier (immutable)
     * Examples: "SRV-CARDIO-001", "SRV-LAB-CBC", "SRV-IMAGING-XRAY"
     */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MedicalServiceStatus status = MedicalServiceStatus.ACTIVE;

    /**
     * Service name (unified - Arabic-only system)
     */
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /**
     * Reference to medical category
     * 
     * ARCHITECTURAL RULE: This field is MANDATORY for ACTIVE services
     * - Links service to classification hierarchy
     * - Required for coverage fallback resolution
     * - Can be null ONLY if status = DRAFT
     */
    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "specialty_id")
    private Long specialtyId;

    /**
     * Service description (optional)
     */
    @Column(name = "description", length = 500)
    private String description;

    /**
     * [REFERENCE ONLY] Base/reference price
     * 
     * @deprecated This field is for reference only.
     *             Actual price must come from ProviderContract.contractPrice
     * 
     *             Purpose:
     *             - Baseline for price estimation
     *             - Out-of-network fallback
     *             - Reporting and analytics
     * 
     *             NOT used for:
     *             - Final claim calculation (use ProviderContract rate)
     *             - Coverage calculation (use BenefitPolicyRule)
     */
    @Deprecated(since = "2026-01-22", forRemoval = false)
    @Column(name = "base_price", precision = 10, scale = 2)
    private BigDecimal basePrice;

    /**
     * [DEPRECATED] Flag indicating if service requires pre-authorization
     * 
     * @deprecated PA requirement is now determined ONLY by BenefitPolicyRule.
     *             This field remains for backward compatibility but should not be
     *             used
     *             for business logic.
     * 
     *             Use BenefitPolicyCoverageService.requiresPreApproval() instead.
     */
    @Deprecated(since = "2026-01-22", forRemoval = false)
    @Column(name = "requires_pa", nullable = false)
    @Builder.Default
    private boolean requiresPA = true;

    /**
     * Arabic display name (unified catalog — bilingual support)
     */
    @Column(name = "name_ar", length = 255)
    private String nameAr;

    /**
     * English display name (unified catalog — bilingual support)
     */
    @Column(name = "name_en", length = 255)
    private String nameEn;

    /**
     * Catalog reference cost (unified pricing anchor for the service).
     * Distinct from {@link #basePrice}: base_price is the legacy estimation field;
     * cost is the authoritative unified-catalog amount.
     */
    @Column(name = "cost", precision = 15, scale = 2)
    private BigDecimal cost;

    /**
     * Master catalog flag.
     * {@code true} = canonical reference entry; {@code false} = alias/variant.
     */
    @Column(name = "is_master", nullable = false)
    @Builder.Default
    private boolean isMaster = false;

    /**
     * Soft-delete flag (unified catalog pattern)
     */
    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    /**
     * Timestamp when the record was soft-deleted
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * ID of the user who soft-deleted this record
     */
    @Column(name = "deleted_by")
    private Long deletedBy;

    /**
     * Soft delete flag (legacy — kept for backward compat with older code paths)
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Audit: creation timestamp
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Audit: last update timestamp
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        validateArchitecturalRules();
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        validateArchitecturalRules();
        updatedAt = LocalDateTime.now();
    }

    /**
     * Validate architectural rules before persist/update.
     * These rules are NON-NEGOTIABLE.
     */
    private void validateArchitecturalRules() {
        // RULE 1: Category is mandatory for ACTIVE services
        if ((status == MedicalServiceStatus.ACTIVE || active) && categoryId == null) {
            throw ArchitecturalViolationException.serviceWithoutCategory(code);
        }

        // Auto-correct: If status is DRAFT, force active = false
        if (status == MedicalServiceStatus.DRAFT) {
            this.active = false;
        }
    }

    /**
     * Get the category ID (non-null guaranteed by architectural rule)
     */
    public Long getCategoryId() {
        return categoryId;
    }

    /**
     * Get base price (reference only - not for calculation)
     * 
     * @deprecated Use ProviderContract.contractPrice for actual pricing
     */
    @Deprecated
    public BigDecimal getBasePrice() {
        return basePrice;
    }

    /**
     * Check if PA is required (deprecated - use policy rules instead)
     * 
     * @deprecated Use BenefitPolicyCoverageService.requiresPreApproval()
     */
    @Deprecated
    public boolean isRequiresPA() {
        return requiresPA;
    }
}
