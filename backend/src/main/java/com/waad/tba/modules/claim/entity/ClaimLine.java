package com.waad.tba.modules.claim.entity;

import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
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
        @Index(name = "idx_claim_line_service", columnList = "medical_service_id"),
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

    // ==================== MEDICAL SERVICE (CONTRACT-DRIVEN) ====================

    /**
     * Medical Service (FK)
     * ARCHITECTURAL LAW: Service MUST be selected from Provider Contract - NO
     * free-text
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medical_service_id")
    private MedicalService medicalService;

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
     * This might be different from serviceCategoryId if Claim.manualCategoryEnabled is TRUE.
     */
    @Column(name = "applied_category_id")
    private Long appliedCategoryId;

    /**
     * Denormalized name of the applied category.
     */
    @Column(name = "applied_category_name", length = 200)
    private String appliedCategoryName;

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
        if (this.medicalService != null) {
            this.serviceCode = this.medicalService.getCode();
            this.serviceName = this.medicalService.getName();

            // Only update category from DB if it exists there,
            // otherwise keep the one sent from the UI
            if (this.medicalService.getCategoryId() != null) {
                this.serviceCategoryId = this.medicalService.getCategoryId();
            }

            this.requiresPA = this.medicalService.isRequiresPA();
        }
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

        // RULE: MedicalService is MANDATORY
        if (medicalService == null) {
            throw new IllegalStateException("ARCHITECTURAL VIOLATION: ClaimLine MUST reference a MedicalService");
        }

        // RULE: Category is MANDATORY (must come from service)
        if (serviceCategoryId == null) {
            throw new IllegalStateException(
                    "ARCHITECTURAL VIOLATION: ClaimLine MUST have a medical category. " +
                            "Service selection without category is not allowed.");
        }

        // RULE: Service must belong to the selected category
        if (medicalService.getCategoryId() != null &&
                !medicalService.getCategoryId().equals(serviceCategoryId)) {
            throw new IllegalStateException(
                    "ARCHITECTURAL VIOLATION: Medical service does not belong to the selected category. " +
                            "Service categoryId=" + medicalService.getCategoryId() +
                            ", selected categoryId=" + serviceCategoryId);
        }

        // RULE: Unit price must be set (from contract)
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(
                    "ARCHITECTURAL VIOLATION: Unit price must be resolved from Provider Contract");
        }

        // RULE: Quantity must be positive
        if (quantity == null || quantity <= 0) {
            throw new IllegalStateException("Quantity must be a positive number");
        }
    }
}
