package com.waad.tba.modules.claim.entity;

import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "claims")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic Locking Version (PHASE 1: Race Condition Protection)
     * 
     * Prevents concurrent modifications to the same claim.
     * If two transactions try to update the same claim simultaneously,
     * one will fail with OptimisticLockException.
     * 
     * Critical for financial integrity:
     * - Prevents double deduction from member's balance
     * - Ensures claim approval amounts are consistent
     * - Protects against concurrent financial calculations
     * 
     * @since Phase 1 - Financial Lifecycle Completion
     */
    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    // REMOVED: insuranceOrganization field
    // Domain Architecture Decision (2026-02-13): No insurance organization concept
    // Employer is the ONLY business entity

    // REMOVED: InsurancePolicy and PolicyBenefitPackage
    // Coverage is now determined via Member.benefitPolicy (BenefitPolicy module)
    // Legacy columns kept in DB for data migration but not mapped

    /**
     * ARCHITECTURAL DECISION (2026-01-15):
     * - Claim links to PreAuthorization (modules/preauthorization)
     * - modules/preauth is DEPRECATED and should not be used
     * - PreAuthorization is the canonical source for pre-approvals
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pre_authorization_id")
    private PreAuthorization preAuthorization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_batch_id")
    private ClaimBatch claimBatch;

    // ==================== UNIFIED WORKFLOW ====================

    /**
     * Related visit (unified workflow)
     * ARCHITECTURAL DECISION (2026-01-15): Required - Visit-Centric Architecture
     * Claims MUST always reference an existing Visit.
     * No standalone claim creation allowed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_id", nullable = false)
    private com.waad.tba.modules.visit.entity.Visit visit;

    /**
     * رقم المطالبة الفريد للعرض (CLM-{id}).
     * يُولَّد في الخدمة بعد الحفظ الأول ثم يُحدَّث فوراً.
     */
    @Column(name = "claim_number", length = 100, unique = true)
    private String claimNumber;

    // ==================== CLAIM DETAILS ====================

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = false)
    @SQLRestriction("current_line = true")
    @Builder.Default
    private List<ClaimLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ClaimAttachment> attachments = new ArrayList<>();

    // ==================== PROVIDER INFORMATION ====================

    /**
     * Provider ID - Links claim to the healthcare provider
     * AUTO-FILLED from JWT security context
     */
    @Column(name = "provider_id", nullable = false)
    private Long providerId;

    /**
     * Provider name (denormalized snapshot)
     */
    @Column(name = "provider_name", length = 255)
    private String providerName;

    @Column(name = "doctor_name", length = 255)
    private String doctorName;

    // ==================== DIAGNOSIS (SYSTEM-SELECTED) ====================

    /**
     * Diagnosis ICD-10 code (selected, not free-text)
     */
    @Column(name = "diagnosis_code", length = 20)
    private String diagnosisCode;

    /**
     * Diagnosis description (snapshot at claim time)
     */
    @Column(name = "diagnosis_description", length = 500)
    private String diagnosisDescription;

    @Column(name = "complaint", length = 1000)
    private String complaint;

    /**
     * Service/Visit date
     */
    @Column(name = "service_date")
    private LocalDate serviceDate;

    // ==================== CALCULATED AMOUNTS (CONTRACT-DRIVEN)
    // ====================

    /**
     * Total requested amount (SUM of all claim lines total_price)
     * SERVER-CALCULATED from lines
     */
    @Column(name = "requested_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal requestedAmount;

    @Column(name = "approved_amount", precision = 15, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "refused_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal refusedAmount = BigDecimal.ZERO;

    // B-07 OOP FIX: differenceAmount is mathematically derived.
    // Storing it physically creates a risk of silent sync drift (database anomaly).
    // Now purely calculated at runtime.
    @Transient
    private BigDecimal differenceAmount;

    public BigDecimal getDifferenceAmount() {
        BigDecimal req = this.requestedAmount != null ? this.requestedAmount : BigDecimal.ZERO;
        BigDecimal net = this.netProviderAmount != null ? this.netProviderAmount
                : (this.approvedAmount != null ? this.approvedAmount : BigDecimal.ZERO);
        return req.subtract(net);
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    @Builder.Default
    private ClaimStatus status = ClaimStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_source", length = 30, nullable = false)
    @Builder.Default
    private ClaimSubmissionSource submissionSource = ClaimSubmissionSource.INTERNAL_DIRECT;

    @Column(name = "reviewer_comment", columnDefinition = "TEXT")
    private String reviewerComment;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by_id")
    private Long reviewedById;

    @Column(name = "reviewed_by", length = 255)
    private String reviewedBy;

    @Column(name = "review_paused", nullable = false)
    @Builder.Default
    private Boolean reviewPaused = false;

    @Column(name = "review_pause_reason", length = 1000)
    private String reviewPauseReason;

    @Column(name = "review_paused_at")
    private LocalDateTime reviewPausedAt;

    @Column(name = "review_paused_by", length = 150)
    private String reviewPausedBy;

    // ==================== COVERAGE CONTEXT (PHASE: DYNAMIC BENEFITS)
    // ====================

    /**
     * Care setting for the whole encounter. Changing it selects a different
     * policy rule; it never changes a line's medical category.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "encounter_type", length = 20, nullable = false)
    @Builder.Default
    private EncounterType encounterType = EncounterType.OUTPATIENT;

    /**
     * Full coverage override: 100% coverage, no limits.
     * When TRUE, bypasses all service-specific limits (amount/times).
     */
    @Column(name = "full_coverage")
    @Builder.Default
    private Boolean fullCoverage = false;

    /**
     * Indicates claim financial/coverage view is stale and requires recalculation.
     * Used by strict state-machine guards before approval.
     */
    @Column(name = "pending_recalculation", nullable = false)
    @Builder.Default
    private Boolean pendingRecalculation = false;

    /**
     * Monotonic version for coverage context evolution on claim edits.
     * Incremented whenever lines/category context materially change.
     */
    @Column(name = "coverage_version", nullable = false)
    @Builder.Default
    private Integer coverageVersion = 1;

    // ========== Financial Snapshot Fields (Phase MVP) ==========

    /**
     * نسبة تحمل المريض (Co-Pay + Deductible)
     */
    @Column(name = "patient_copay", precision = 15, scale = 2)
    private BigDecimal patientCoPay;

    /**
     * المبلغ الصافي المستحق لمقدم الخدمة
     */
    @Column(name = "net_provider_amount", precision = 15, scale = 2)
    private BigDecimal netProviderAmount;

    /**
     * نسبة المشاركة المُطبقة (%)
     */
    @Column(name = "copay_percent", precision = 5, scale = 2)
    private BigDecimal coPayPercent;

    /**
     * الخصم المُطبق (Deductible)
     */
    @Column(name = "deductible_applied", precision = 15, scale = 2)
    private BigDecimal deductibleApplied;

    /**
     * نسبة خصم العقد المُطبَّقة فعلياً عند اعتماد المطالبة.
     * تُسجَّل مرة واحدة في creditOnClaimApproval ولا تتغير بعد ذلك،
     * بحيث يبقى التدقيق المالي دقيقاً حتى لو تغيّر العقد لاحقاً.
     */
    @Column(name = "applied_discount_percent", precision = 5, scale = 2)
    private BigDecimal appliedDiscountPercent;

    /** Exact provider contract selected by serviceDate and scope. */
    @Column(name = "provider_contract_id")
    private Long providerContractId;

    /** Exact immutable-in-time contract terms selected for this calculation. */
    @Column(name = "contract_terms_id")
    private Long contractTermsId;

    @Column(name = "financial_calculated_at")
    private LocalDateTime financialCalculatedAt;

    /**
     * القيمة المستحقة للشركة (قيمة الخصم التعاقدي).
     * تُحسب آلياً بناءً على appliedDiscountPercent ولا يدخلها المستخدم.
     */
    @Column(name = "company_discount_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal companyDiscountAmount = BigDecimal.ZERO;

    /**
     * لقطة لإعداد توقيت الخصم من العقد عند اعتماد المطالبة.
     * true = خصم نسبة التخفيض قبل خصم المرفوض, false = بعده.
     */
    @Column(name = "discount_before_rejection")
    @Builder.Default
    private Boolean discountBeforeRejection = true;

    // ========== Settlement Fields (Phase MVP) ==========

    /**
     * رقم مرجع الدفع
     */
    @Column(name = "payment_reference", length = 100)
    private String paymentReference;

    /**
     * تاريخ التسوية
     */
    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    /**
     * ملاحظات التسوية
     */
    @Column(name = "settlement_notes", columnDefinition = "TEXT")
    private String settlementNotes;

    // ========== Provider Account Settlement (Phase: Settlement Refactor)
    // ==========

    /**
     * المبلغ المدفوع فعلياً لمقدم الخدمة عند التسوية (paid_amount).
     * يُسجَّل لحظة إتمام التسوية، ويُستخدم في التقارير المالية.
     */
    @Column(name = "paid_amount", precision = 15, scale = 2)
    private BigDecimal paidAmount;

    // ========== SLA Tracking Fields (Phase 1: SLA Implementation) ==========

    /**
     * Expected completion date (calculated from submission date + SLA business
     * days).
     * Automatically set when claim status changes to SUBMITTED.
     * Uses configurable system setting CLAIM_SLA_DAYS (default: 10 business days).
     * 
     * Example:
     * - Submission Date: 2026-01-12 (Sunday)
     * - SLA Days: 10 business days
     * - Expected Completion: 2026-01-28 (Wednesday)
     * 
     * @since Phase 1 - SLA Implementation
     */
    @Column(name = "expected_completion_date")
    private LocalDate expectedCompletionDate;

    /**
     * Actual completion date (when claim is approved or rejected).
     * Set automatically when status changes to APPROVED or REJECTED.
     * 
     * @since Phase 1 - SLA Implementation
     */
    @Column(name = "actual_completion_date")
    private LocalDate actualCompletionDate;

    /**
     * Whether the claim was completed within SLA.
     * 
     * Calculation:
     * - businessDaysTaken <= SLA Days → true (within SLA)
     * - businessDaysTaken > SLA Days → false (exceeded SLA)
     * 
     * Set automatically when claim is approved/rejected.
     * 
     * @since Phase 1 - SLA Implementation
     */
    @Column(name = "within_sla")
    private Boolean withinSla;

    /**
     * Number of business days taken to process the claim.
     * 
     * Calculated from submission date to actual completion date,
     * excluding weekend (Friday) and public holidays.
     * 
     * Example:
     * - Submission: 2026-01-12
     * - Approval: 2026-01-27
     * - Business Days Taken: 9 days
     * 
     * @since Phase 1 - SLA Implementation
     */
    @Column(name = "business_days_taken")
    private Integer businessDaysTaken;

    /**
     * SLA days configured at the time of submission.
     * Stores the SLA value used for this specific claim.
     * 
     * This allows changing the system-wide SLA setting without affecting
     * existing claims that were submitted under different SLA values.
     * 
     * Example:
     * - System SLA = 10 days (at submission time)
     * - This field stores: 10
     * - Later, admin changes system SLA to 7 days
     * - This claim still uses: 10 days (original SLA)
     * 
     * @since Phase 1 - SLA Implementation
     */
    @Column(name = "sla_days_configured")
    private Integer slaDaysConfigured;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    /** تاريخ الحذف الناعم */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** من قام بالحذف */
    @Column(name = "deleted_by", length = 255)
    private String deletedBy;

    /** سبب الإلغاء */
    @Column(name = "void_reason", columnDefinition = "TEXT")
    private String voidReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        validateArchitecturalRules();
        validateBusinessRules();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        validateBusinessRules();
    }

    /**
     * Validate architectural rules (CANONICAL REBUILD 2026-01-16)
     */
    private void validateArchitecturalRules() {
        // RULE: Visit is MANDATORY
        if (visit == null) {
            throw new IllegalStateException("ARCHITECTURAL VIOLATION: Claim MUST reference a Visit");
        }

        // RULE: Provider ID is MANDATORY
        if (providerId == null) {
            throw new IllegalStateException("Provider ID is required");
        }

        // RULE: At least one claim line is required
        if (lines == null || lines.isEmpty()) {
            throw new IllegalStateException("ARCHITECTURAL VIOLATION: Claim MUST have at least one service line");
        }

        // RULE: Check if any line requires PA and validate preAuthorization
        boolean anyLineRequiresPA = lines.stream()
                .anyMatch(line -> Boolean.TRUE.equals(line.getRequiresPA()));

        if (anyLineRequiresPA && preAuthorization == null && !Boolean.TRUE.equals(isBacklog)) {
            throw new IllegalStateException(
                    "يرجى إدخال رقم الموافقة المسبقة. المطالبة تحتوي على خدمات تتطلب موافقة مسبقة ولم يتم العثور على موافقة مرتبطة.");
        }
    }

    private void validateBusinessRules() {
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Requested amount must be greater than zero");
        }

        if (approvedAmount != null && approvedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Approved amount cannot be negative");
        }

        // In direct-entry mode (APPROVED status), approvedAmount is calculated
        // automatically
        // and can be 0 if patient covers 100% copay. Only require it to be non-null.
        if (status == ClaimStatus.APPROVED) {
            if (approvedAmount == null) {
                throw new IllegalStateException("Approved status requires approved amount to be set");
            }
        }

        // SETTLED requires non-negative approved amount
        if (status == ClaimStatus.SETTLED) {
            if (approvedAmount == null || approvedAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalStateException("Settled status requires non-negative approved amount");
            }
        }

        // Note: Partial approval is now just APPROVED with approvedAmount <
        // requestedAmount
        // The difference is tracked via differenceAmount field

        if (status == ClaimStatus.REJECTED) {
            if (reviewerComment == null || reviewerComment.trim().isEmpty()) {
                throw new IllegalStateException("Rejected status requires reviewer comment");
            }
        }

        // NEEDS_CORRECTION is deliberately non-financial: the prior cycle has
        // already been reversed and claim-level payable totals are cleared until
        // a new calculation is approved. Every final/payable state remains
        // fail-closed through the identity below.
        if (status != ClaimStatus.NEEDS_CORRECTION) {
            validateFinancialIdentity();
        }

        // Auto-set reviewedAt when status changes from draft states
        if (status != null && status.requiresReviewerAction() && reviewedAt == null) {
            reviewedAt = LocalDateTime.now();
        }
    }

    private void validateFinancialIdentity() {
        BigDecimal gross = scale2(requestedAmount);
        BigDecimal patient = scale2(patientCoPay);
        BigDecimal rejected = scale2(refusedAmount);
        BigDecimal payable = scale2(getNetPayableAmount());
        BigDecimal discount = scale2(companyDiscountAmount);
        if (patient.signum() < 0 || rejected.signum() < 0 || payable.signum() < 0 || discount.signum() < 0) {
            throw new IllegalStateException("Financial snapshot contains a negative component");
        }
        BigDecimal components = scale2(patient.add(rejected).add(discount).add(payable));
        if (abs(scale2(gross.subtract(components))).compareTo(new BigDecimal("0.05")) > 0) {
            throw new IllegalStateException(
                    "Financial snapshot mismatch: gross=" + gross + " components=" + components
                            + " [patient=" + patient + ", refused=" + rejected
                            + ", discount=" + discount + ", payable=" + payable + "]");
        }
    }


    private BigDecimal scale2(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal abs(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.abs();
    }

    // Helper methods for bidirectional relationships
    public void addLine(ClaimLine line) {
        lines.add(line);
        line.setClaim(this);
    }

    public void removeLine(ClaimLine line) {
        lines.remove(line);
        line.setClaim(null);
    }

    public void addAttachment(ClaimAttachment attachment) {
        attachments.add(attachment);
        attachment.setClaim(this);
    }

    public void removeAttachment(ClaimAttachment attachment) {
        attachments.remove(attachment);
        attachment.setClaim(null);
    }

    /**
     * Get the net amount payable to provider
     */
    public BigDecimal getNetPayableAmount() {
        return netProviderAmount != null ? netProviderAmount
                : (approvedAmount != null ? approvedAmount : BigDecimal.ZERO);
    }

    /**
     * Mark claim coverage snapshot as stale after any editable data mutation.
     */
    public void markCoverageDirty() {
        this.pendingRecalculation = true;
        this.coverageVersion = (this.coverageVersion == null ? 1 : this.coverageVersion + 1);
    }

    /**
     * Mark claim coverage snapshot as synchronized after successful recalculation.
     */
    public void markCoverageSynced() {
        this.pendingRecalculation = false;
        if (this.coverageVersion == null || this.coverageVersion < 1) {
            this.coverageVersion = 1;
        }
    }

    /**
     * Get number of service lines (Transient)
     */
    @Transient
    public Integer getServiceCount() {
        return lines != null ? lines.size() : 0;
    }

    /**
     * Get number of attachments (Transient)
     */
    @Transient
    public Integer getAttachmentsCount() {
        return attachments != null ? attachments.size() : 0;
    }

    @Column(name = "is_backlog")
    @Builder.Default
    private Boolean isBacklog = false;
}
