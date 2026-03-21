package com.waad.tba.modules.claim.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * ClaimLine Entity (CANONICAL REBUILD 2026-01-16)
 * 
 * ARCHITECTURAL LAW:
 * - Each line MUST reference a MedicalService (FK) - NO free-text services
 * - Unit price is AUTO-RESOLVED from Provider Contract - NO manual entry
 * - Total price is SERVER-CALCULATED: quantity × unitPrice
 * 
 * Data Flow: MedicalService (from Contract) → ContractPrice (auto) → TotalPrice
 * (calculated)
 */
@Entity
@Table(name = "claim_lines", indexes = {
        @Index(name = "idx_claim_lines_category", columnList = "service_category_id"),
        @Index(name = "idx_claim_line_claim", columnList = "claim_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SuppressWarnings("deprecation")
public class ClaimLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic Locking Version (FINANCIAL HARDENING - Defense-in-Depth)
     * 
     * Provides additional concurrency protection for claim line modifications.
     * While ClaimLine modifications are typically protected by parent Claim's
     * PESSIMISTIC lock, this @Version provides defense-in-depth for scenarios
     * where:
     * - Line-level API updates might be exposed
     * - Draft claim editing happens concurrently
     * 
     * Prevents lost updates if two transactions modify the same line
     * simultaneously.
     * 
     * @since Financial Hardening Phase - Post-Production Enhancement
     */
    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    // ==================== MEDICAL SERVICE IDENTIFICATION ====================

    /**
     * Pricing Item ID (from provider_contract_pricing_items)
     * Links this line directly to a specific contract price entry.
     * This is the primary reference to provider service (replaces MedicalService
     * FK).
     */
    @Column(name = "pricing_item_id")
    private Long pricingItemId;

    /**
     * Service code (denormalized snapshot for reports/queries)
     */
    @Column(name = "service_code", length = 50, nullable = false)
    private String serviceCode;

    /**
     * Service name (denormalized snapshot at claim time)
     */
    @Column(name = "service_name", length = 255)
    private String serviceName;

    /**
     * Medical Category ID (MANDATORY - ARCHITECTURAL LAW)
     * 
     * RULE: Coverage resolution requires BOTH category AND service.
     * The same service can have different coverage in different categories.
     * This field MUST be populated from the selected MedicalService.categoryId.
     */
    @Column(name = "service_category_id")
    private Long serviceCategoryId;

    /**
     * Medical Category Name (denormalized snapshot for reports)
     */
    @Column(name = "service_category_name", length = 200)
    private String serviceCategoryName;

    // ==================== APPLIED COVERAGE RESOLUTION ====================

    /**
     * The category ID actually used for coverage calculation.
     * This might be different from serviceCategoryId if Claim.manualCategoryEnabled
     * is TRUE.
     */
    @Column(name = "applied_category_id")
    private Long appliedCategoryId;

    /**
     * Denormalized name of the applied category.
     */
    @Column(name = "applied_category_name", length = 200)
    private String appliedCategoryName;

    /**
     * Benefit limit for this service/category at the time of claim creation.
     * Denormalized from BenefitPolicyRule for audit trail and faster display.
     */
    @Column(name = "benefit_limit", precision = 15, scale = 2)
    private BigDecimal benefitLimit;

    /**
     * Used amount from the benefit limit at the time of claim creation.
     * Snapshot for historical accuracy.
     */
    @Column(name = "used_amount_snapshot", precision = 15, scale = 2)
    private BigDecimal usedAmountSnapshot;

    @Column(name = "remaining_amount_snapshot", precision = 15, scale = 2)
    private BigDecimal remainingAmountSnapshot;

    // ==================== QUANTITY & PRICING ====================

    /**
     * Quantity of service
     */
    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    /**
     * Unit price from Provider Contract (READ-ONLY, auto-resolved)
     * ARCHITECTURAL LAW: This is NOT user-editable
     */
    @Column(name = "unit_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    /**
     * Total price (SERVER-CALCULATED: quantity × unitPrice)
     * ARCHITECTURAL LAW: This is auto-calculated, not user-entered
     */
    @Column(name = "total_price", precision = 15, scale = 2, nullable = false)
    private BigDecimal totalPrice;

    /**
     * Whether service requires pre-authorization (snapshot from MedicalService)
     */
    @Column(name = "requires_pa")
    @Builder.Default
    private Boolean requiresPA = false;

    // ==================== COVERAGE SNAPSHOT (FINANCIAL AUDIT TRAIL)
    // ====================

    /**
     * Coverage percentage at time of claim creation (snapshot from
     * BenefitPolicyRule)
     * IMPORTANT: This is stored as snapshot and should NOT be recalculated after
     * creation
     */
    @Column(name = "coverage_percent_snapshot")
    private Integer coveragePercentSnapshot;

    /**
     * Patient copay percentage at time of claim creation (snapshot from
     * BenefitPolicyRule)
     * IMPORTANT: This is stored as snapshot and should NOT be recalculated after
     * creation
     */
    @Column(name = "patient_copay_percent_snapshot")
    private Integer patientCopayPercentSnapshot;

    @Column(name = "times_limit_snapshot")
    private Integer timesLimitSnapshot;

    @Column(name = "amount_limit_snapshot", precision = 15, scale = 2)
    private BigDecimal amountLimitSnapshot;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /**
     * Rejection reason code (e.g., "PRICE_EXCEEDED", "NOT_COVERED",
     * "PRE_AUTH_REQUIRED")
     */
    @Column(name = "rejection_reason_code", length = 50)
    private String rejectionReasonCode;

    /**
     * Detailed notes from the medical reviewer
     */
    @Column(name = "reviewer_notes", columnDefinition = "TEXT")
    private String reviewerNotes;

    @Column(name = "rejected")
    @Builder.Default
    private Boolean rejected = false;

    @Column(name = "refused_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal refusedAmount = BigDecimal.ZERO;

    @Column(name = "manual_refused_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal manualRefusedAmount = BigDecimal.ZERO;

    // ==================== FINANCIAL AUDIT: REQUESTED VS APPROVED
    // ====================

    @Column(name = "requested_unit_price", precision = 15, scale = 2)
    private BigDecimal requestedUnitPrice;

    @Column(name = "approved_unit_price", precision = 15, scale = 2)
    private BigDecimal approvedUnitPrice;

    @Column(name = "requested_quantity")
    private Integer requestedQuantity;

    @Column(name = "approved_quantity")
    private Integer approvedQuantity;

    // ==================== LIFECYCLE HOOKS ====================

    @PrePersist
    private void prePersist() {
        populateDenormalizedFields();
        initializeFinancialAuditFields();
        calculateTotalPrice();
        validateArchitecturalRules();
    }

    @PreUpdate
    private void preUpdate() {
        populateDenormalizedFields();
        calculateTotalPrice();
        validateArchitecturalRules();
    }

    private void initializeFinancialAuditFields() {
        if (requestedUnitPrice == null)
            requestedUnitPrice = unitPrice;
        if (requestedQuantity == null)
            requestedQuantity = quantity;

        if (Boolean.TRUE.equals(rejected)) {
            approvedUnitPrice = BigDecimal.ZERO;
            approvedQuantity = 0;
        } else {
            if (approvedUnitPrice == null)
                approvedUnitPrice = unitPrice;
            if (approvedQuantity == null)
                approvedQuantity = quantity;
        }
    }

    /**
     * Populate denormalized fields from MedicalService
     */
    public void populateDenormalizedFields() {
        // serviceCode and serviceName are set directly by ClaimMapper from the pricing
        // item.
        // Ensure defaults if still null.
        if (this.serviceCode == null)
            this.serviceCode = "N/A";
        if (this.serviceName == null)
            this.serviceName = "Unknown Service";
    }

    private void calculateTotalPrice() {
        if (quantity != null && unitPrice != null) {
            totalPrice = unitPrice.multiply(new BigDecimal(quantity));
        }
    }

    /**
     * Validate architectural rules
     */
    private void validateArchitecturalRules() {
        // SKIP for backlog claims - they might not have catalog mapping
        if (claim != null && Boolean.TRUE.equals(claim.getIsBacklog())) {
            return;
        }

        // RULE: ServiceCode must be present
        if (serviceCode == null || serviceCode.isBlank()) {
            throw new IllegalStateException("ARCHITECTURAL VIOLATION: ClaimLine must have a serviceCode");
        }

        // RULE: Unit price must be set
        if (unitPrice == null) {
            throw new IllegalStateException(
                    "ARCHITECTURAL VIOLATION: Unit price must be resolved");
        }

        // RULE: Quantity must be positive
        if (quantity == null || quantity <= 0) {
            throw new IllegalStateException("Quantity must be a positive number");
        }
    }
}
