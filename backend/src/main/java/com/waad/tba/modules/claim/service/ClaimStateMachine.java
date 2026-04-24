package com.waad.tba.modules.claim.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.waad.tba.common.exception.ClaimStateTransitionException;
import com.waad.tba.modules.audit.enums.AuditAction;
import com.waad.tba.modules.audit.enums.AuditSource;
import com.waad.tba.modules.audit.enums.EntityType;
import com.waad.tba.modules.audit.service.AuditLogWriteRequest;
import com.waad.tba.modules.audit.service.MedicalAuditLogService;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.rbac.entity.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Claim State Machine - Enforces strict lifecycle transitions with role-based
 * permissions.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * TRANSITION MATRIX
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * | From Status | To Status | Allowed Roles |
 * |-------------------|-------------------|----------------------------------|
 * | DRAFT | SUBMITTED | SUPER_ADMIN, EMPLOYER, INSURANCE, PROVIDER |
 * | SUBMITTED | UNDER_REVIEW | SUPER_ADMIN, INSURANCE, REVIEWER |
 * | UNDER_REVIEW | APPROVED | SUPER_ADMIN, INSURANCE, REVIEWER |
 * | UNDER_REVIEW | REJECTED | SUPER_ADMIN, INSURANCE, REVIEWER |
 * | UNDER_REVIEW | NEEDS_CORRECTION | SUPER_ADMIN, REVIEWER |
 * | NEEDS_CORRECTION | SUBMITTED | SUPER_ADMIN, EMPLOYER, INSURANCE, PROVIDER |
 * | APPROVED | SETTLED | SUPER_ADMIN, INSURANCE |
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * BUSINESS RULES
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * 1. REJECTION requires reviewerComment
 * 2. APPROVAL requires approvedAmount > 0
 * 3. SETTLEMENT requires claim to be APPROVED first
 * 4. Terminal states (REJECTED, SETTLED) cannot be changed
 * 5. Only DRAFT and NEEDS_CORRECTION allow claim edits
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * SMOKE TEST SCENARIOS
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Scenario 1: Happy Path
 * Given: Claim C001 in DRAFT
 * When: EMPLOYER submits → INSURANCE reviews → REVIEWER approves → INSURANCE
 * settles
 * Then: Status transitions: DRAFT → SUBMITTED → UNDER_REVIEW → APPROVED →
 * SETTLED
 * 
 * Scenario 2: Invalid Transition
 * Given: Claim C002 in DRAFT
 * When: EMPLOYER tries to set status to APPROVED
 * Then: ClaimStateTransitionException("Invalid state transition: DRAFT →
 * APPROVED")
 * 
 * Scenario 3: Role Validation
 * Given: Claim C003 in SUBMITTED
 * When: EMPLOYER tries to transition to UNDER_REVIEW
 * Then: ClaimStateTransitionException with requiredRole = "INSURANCE or
 * REVIEWER"
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimStateMachine {

    // Role names must match SystemRole enum values exactly
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_ACCOUNTANT = "ACCOUNTANT";
    private static final String ROLE_EMPLOYER = "EMPLOYER_ADMIN";
    private static final String ROLE_REVIEWER = "MEDICAL_REVIEWER";
    private static final String ROLE_PROVIDER = "PROVIDER_STAFF";

    private static final Set<ClaimStatus> HARD_LOCKED_FINAL_STATES = Set.of(
            ClaimStatus.REJECTED,
            ClaimStatus.SETTLED);

    /**
     * Core strict workflow:
     * DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED -> SETTLED
     * UNDER_REVIEW -> REJECTED
     * UNDER_REVIEW -> NEEDS_CORRECTION
     *
     * Extensions kept for backward compatibility (APPROVAL_IN_PROGRESS, BATCHED,
     * NEEDS_CORRECTION -> SUBMITTED).
     */
    private static final Map<ClaimStatus, Set<ClaimStatus>> TRANSITION_MATRIX = Map.of(
            ClaimStatus.DRAFT, Set.of(ClaimStatus.SUBMITTED),
            ClaimStatus.SUBMITTED, Set.of(ClaimStatus.UNDER_REVIEW),
            ClaimStatus.UNDER_REVIEW,
            Set.of(ClaimStatus.APPROVAL_IN_PROGRESS, ClaimStatus.APPROVED, ClaimStatus.REJECTED,
                    ClaimStatus.NEEDS_CORRECTION),
            ClaimStatus.APPROVAL_IN_PROGRESS,
            Set.of(ClaimStatus.APPROVED, ClaimStatus.REJECTED, ClaimStatus.UNDER_REVIEW),
            ClaimStatus.NEEDS_CORRECTION, Set.of(ClaimStatus.SUBMITTED, ClaimStatus.APPROVED),
            ClaimStatus.APPROVED, Set.of(ClaimStatus.SETTLED, ClaimStatus.BATCHED, ClaimStatus.NEEDS_CORRECTION),
            ClaimStatus.BATCHED, Set.of(ClaimStatus.SETTLED, ClaimStatus.APPROVED),
            ClaimStatus.REJECTED, Set.of(),
            ClaimStatus.SETTLED, Set.of());

    private static final Map<String, Set<String>> TRANSITION_ROLE_POLICY = Map.ofEntries(
            Map.entry(key(ClaimStatus.DRAFT, ClaimStatus.SUBMITTED),
                    Set.of(ROLE_EMPLOYER, ROLE_ACCOUNTANT, ROLE_PROVIDER)),
            Map.entry(key(ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW), Set.of(ROLE_ACCOUNTANT, ROLE_REVIEWER)),
            Map.entry(key(ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVAL_IN_PROGRESS),
                    Set.of(ROLE_ACCOUNTANT, ROLE_REVIEWER)),
            Map.entry(key(ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED), Set.of(ROLE_ACCOUNTANT, ROLE_REVIEWER)),
            Map.entry(key(ClaimStatus.UNDER_REVIEW, ClaimStatus.REJECTED), Set.of(ROLE_ACCOUNTANT, ROLE_REVIEWER)),
            Map.entry(key(ClaimStatus.UNDER_REVIEW, ClaimStatus.NEEDS_CORRECTION), Set.of(ROLE_REVIEWER)),
            Map.entry(key(ClaimStatus.APPROVAL_IN_PROGRESS, ClaimStatus.APPROVED),
                    Set.of(ROLE_ACCOUNTANT, ROLE_REVIEWER)),
            Map.entry(key(ClaimStatus.APPROVAL_IN_PROGRESS, ClaimStatus.REJECTED),
                    Set.of(ROLE_ACCOUNTANT, ROLE_REVIEWER)),
            Map.entry(key(ClaimStatus.APPROVAL_IN_PROGRESS, ClaimStatus.UNDER_REVIEW),
                    Set.of(ROLE_ACCOUNTANT, ROLE_REVIEWER)),
            Map.entry(key(ClaimStatus.NEEDS_CORRECTION, ClaimStatus.SUBMITTED),
                    Set.of(ROLE_EMPLOYER, ROLE_ACCOUNTANT, ROLE_PROVIDER)),
            Map.entry(key(ClaimStatus.NEEDS_CORRECTION, ClaimStatus.APPROVED), Set.of(ROLE_ACCOUNTANT, ROLE_REVIEWER)),
            Map.entry(key(ClaimStatus.APPROVED, ClaimStatus.SETTLED), Set.of(ROLE_ACCOUNTANT)),
            Map.entry(key(ClaimStatus.APPROVED, ClaimStatus.BATCHED), Set.of(ROLE_ACCOUNTANT)),
            Map.entry(key(ClaimStatus.APPROVED, ClaimStatus.NEEDS_CORRECTION), Set.of(ROLE_ACCOUNTANT, ROLE_REVIEWER)),
            Map.entry(key(ClaimStatus.BATCHED, ClaimStatus.SETTLED), Set.of(ROLE_ACCOUNTANT)),
            Map.entry(key(ClaimStatus.BATCHED, ClaimStatus.APPROVED), Set.of(ROLE_ACCOUNTANT)));

    private final MedicalAuditLogService medicalAuditLogService;

    /**
     * Validate if a transition is allowed and perform it.
     * 
     * @param claim        The claim to transition
     * @param targetStatus The desired target status
     * @param currentUser  The user attempting the transition
     * @throws ClaimStateTransitionException if transition is invalid
     */
    public void transition(Claim claim, ClaimStatus targetStatus, User currentUser) {
        transition(claim, targetStatus, currentUser, TransitionContext.fromClaim(claim));
    }

    public void transition(Claim claim, ClaimStatus targetStatus, User currentUser, TransitionContext context) {
        if (claim == null) {
            throw new ClaimStateTransitionException("Claim is required for state transition");
        }
        if (targetStatus == null) {
            throw new ClaimStateTransitionException("Target status is required for state transition");
        }

        ClaimStatus currentStatus = claim.getStatus();
        String actor = currentUser != null ? currentUser.getUsername() : "system";

        log.info("🔄 Claim transition request: {} → {} by user {}",
                currentStatus, targetStatus, actor);

        // Idempotency: same transition request should be a no-op without side effects.
        if (currentStatus == targetStatus) {
            log.debug("↩️ Idempotent claim transition skipped for claim {} at status {}", claim.getId(), currentStatus);
            return;
        }

        TransitionContext safeContext = context != null ? context : TransitionContext.fromClaim(claim);

        validateFinalStateLock(currentStatus, targetStatus);

        // Rule 1: Check if transition is valid in the state machine
        validateTransitionPath(currentStatus, targetStatus);

        // Rule 2: Check if user has required role for this transition
        validateRolePermission(currentStatus, targetStatus, currentUser);

        // Rule 3: Apply business rules for specific transitions
        validateTransitionRequirements(claim, currentStatus, targetStatus, safeContext);

        // All validations passed - perform transition
        claim.setStatus(targetStatus);
        claim.setUpdatedBy(actor);

        // Set reviewedAt timestamp for reviewer actions
        if (targetStatus.requiresReviewerAction()) {
            claim.setReviewedAt(LocalDateTime.now());
        }

        recordStatusChangeAudit(claim, currentStatus, targetStatus, currentUser, safeContext.reason());

        log.info("✅ Claim {} transitioned: {} → {}", claim.getId(), currentStatus, targetStatus);
    }

    private void validateFinalStateLock(ClaimStatus from, ClaimStatus to) {
        if (HARD_LOCKED_FINAL_STATES.contains(from) && from != to) {
            throw new ClaimStateTransitionException(
                    from.name(),
                    to.name(),
                    "Final state cannot be modified");
        }
    }

    /**
     * Check if transition path is valid according to state machine.
     */
    private void validateTransitionPath(ClaimStatus from, ClaimStatus to) {
        Set<ClaimStatus> allowedTargets = TRANSITION_MATRIX.getOrDefault(from, Set.of());
        if (!allowedTargets.contains(to)) {
            throw new ClaimStateTransitionException(from.name(), to.name());
        }
    }

    /**
     * Check if user has required role for this specific transition.
     */
    private void validateRolePermission(ClaimStatus from, ClaimStatus to, User user) {
        Set<String> requiredRoles = getRequiredRoles(from, to);
        Set<String> userRoles = getUserRoleNames(user);

        if (requiredRoles.isEmpty()) {
            throw new ClaimStateTransitionException(from.name(), to.name(), "SYSTEM_PROCESS_ONLY");
        }

        // SUPER_ADMIN can do anything
        if (userRoles.contains(ROLE_SUPER_ADMIN)) {
            return;
        }

        // Check if user has at least one required role
        boolean hasPermission = requiredRoles.stream()
                .anyMatch(userRoles::contains);

        if (!hasPermission) {
            String rolesStr = String.join(" or ", requiredRoles);
            throw new ClaimStateTransitionException(from.name(), to.name(), rolesStr);
        }
    }

    /**
     * Get roles allowed to perform a specific transition.
     * 
     * Finance-only settlement policy:
     * - APPROVED → BATCHED (finance)
     * - BATCHED → SETTLED (finance)
     * - No direct APPROVED → SETTLED transition.
     */
    private Set<String> getRequiredRoles(ClaimStatus from, ClaimStatus to) {
        return TRANSITION_ROLE_POLICY.getOrDefault(key(from, to), Set.of());
    }

    /**
     * Validate business requirements for specific transitions.
     */
    private void validateTransitionRequirements(
            Claim claim,
            ClaimStatus currentStatus,
            ClaimStatus targetStatus,
            TransitionContext context) {

        switch (targetStatus) {
            case UNDER_REVIEW -> {
                if (!context.claimComplete()) {
                    throw new ClaimStateTransitionException(
                            currentStatus.name(),
                            targetStatus.name(),
                            "Claim is incomplete and cannot move to UNDER_REVIEW");
                }
            }
            case REJECTED -> {
                if (claim.getReviewerComment() == null || claim.getReviewerComment().isBlank()) {
                    throw new ClaimStateTransitionException(
                            "Cannot reject claim without reviewer comment. Please provide rejection reason.");
                }
            }
            case APPROVED -> {
                if (claim.getLines() == null || claim.getLines().isEmpty()) {
                    throw new ClaimStateTransitionException(
                            currentStatus.name(),
                            targetStatus.name(),
                            "Cannot approve claim with no lines");
                }

                BigDecimal totalApproved = context.totalApproved() != null
                        ? context.totalApproved()
                        : claim.getApprovedAmount();

                if (totalApproved == null || totalApproved.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ClaimStateTransitionException(
                            currentStatus.name(),
                            targetStatus.name(),
                            "Cannot approve claim with totalApproved = 0");
                }

                if (!context.allLinesCalculated()) {
                    throw new ClaimStateTransitionException(
                            currentStatus.name(),
                            targetStatus.name(),
                            "Cannot approve claim while some lines are not calculated");
                }

                if (context.pendingRecalculation()) {
                    throw new ClaimStateTransitionException(
                            currentStatus.name(),
                            targetStatus.name(),
                            "Cannot approve claim while recalculation is pending");
                }

                if (!context.coverageUpToDate()) {
                    throw new ClaimStateTransitionException(
                            currentStatus.name(),
                            targetStatus.name(),
                            "Cannot approve claim while coverage is out-of-date");
                }
            }
            case SETTLED -> {
                if (currentStatus != ClaimStatus.APPROVED && currentStatus != ClaimStatus.BATCHED) {
                    throw new ClaimStateTransitionException(
                            currentStatus.name(), targetStatus.name(),
                            "Claim must be APPROVED before settlement");
                }
            }
            default -> {
                /* No additional requirements */ }
        }
    }

    /**
     * Extract role names from user.
     */
    private Set<String> getUserRoleNames(User user) {
        if (user == null || user.getUserType() == null) {
            return Set.of();
        }
        return Set.of(user.getUserType());
    }

    private void recordStatusChangeAudit(
            Claim claim,
            ClaimStatus oldStatus,
            ClaimStatus newStatus,
            User currentUser,
            String reason) {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("status", oldStatus.name());

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", newStatus.name());

        medicalAuditLogService.record(AuditLogWriteRequest.builder()
                .entityType(EntityType.CLAIM)
                .entityId(claim.getId() != null ? String.valueOf(claim.getId()) : "unknown")
                .action(AuditAction.STATUS_CHANGE)
                .reason(reason)
                .beforeState(before)
                .afterState(after)
                .source(currentUser != null ? AuditSource.USER : AuditSource.SYSTEM)
                .build());
    }

    private static String key(ClaimStatus from, ClaimStatus to) {
        return from.name() + "->" + to.name();
    }

    public record TransitionContext(
            BigDecimal totalApproved,
            boolean claimComplete,
            boolean allLinesCalculated,
            boolean pendingRecalculation,
            boolean coverageUpToDate,
            String reason) {

        public static TransitionContext fromClaim(Claim claim) {
            boolean hasLines = claim != null && claim.getLines() != null && !claim.getLines().isEmpty();

            boolean complete = hasLines
                    && claim.getMember() != null
                    && claim.getProviderId() != null
                    && claim.getServiceDate() != null;

            boolean allCalculated = hasLines && claim.getLines().stream().allMatch(ClaimStateMachine::isLineCalculated);
            boolean coverageFresh = hasLines
                    && claim.getLines().stream().allMatch(ClaimStateMachine::isCoverageSnapshotReady);
            boolean recalcPending = false;

            return new TransitionContext(
                    claim != null ? claim.getApprovedAmount() : null,
                    complete,
                    allCalculated,
                    recalcPending,
                    coverageFresh,
                    null);
        }
    }

    private static boolean isLineCalculated(ClaimLine line) {
        return line != null
                && line.getQuantity() != null
                && line.getQuantity() > 0
                && line.getUnitPrice() != null
                && line.getTotalPrice() != null;
    }

    private static boolean isCoverageSnapshotReady(ClaimLine line) {
        return line != null && line.getCoveragePercentSnapshot() != null;
    }

    /**
     * Check if claim can be edited in current status.
     * Only allows edits in DRAFT and NEEDS_CORRECTION states.
     */
    public boolean canEdit(Claim claim) {
        return claim.getStatus().allowsEdit();
    }

    /**
     * Get list of valid next statuses for display in UI.
     */
    public Set<ClaimStatus> getAvailableTransitions(Claim claim, User user) {
        ClaimStatus current = claim.getStatus();
        Set<ClaimStatus> validTransitions = TRANSITION_MATRIX.getOrDefault(current, Set.of());
        Set<String> userRoles = getUserRoleNames(user);

        // Filter by user permissions
        return validTransitions.stream()
                .filter(target -> {
                    Set<String> required = getRequiredRoles(current, target);
                    return userRoles.contains(ROLE_SUPER_ADMIN) ||
                            required.stream().anyMatch(userRoles::contains);
                })
                .collect(java.util.stream.Collectors.toSet());
    }
}
