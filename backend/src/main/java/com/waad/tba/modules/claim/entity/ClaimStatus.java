package com.waad.tba.modules.claim.entity;

import java.util.Collections;
import java.util.Set;

/**
 * Claim lifecycle status enum with strict transition rules.
 * 
 * LIFECYCLE FLOW:
 * ┌────────┐
 * │ DRAFT  │ ─── Initial state for newly created claims
 * └────┬───┘
 *      │ submit()
 *      ▼
 * ┌────────────┐
 * │ SUBMITTED  │ ─── Claim submitted for review
 * └─────┬──────┘
 *       │ startReview()
 *       ▼
 * ┌──────────────┐       ┌────────────────────┐
 * │ UNDER_REVIEW │──────▶│ NEEDS_CORRECTION   │ (provider must fix)
 * └──────┬───────┘       └─────────┬──────────┘
 *        │                         │ resubmit()
 *        │ ◄───────────────────────┘
 *        │
 *   ┌────┴────┐
 *   ▼         ▼
 * ┌──────────┐  ┌──────────┐
 * │ APPROVED │  │ REJECTED │ ─── Terminal (requires comment)
 * └────┬─────┘  └──────────┘
 *      │
 *      ├─────────────────────────┐
 *      │ addToBatch()            │ settle() (legacy direct)
 *      ▼                         │
 * ┌──────────┐                   │
 * │ BATCHED  │ ── In settlement  │
 * └────┬─────┘    batch          │
 *      │                         │
 *      │ batchPaid()             │
 *      ▼                         ▼
 * ┌──────────────────────────────┐
 * │         SETTLED              │ ─── Payment completed (Terminal)
 * └──────────────────────────────┘
 * 
 * LEGACY MAPPING:
 * - PENDING_REVIEW → now SUBMITTED or UNDER_REVIEW
 * - PREAPPROVED → handled via PreApproval entity
 * - PARTIALLY_APPROVED → APPROVED with partialApproval flag
 * - CANCELLED → soft delete (active=false)
 */
public enum ClaimStatus {
    /**
     * Initial draft state. Claim created but not yet submitted.
     * Can be edited freely. No review or approval possible.
     */
    DRAFT("مسودة", false, false),
    
    /**
     * Claim submitted for review.
     * Waiting to be picked up by a reviewer.
     */
    SUBMITTED("مقدم", false, false),
    
    /**
     * Claim is actively being reviewed.
     * Reviewer can approve, reject, or request more information.
     */
    UNDER_REVIEW("قيد المراجعة", false, false),
    
    /**
     * Claim needs correction from provider.
     * Provider must fix data and resubmit.
     * Replaces RETURNED_FOR_INFO (Provider Portal Security Fix).
     */
    NEEDS_CORRECTION("يحتاج تصحيح", false, false),
    
    /**
     * Approval in progress - async processing.
     * Financial calculations and validations are being executed in background.
     */
    APPROVAL_IN_PROGRESS("جاري معالجة الموافقة", false, false),
    
    /**
     * Claim approved for payment.
     * May be full or partial approval (see approvedAmount).
     */
    APPROVED("موافق عليه", true, false),
    
    /**
     * Claim added to a settlement batch.
     * Waiting for batch to be confirmed and paid.
     * NEW: Part of Provider Account Settlement model.
     */
    BATCHED("في دفعة تسوية", true, false),
    
    /**
     * Claim rejected. Requires reviewerComment.
     * Terminal state - cannot be changed.
     */
    REJECTED("مرفوض", true, true),
    
    /**
     * Payment has been processed and completed.
     * Terminal state - cannot be changed.
     */
    SETTLED("تمت التسوية", true, true);

    private final String arabicLabel;
    private final boolean requiresReviewerAction;
    private final boolean terminal;

    ClaimStatus(String arabicLabel, boolean requiresReviewerAction, boolean terminal) {
        this.arabicLabel = arabicLabel;
        this.requiresReviewerAction = requiresReviewerAction;
        this.terminal = terminal;
    }

    public String getArabicLabel() {
        return arabicLabel;
    }

    public boolean requiresReviewerAction() {
        return requiresReviewerAction;
    }

    /**
     * @return true if this is a terminal state that cannot be changed
     */
    public boolean isTerminal() {
        return terminal;
    }

    /**
     * Check if this status allows editing claim details.
     * Only DRAFT and NEEDS_CORRECTION allow edits.
     */
    public boolean allowsEdit() {
        return this == DRAFT || this == NEEDS_CORRECTION;
    }

    /**
     * Get valid next statuses from current status.
     * Used by ClaimStateMachine for validation.
     */
    public Set<ClaimStatus> getValidTransitions() {
        return switch (this) {
            case DRAFT -> Set.of(SUBMITTED);
            case SUBMITTED -> Set.of(UNDER_REVIEW);
            case UNDER_REVIEW -> Set.of(APPROVAL_IN_PROGRESS, REJECTED, NEEDS_CORRECTION);
            case NEEDS_CORRECTION -> Set.of(SUBMITTED);
            case APPROVAL_IN_PROGRESS -> Set.of(APPROVED, REJECTED); // Async result
            case APPROVED -> Set.of(BATCHED); // Finance handoff only (no direct settle)
            case BATCHED -> Set.of(SETTLED, APPROVED); // Settle from batch, or unbatch back to APPROVED
            case REJECTED, SETTLED -> Collections.emptySet(); // Terminal
        };
    }

    /**
     * Check if transition to target status is valid.
     */
    public boolean canTransitionTo(ClaimStatus target) {
        return getValidTransitions().contains(target);
    }

    // ========== LEGACY COMPATIBILITY ==========
    
    /**
     * @deprecated Use SUBMITTED or UNDER_REVIEW instead
     */
    @Deprecated
    public static ClaimStatus PENDING_REVIEW() {
        return SUBMITTED;
    }
    
    /**
     * Map legacy status strings to new statuses.
     * Useful for data migration.
     */
    public static ClaimStatus fromLegacy(String legacyStatus) {
        if (legacyStatus == null) return DRAFT;
        
        return switch (legacyStatus.toUpperCase()) {
            case "PENDING_REVIEW" -> SUBMITTED;
            case "PREAPPROVED" -> SUBMITTED; // PreApproval is separate entity
            case "PARTIALLY_APPROVED" -> APPROVED;
            case "CANCELLED" -> REJECTED; // Or handle via active=false
            default -> {
                try {
                    yield ClaimStatus.valueOf(legacyStatus.toUpperCase());
                } catch (IllegalArgumentException e) {
                    yield DRAFT;
                }
            }
        };
    }
}
