package com.waad.tba.modules.claim.service;

import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.waad.tba.common.exception.ClaimStateTransitionException;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.rbac.entity.User;

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
public class ClaimStateMachine {

    // Role names must match SystemRole enum values exactly
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_ACCOUNTANT = "ACCOUNTANT";
    private static final String ROLE_EMPLOYER = "EMPLOYER_ADMIN";
    private static final String ROLE_REVIEWER = "MEDICAL_REVIEWER";
    private static final String ROLE_PROVIDER = "PROVIDER_STAFF";

    /**
     * Validate if a transition is allowed and perform it.
     * 
     * @param claim        The claim to transition
     * @param targetStatus The desired target status
     * @param currentUser  The user attempting the transition
     * @throws ClaimStateTransitionException if transition is invalid
     */
    public void transition(Claim claim, ClaimStatus targetStatus, User currentUser) {
        ClaimStatus currentStatus = claim.getStatus();

        log.info("🔄 Claim transition request: {} → {} by user {}",
                currentStatus, targetStatus, currentUser.getUsername());

        // Rule 1: Check if transition is valid in the state machine
        validateTransitionPath(currentStatus, targetStatus);

        // Rule 2: Check if user has required role for this transition
        validateRolePermission(currentStatus, targetStatus, currentUser);

        // Rule 3: Apply business rules for specific transitions
        validateTransitionRequirements(claim, targetStatus);

        // All validations passed - perform transition
        claim.setStatus(targetStatus);
        claim.setUpdatedBy(currentUser.getUsername());

        // Set reviewedAt timestamp for reviewer actions
        if (targetStatus.requiresReviewerAction()) {
            claim.setReviewedAt(LocalDateTime.now());
        }

        log.info("✅ Claim {} transitioned: {} → {}", claim.getId(), currentStatus, targetStatus);
    }

    /**
     * Check if transition path is valid according to state machine.
     */
    private void validateTransitionPath(ClaimStatus from, ClaimStatus to) {
        if (from == to) {
            return; // No-op, allow same status
        }

        if (from.isTerminal()) {
            throw new ClaimStateTransitionException(
                    from.name(), to.name(),
                    "Cannot transition from terminal state " + from.name());
        }

        if (!from.canTransitionTo(to)) {
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
        return switch (from) {
            case DRAFT -> switch (to) {
                case SUBMITTED -> Set.of(ROLE_EMPLOYER, ROLE_ACCOUNTANT, ROLE_PROVIDER);
                default -> Set.of();
            };
            case SUBMITTED -> switch (to) {
                case UNDER_REVIEW -> Set.of(ROLE_ACCOUNTANT, ROLE_REVIEWER);
                case SUBMITTED -> Set.of();
                default -> Set.of();
            };
            case UNDER_REVIEW -> switch (to) {
                case APPROVAL_IN_PROGRESS -> Set.of(ROLE_ACCOUNTANT, ROLE_REVIEWER);
                case APPROVED, REJECTED -> Set.of(ROLE_ACCOUNTANT, ROLE_REVIEWER);
                case NEEDS_CORRECTION -> Set.of(ROLE_REVIEWER);
                default -> Set.of();
            };
            case APPROVAL_IN_PROGRESS -> switch (to) {
                case APPROVED, REJECTED -> Set.of(ROLE_ACCOUNTANT, ROLE_REVIEWER);
                default -> Set.of();
            };
            case NEEDS_CORRECTION -> switch (to) {
                case APPROVED -> Set.of(ROLE_ACCOUNTANT, ROLE_REVIEWER); // After correction verified
                default -> Set.of();
            };
            case APPROVED -> switch (to) {
                case BATCHED -> Set.of(ROLE_ACCOUNTANT);
                case NEEDS_CORRECTION -> Set.of(ROLE_REVIEWER, ROLE_ACCOUNTANT); // Reviewer suspends
                default -> Set.of();
            };
            case BATCHED -> switch (to) {
                case SETTLED, APPROVED -> Set.of(ROLE_ACCOUNTANT);
                default -> Set.of();
            };
            default -> Set.of();
        };
    }

    /**
     * Validate business requirements for specific transitions.
     */
    private void validateTransitionRequirements(Claim claim, ClaimStatus targetStatus) {
        switch (targetStatus) {
            case REJECTED -> {
                if (claim.getReviewerComment() == null || claim.getReviewerComment().isBlank()) {
                    throw new ClaimStateTransitionException(
                            "Cannot reject claim without reviewer comment. Please provide rejection reason.");
                }
            }
            case APPROVED -> {
                if (claim.getApprovedAmount() == null ||
                        claim.getApprovedAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    throw new ClaimStateTransitionException(
                            "Cannot approve claim without approved amount. Please set approvedAmount > 0.");
                }
            }
            case SETTLED -> {
                if (claim.getStatus() != ClaimStatus.APPROVED) {
                    throw new ClaimStateTransitionException(
                            claim.getStatus().name(), targetStatus.name(),
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
        Set<ClaimStatus> validTransitions = current.getValidTransitions();
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
