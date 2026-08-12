package com.waad.tba.modules.eligibility.domain;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Eligibility Result
 * Phase E1 - Eligibility Engine
 * 
 * The final decision output from the eligibility engine.
 * Contains the eligibility status, reasons, and a snapshot of the data at check time.
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Data
@Builder(toBuilder = true)
public class EligibilityResult {

    // ============================================
    // Decision
    // ============================================

    /**
     * Simple boolean: is the member eligible?
     */
    private final boolean eligible;

    /**
     * Detailed status
     */
    private final EligibilityStatus status;

    /**
     * List of reasons (for failures/warnings)
     */
    @Builder.Default
    private final List<ReasonDetail> reasons = new ArrayList<>();

    // ============================================
    // Request Info
    // ============================================

    /**
     * Request ID for tracking
     */
    private final String requestId;

    /**
     * When the check was performed
     */
    private final LocalDateTime checkTimestamp;

    /**
     * Processing time in milliseconds
     */
    private final long processingTimeMs;

    /**
     * Number of rules evaluated
     */
    private final int rulesEvaluated;

    // ============================================
    // Snapshot (denormalized for audit)
    // ============================================

    /**
     * Snapshot of data at the time of the check
     */
    private final EligibilitySnapshot snapshot;

    /**
     * Whether the audit trail record for this decision was actually persisted.
     * The eligibility decision itself is never lost or altered because the
     * audit write failed (e.g. a transient DB issue) -- this flag lets callers
     * that need a guaranteed audit trail (or monitoring) detect that gap
     * instead of assuming every decision is traceable. See
     * EligibilityAuditRecorder, which persists in its own REQUIRES_NEW
     * transaction specifically so an audit-write failure can never roll back
     * or hide the eligibility result it's describing.
     */
    @Builder.Default
    private final boolean auditRecorded = true;

    // ============================================
    // Nested Classes
    // ============================================

    /**
     * Eligibility Status Enum
     */
    public enum EligibilityStatus {
        ELIGIBLE,
        NOT_ELIGIBLE,
        WARNING
    }

    /**
     * Reason Detail for response
     */
    @Data
    @Builder
    public static class ReasonDetail {
        private final String code;
        private final String messageAr;
        private final String messageEn;
        private final String details;
        private final boolean hardFailure;

        public static ReasonDetail from(EligibilityReason reason, String details) {
            return ReasonDetail.builder()
                    .code(reason.getCode())
                    .messageAr(reason.getMessageAr())
                    .messageEn(reason.getMessageEn())
                    .details(details)
                    .hardFailure(reason.isHardFailure())
                    .build();
        }

        public static ReasonDetail from(RuleResult result) {
            if (result.getReason() == null) return null;
            return ReasonDetail.builder()
                    .code(result.getReason().getCode())
                    .messageAr(result.getMessage())
                    .messageEn(result.getReason().getMessageEn())
                    .details(result.getDetails())
                    .hardFailure(result.getReason().isHardFailure())
                    .build();
        }
    }

    /**
     * Snapshot of data at check time
     */
    @Data
    @Builder
    public static class EligibilitySnapshot {
        // Member Info
        private final Long memberId;
        private final String memberName;
        private final String memberCivilId;
        private final String memberStatus;
        private final String memberCardNumber;

        // Policy Info
        private final Long policyId;
        private final String policyNumber;
        private final String policyStatus;
        private final LocalDate coverageStart;
        private final LocalDate coverageEnd;
        private final String productName;

        // Employer Info
        private final Long employerId;
        private final String employerName;

        // Provider Info (if provided)
        private final Long providerId;
        private final String providerName;

        // Service Info
        private final LocalDate serviceDate;
        private final String serviceCode;
        
        // CANONICAL: Category/Service specific coverage info
        private final Long medicalCategoryId;
        private final String medicalCategoryName;
        private final Long medicalServiceId;
        private final String medicalServiceName;
        private final Integer coveragePercent;      // From BenefitPolicyRule
        private final Integer patientCopayPercent;  // From BenefitPolicyRule  
        private final Boolean requiresPreApproval;  // From BenefitPolicyRule

        // Financial Usage Data
        private final Double benefitLimit;
        private final Double usedAmount;
        private final Double remainingAmount;
        private final Long matchingCategoryId;
    }

    // ============================================
    // Factory Methods
    // ============================================

    /**
     * Create an eligible result
     */
    public static EligibilityResult eligible(String requestId, EligibilitySnapshot snapshot,
                                             long processingTimeMs, int rulesEvaluated) {
        return EligibilityResult.builder()
                .eligible(true)
                .status(EligibilityStatus.ELIGIBLE)
                .requestId(requestId)
                .checkTimestamp(LocalDateTime.now())
                .processingTimeMs(processingTimeMs)
                .rulesEvaluated(rulesEvaluated)
                .snapshot(snapshot)
                .build();
    }

    /**
     * Create an eligible with warnings result
     */
    public static EligibilityResult eligibleWithWarnings(String requestId, EligibilitySnapshot snapshot,
                                                          List<ReasonDetail> warnings,
                                                          long processingTimeMs, int rulesEvaluated) {
        return EligibilityResult.builder()
                .eligible(true)
                .status(EligibilityStatus.WARNING)
                .reasons(warnings)
                .requestId(requestId)
                .checkTimestamp(LocalDateTime.now())
                .processingTimeMs(processingTimeMs)
                .rulesEvaluated(rulesEvaluated)
                .snapshot(snapshot)
                .build();
    }

    /**
     * Create a not eligible result
     */
    public static EligibilityResult notEligible(String requestId, EligibilitySnapshot snapshot,
                                                 List<ReasonDetail> reasons,
                                                 long processingTimeMs, int rulesEvaluated) {
        return EligibilityResult.builder()
                .eligible(false)
                .status(EligibilityStatus.NOT_ELIGIBLE)
                .reasons(reasons != null ? reasons : new ArrayList<>())
                .requestId(requestId)
                .checkTimestamp(LocalDateTime.now())
                .processingTimeMs(processingTimeMs)
                .rulesEvaluated(rulesEvaluated)
                .snapshot(snapshot)
                .build();
    }

    // ============================================
    // Helper Methods
    // ============================================

    /**
     * Check if there are any warnings
     */
    public boolean hasWarnings() {
        return reasons != null && reasons.stream().anyMatch(r -> !r.isHardFailure());
    }

    /**
     * Get only hard failure reasons
     */
    public List<ReasonDetail> getHardFailures() {
        if (reasons == null) return new ArrayList<>();
        return reasons.stream().filter(ReasonDetail::isHardFailure).toList();
    }

    /**
     * Get only warning reasons
     */
    public List<ReasonDetail> getWarnings() {
        if (reasons == null) return new ArrayList<>();
        return reasons.stream().filter(r -> !r.isHardFailure()).toList();
    }
}
