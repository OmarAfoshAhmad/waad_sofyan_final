package com.waad.tba.modules.preauthorization.entity;

import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.visit.entity.Visit;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PreAuthorization Entity (CANONICAL REBUILD 2026-01-16)
 * 
 * ARCHITECTURAL LAW:
 * - Pre-authorization is a request to approve a SYSTEM-DEFINED medical service
 * - NO free-text medical service or pricing allowed
 * - ALL data must be derived from: Visit → Provider Contract → Medical Service
 * 
 * Data Flow:
 * Visit → Diagnosis (selected) → MedicalService (from Contract) → ContractPrice
 * (auto)
 */
@Entity
@Table(name = "pre_authorizations", indexes = {
        @Index(name = "idx_preauth_member", columnList = "member_id"),
        @Index(name = "idx_preauth_provider", columnList = "provider_id"),
        @Index(name = "idx_preauth_service_id", columnList = "medical_service_id"),
        @Index(name = "idx_preauth_status", columnList = "status"),
        @Index(name = "idx_preauth_request_date", columnList = "request_date"),
        @Index(name = "idx_preauth_reference", columnList = "reference_number"),
        @Index(name = "idx_preauth_visit", columnList = "visit_id"),
        @Index(name = "idx_preauth_member_status", columnList = "member_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreAuthorization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Pre-authorization number (legacy column - required by database)
     * Format: PA-YYYYMMDD-XXXXX
     */
    @Column(name = "pre_auth_number", nullable = false, unique = true, length = 50)
    private String preAuthNumber;

    /**
     * Unique reference number for this pre-authorization
     * Format: PA-YYYYMMDD-XXXXX
     */
    @Column(name = "reference_number", length = 50)
    private String referenceNumber;

    /**
     * Member requesting the service (denormalized from Visit for queries)
     */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /**
     * Provider who will deliver the service (from JWT security context)
     */
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    @Column(name = "email_request_id")
    private Long emailRequestId;
    
    // ==================== VISIT-CENTRIC ARCHITECTURE ====================

    /**
     * Visit this pre-authorization is linked to
     * ARCHITECTURAL LAW: Pre-authorizations MUST always reference an existing Visit
     * This is NON-NEGOTIABLE - no standalone pre-authorization creation allowed
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id", nullable = true)
    private Visit visit;

    // ==================== CONTRACT-DRIVEN MEDICAL SERVICE ====================

    /**
     * Medical Service (FK to medical_services)
     * ARCHITECTURAL LAW: Service MUST be selected from Provider Contract
     * NO free-text service description allowed
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "medical_service_id", nullable = false)
    private MedicalService medicalService;

    /**
     * Service code (denormalized snapshot for queries/reports)
     * Auto-populated from medicalService.code on save
     */
    @Column(name = "service_code", nullable = false, length = 50)
    private String serviceCode;

    /**
     * Service name (denormalized snapshot for queries/reports)
     */
    @Column(name = "service_name", length = 200)
    private String serviceName;

    /**
     * Service type (legacy column - required by database)
     * Defaults to category name or "MEDICAL"
     */
    @Column(name = "service_type", nullable = false, length = 100)
    @Builder.Default
    private String serviceType = "MEDICAL";

    /**
     * Medical Category ID (MANDATORY - ARCHITECTURAL LAW)
     * 
     * RULE: Coverage resolution requires BOTH category AND service.
     * The same service can have different coverage in different categories.
     * This field MUST be populated from the selected MedicalService.categoryId.
     */
    @Column(name = "service_category_id", nullable = false)
    private Long serviceCategoryId;

    /**
     * Medical Category Name (denormalized snapshot for display)
     */
    @Column(name = "service_category_name", length = 200)
    private String serviceCategoryName;

    /**
     * Date when the service is requested/planned
     */
    @Column(name = "request_date", nullable = false)
    private LocalDate requestDate;

    /**
     * Expected date when the service will be performed
     * Default: same as request_date
     */
    @Column(name = "expected_service_date", nullable = false)
    private LocalDate expectedServiceDate;

    /**
     * Date when the pre-authorization expires
     * Default: request_date + 30 days
     */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    // ==================== CONTRACT-DRIVEN PRICING (READ-ONLY) ====================

    /**
     * Contract price snapshot (from ProviderContract at creation time)
     * ARCHITECTURAL LAW: This is AUTO-RESOLVED from Provider Contract
     * User CANNOT manually enter or override this value
     */
    @Column(name = "contract_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal contractPrice;

    /**
     * Whether service requires pre-authorization (snapshot from MedicalService)
     */
    @Column(name = "requires_pa", nullable = false)
    @Builder.Default
    private Boolean requiresPA = true;

    // ==================== COVERAGE SNAPSHOT (FINANCIAL AUDIT TRAIL)
    // ====================

    /**
     * Coverage percentage at time of pre-auth creation (snapshot from
     * BenefitPolicyRule)
     * IMPORTANT: This is stored as snapshot and should NOT be recalculated after
     * creation
     */
    @Column(name = "coverage_percent_snapshot")
    private Integer coveragePercentSnapshot;

    /**
     * Patient copay percentage at time of pre-auth creation (snapshot from
     * BenefitPolicyRule)
     * IMPORTANT: This is stored as snapshot and should NOT be recalculated after
     * creation
     */
    @Column(name = "patient_copay_percent_snapshot")
    private Integer patientCopayPercentSnapshot;

    /**
     * Approved amount after policy validation
     */
    @Column(name = "approved_amount", precision = 10, scale = 2)
    private BigDecimal approvedAmount;

    /**
     * Member copay amount (based on policy)
     */
    @Column(name = "copay_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal copayAmount = BigDecimal.ZERO;

    /**
     * Copay percentage (from member's policy)
     */
    @Column(name = "copay_percentage", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal copayPercentage = BigDecimal.ZERO;

    /**
     * Amount covered by insurance
     */
    @Column(name = "insurance_covered_amount", precision = 10, scale = 2)
    private BigDecimal insuranceCoveredAmount;

    /**
     * Reserved amount for limit tracking.
     * 
     * ARCHITECTURAL RULE:
     * - This field tracks the amount "reserved" when a PreAuth is APPROVED
     * - It does NOT deduct from the annual limit
     * - Only Claim Approval actually deducts from the limit
     * - Used to warn about potential over-commitment
     * 
     * Workflow:
     * 1. PreAuth APPROVED → reservedAmount = insuranceCoveredAmount
     * 2. Claim created from PreAuth → reservedAmount becomes guidance only
     * 3. Claim APPROVED → actual deduction happens (from Claim.approvedAmount)
     * 4. PreAuth USED/EXPIRED → reservedAmount no longer counts
     */
    @Column(name = "reserved_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal reservedAmount = BigDecimal.ZERO;

    /**
     * Currency code (ISO 4217)
     */
    @Column(name = "currency", length = 3)
    @Builder.Default
    private String currency = "LYD";

    /**
     * PreAuthorization status
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PreAuthStatus status = PreAuthStatus.PENDING;

    /**
     * Priority level
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    @Builder.Default
    private Priority priority = Priority.NORMAL;

    // ==================== DIAGNOSIS (SYSTEM-SELECTED) ====================

    /**
     * Diagnosis ICD-10 code (for future structured diagnosis)
     * Create Diagnosis entity and link as FK when ICD table is available
     */
    @Column(name = "diagnosis_code", nullable = false, length = 20)
    @Builder.Default
    private String diagnosisCode = "Z00.0";

    /**
     * Diagnosis description (snapshot at creation time)
     * For display purposes - derived from diagnosis code
     */
    @Column(name = "diagnosis_description", length = 500)
    private String diagnosisDescription;

    /**
     * Additional notes/comments
     */
    @Column(name = "notes", length = 1000)
    private String notes;

    /**
     * Rejection reason (if status = REJECTED)
     */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /**
     * Soft delete flag
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Version field for optimistic locking
     * Prevents concurrent approval race conditions
     * PRODUCTION HARDENING: Phase 1 - Critical Fix C1
     */
    @Version
    @Column(name = "version")
    private Long version;

    /**
     * Audit fields
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    /**
     * PreAuthorization Status Enum
     * 
     * LIFECYCLE FLOW:
     * PENDING → UNDER_REVIEW → APPROVAL_IN_PROGRESS → APPROVED → ACKNOWLEDGED →
     * USED
     * ↓ ↓
     * EXPIRED CANCELLED
     */
    public enum PreAuthStatus {
        PENDING("معلق"), // Awaiting review
        UNDER_REVIEW("قيد المراجعة"), // Currently being reviewed
        APPROVAL_IN_PROGRESS("جاري معالجة الموافقة"), // Async approval processing
        APPROVED("موافق عليه"), // Approved and valid
        ACKNOWLEDGED("تم الاطلاع"), // Provider acknowledged the approval
        REJECTED("مرفوض"), // Rejected
        NEEDS_CORRECTION("يحتاج تصحيح"), // Provider must fix data and resubmit
        EXPIRED("منتهي"), // Expired without use
        CANCELLED("ملغي"), // Cancelled by member/provider
        USED("مستخدم"); // Already used in a claim

        private final String arabicLabel;

        PreAuthStatus(String arabicLabel) {
            this.arabicLabel = arabicLabel;
        }

        public String getArabicLabel() {
            return arabicLabel;
        }
    }

    /**
     * Priority Enum
     */
    public enum Priority {
        EMERGENCY, // Emergency cases
        URGENT, // Urgent cases (24-48h)
        NORMAL, // Normal priority
        LOW // Low priority
    }

    // ==================== Business Logic Methods ====================

    /**
     * Populate denormalized fields from related entities
     * Called before persist/update to ensure data consistency
     */
    @PrePersist
    private void prePersist() {
        // Generate reference number if not set
        if (referenceNumber == null || referenceNumber.isBlank()) {
            referenceNumber = generateReferenceNumber();
        }

        // Populate denormalized fields from MedicalService
        if (medicalService != null) {
            this.serviceCode = medicalService.getCode();
            this.serviceCategoryId = medicalService.getCategoryId();
            // NOTE: requiresPA is no longer taken from MedicalService
            // PA requirement comes from BenefitPolicyRule.requiresPreApproval
            this.requiresPA = true; // PreAuthorizations always require PA (that's why they exist)
        }

        // Populate memberId from Visit
        if (visit != null && memberId == null) {
            this.memberId = visit.getMember().getId();
        }

        // Set default request date
        if (requestDate == null) {
            requestDate = LocalDate.now();
        }

        // Set default expiry date (30 days from request)
        if (expiryDate == null && requestDate != null) {
            expiryDate = requestDate.plusDays(30);
        }

        validateArchitecturalRules();
        validateAmounts();
    }

    /**
     * Validate architectural rules before save
     * THROWS IllegalStateException if any rule is violated
     */
    private void validateArchitecturalRules() {
        // RULE: Visit is MANDATORY (unless it's an email request)
        if (visit == null && emailRequestId == null) {
            throw new IllegalStateException("ARCHITECTURAL VIOLATION: PreAuthorization MUST reference a Visit or an Email Request");
        }

        // RULE: MedicalService is MANDATORY (no free-text services)
        if (medicalService == null) {
            throw new IllegalStateException(
                    "ARCHITECTURAL VIOLATION: PreAuthorization MUST reference a MedicalService from Provider Contract");
        }

        // RULE: Contract price is MANDATORY (no manual pricing)
        if (contractPrice == null || contractPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(
                    "ARCHITECTURAL VIOLATION: Contract price must be resolved from Provider Contract");
        }

        // RULE: Provider ID is MANDATORY
        if (providerId == null) {
            throw new IllegalStateException("Provider ID is required");
        }
    }

    /**
     * Check if pre-authorization is currently valid
     */
    public boolean isValid() {
        return active &&
                status == PreAuthStatus.APPROVED &&
                (expiryDate == null || !LocalDate.now().isAfter(expiryDate));
    }

    /**
     * Check if pre-authorization allows editing.
     * Only PENDING and NEEDS_CORRECTION statuses allow data edits.
     * 
     * @return true if status allows editing
     * @since Provider Portal Security Fix (Phase 0)
     */
    public boolean allowsEdit() {
        return status == PreAuthStatus.PENDING || status == PreAuthStatus.NEEDS_CORRECTION;
    }

    /**
     * Check if pre-authorization is expired
     */
    public boolean isExpired() {
        return expiryDate != null && LocalDate.now().isAfter(expiryDate);
    }

    /**
     * Check if can be approved
     * Allows approval from PENDING, UNDER_REVIEW, or APPROVAL_IN_PROGRESS status
     */
    public boolean canBeApproved() {
        return active && (status == PreAuthStatus.PENDING ||
                status == PreAuthStatus.UNDER_REVIEW ||
                status == PreAuthStatus.APPROVAL_IN_PROGRESS);
    }

    /**
     * Check if can be rejected
     * Allows rejection from PENDING, UNDER_REVIEW, or APPROVAL_IN_PROGRESS status
     */
    public boolean canBeRejected() {
        return active && (status == PreAuthStatus.PENDING ||
                status == PreAuthStatus.UNDER_REVIEW ||
                status == PreAuthStatus.APPROVAL_IN_PROGRESS);
    }

    /**
     * Check if can be cancelled
     */
    public boolean canBeCancelled() {
        return active && (status == PreAuthStatus.PENDING || status == PreAuthStatus.APPROVED);
    }

    /**
     * Approve the pre-authorization
     * 
     * ARCHITECTURAL RULE:
     * - Sets reservedAmount = insuranceCoveredAmount for limit tracking
     * - This does NOT deduct from the annual limit
     * - Only Claim Approval actually deducts from the limit
     */
    public void approve(BigDecimal approvedAmount, BigDecimal copayAmount, String approvedBy) {
        if (!canBeApproved()) {
            throw new IllegalStateException("PreAuthorization cannot be approved in current status: " + status);
        }
        this.status = PreAuthStatus.APPROVED;
        this.approvedAmount = approvedAmount;
        this.copayAmount = copayAmount;
        this.insuranceCoveredAmount = approvedAmount.subtract(copayAmount);

        // ARCHITECTURAL: Set reserved amount for limit tracking (not deduction)
        this.reservedAmount = this.insuranceCoveredAmount;

        this.approvedAt = LocalDateTime.now();
        this.approvedBy = approvedBy;
    }

    /**
     * Reject the pre-authorization
     */
    public void reject(String rejectionReason, String rejectedBy) {
        if (!canBeRejected()) {
            throw new IllegalStateException("PreAuthorization cannot be rejected in current status: " + status);
        }
        this.status = PreAuthStatus.REJECTED;
        this.rejectionReason = rejectionReason;
        this.reservedAmount = BigDecimal.ZERO; // Clear any reserved amount
        this.updatedBy = rejectedBy;
    }

    /**
     * Cancel the pre-authorization
     */
    public void cancel(String cancelReason, String cancelledBy) {
        if (!canBeCancelled()) {
            throw new IllegalStateException("PreAuthorization cannot be cancelled in current status: " + status);
        }
        this.status = PreAuthStatus.CANCELLED;
        this.reservedAmount = BigDecimal.ZERO; // Clear any reserved amount
        this.notes = (notes != null ? notes + "\n" : "") + "Cancelled: " + cancelReason;
        this.updatedBy = cancelledBy;
    }

    /**
     * Mark as used (when claim is submitted)
     */
    public void markAsUsed(String usedBy) {
        if (status != PreAuthStatus.APPROVED) {
            throw new IllegalStateException("Only approved pre-authorizations can be marked as used");
        }
        this.status = PreAuthStatus.USED;
        this.updatedBy = usedBy;
    }

    /**
     * Mark as expired
     */
    public void markAsExpired() {
        if (isExpired() && status == PreAuthStatus.APPROVED) {
            this.status = PreAuthStatus.EXPIRED;
        }
    }

    /**
     * Calculate copay amount based on percentage
     */
    public BigDecimal calculateCopay(BigDecimal amount, BigDecimal copayPercentage) {
        if (copayPercentage == null || copayPercentage.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(copayPercentage).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Validate amounts - called from prePersist and preUpdate
     */
    @PreUpdate
    private void validateAmounts() {
        if (contractPrice != null && contractPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Contract price cannot be negative");
        }
        if (approvedAmount != null && approvedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Approved amount cannot be negative");
        }
        if (copayAmount != null && copayAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Copay amount cannot be negative");
        }
    }

    /**
     * Generate reference number
     * Format: PA-YYYYMMDD-XXXXX
     */
    public static String generateReferenceNumber() {
        String date = LocalDate.now().toString().replace("-", "");
        String random = String.format("%05d", (int) (Math.random() * 100000));
        return "PA-" + date + "-" + random;
    }

    @Override
    public String toString() {
        return "PreAuthorization{" +
                "id=" + id +
                ", referenceNumber='" + referenceNumber + '\'' +
                ", memberId=" + memberId +
                ", providerId=" + providerId +
                ", serviceCode='" + serviceCode + '\'' +
                ", status=" + status +
                ", approvedAmount=" + approvedAmount +
                '}';
    }
}
