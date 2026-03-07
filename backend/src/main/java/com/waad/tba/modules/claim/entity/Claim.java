package com.waad.tba.modules.claim.entity;

import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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

    // ==================== CLAIM DETAILS ====================

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
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

    /**
     * Service/Visit date
     */
    @Column(name = "service_date")
    private LocalDate serviceDate;

    // ==================== CALCULATED AMOUNTS (CONTRACT-DRIVEN) ====================
    
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

    @Column(name = "difference_amount", precision = 15, scale = 2)
    private BigDecimal differenceAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    @Builder.Default
    private ClaimStatus status = ClaimStatus.DRAFT;

    @Column(name = "reviewer_comment", columnDefinition = "TEXT")
    private String reviewerComment;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

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

    // ========== Provider Account Settlement (Phase: Settlement Refactor) ==========
    
    /**
     * Settlement batch reference (NEW Provider Account Settlement model)
     * 
     * Links claim to a settlement batch when status = BATCHED.
     * Set when claim is added to a batch, cleared if removed from batch.
     * 
     * Workflow:
     * - APPROVED → addToBatch() → BATCHED (settlementBatchId set)
     * - BATCHED → removeFromBatch() → APPROVED (settlementBatchId cleared)
     * - BATCHED → batchPaid() → SETTLED (settlementBatchId retained for audit)
     * 
     * @since Provider Account Settlement Phase 1
     */
    @Column(name = "settlement_batch_id")
    private Long settlementBatchId;

    // ========== SLA Tracking Fields (Phase 1: SLA Implementation) ==========
    
    /**
     * Expected completion date (calculated from submission date + SLA business days).
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
        calculateFields();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        validateBusinessRules();
        calculateFields();
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
        
        if (anyLineRequiresPA && preAuthorization == null) {
            throw new IllegalStateException(
                "ARCHITECTURAL VIOLATION: Claim contains services requiring pre-authorization. " +
                "PreAuthorization ID is REQUIRED.");
        }
    }

    private void validateBusinessRules() {
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Requested amount must be greater than zero");
        }

        if (approvedAmount != null && approvedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Approved amount cannot be negative");
        }

        if (status == ClaimStatus.APPROVED || status == ClaimStatus.SETTLED) {
            if (approvedAmount == null || approvedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("Approved/Settled status requires approved amount greater than zero");
            }
        }

        // Note: Partial approval is now just APPROVED with approvedAmount < requestedAmount
        // The difference is tracked via differenceAmount field

        if (status == ClaimStatus.REJECTED) {
            if (reviewerComment == null || reviewerComment.trim().isEmpty()) {
                throw new IllegalStateException("Rejected status requires reviewer comment");
            }
        }

        // Auto-set reviewedAt when status changes from draft states
        if (status != null && status.requiresReviewerAction() && reviewedAt == null) {
            reviewedAt = LocalDateTime.now();
        }
    }

    private void calculateFields() {
        // Calculate requested amount from lines (SUM of all line totals)
        if (lines != null && !lines.isEmpty()) {
            requestedAmount = lines.stream()
                    .map(ClaimLine::getTotalPrice)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            // Calculate refused amount from rejected lines (Phase: Partial Rejection)
            refusedAmount = lines.stream()
                    .filter(line -> Boolean.TRUE.equals(line.getRejected()))
                    .map(ClaimLine::getTotalPrice)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            requestedAmount = requestedAmount != null ? requestedAmount : BigDecimal.ZERO;
            refusedAmount = (status == ClaimStatus.REJECTED) ? requestedAmount : BigDecimal.ZERO;
        }
        
        // If entire claim is REJECTED, refusedAmount = requestedAmount
        if (status == ClaimStatus.REJECTED) {
            refusedAmount = requestedAmount;
            approvedAmount = BigDecimal.ZERO;
            patientCoPay = BigDecimal.ZERO;
        }
        
        // Calculate difference amount (Requested - Approved)
        if (requestedAmount != null && approvedAmount != null) {
            differenceAmount = requestedAmount.subtract(approvedAmount);
        } else {
            differenceAmount = null;
        }
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

    // ========== Provider Account Settlement Helper Methods ==========
    
    /**
     * Check if claim is currently in a settlement batch
     */
    public boolean isInBatch() {
        return settlementBatchId != null && status == ClaimStatus.BATCHED;
    }
    
    /**
     * Check if claim can be added to a batch
     * Only APPROVED claims can be batched
     */
    public boolean canBeAddedToBatch() {
        return status == ClaimStatus.APPROVED && settlementBatchId == null;
    }
    
    /**
     * Add claim to a batch (state transition helper)
     */
    public void addToBatch(Long batchId) {
        if (!canBeAddedToBatch()) {
            throw new IllegalStateException(
                "Cannot add claim to batch. Status must be APPROVED and not already in a batch. " +
                "Current status: " + status + ", batchId: " + settlementBatchId);
        }
        this.settlementBatchId = batchId;
        this.status = ClaimStatus.BATCHED;
    }
    
    /**
     * Remove claim from batch (state transition helper)
     */
    public void removeFromBatch() {
        if (status != ClaimStatus.BATCHED) {
            throw new IllegalStateException(
                "Cannot remove claim from batch. Status must be BATCHED. Current status: " + status);
        }
        this.settlementBatchId = null;
        this.status = ClaimStatus.APPROVED;
    }
    
    /**
     * Get the net amount payable to provider
     * Used when adding to batch for amount snapshot
     */
    public BigDecimal getNetPayableAmount() {
        return netProviderAmount != null ? netProviderAmount : 
               (approvedAmount != null ? approvedAmount : BigDecimal.ZERO);
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

    /**
     * Legacy backlog check (Always false for unified workflow)
     */
    @Transient
    public Boolean getIsBacklog() {
        return false;
    }
}
