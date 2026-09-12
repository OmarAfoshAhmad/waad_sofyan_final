package com.waad.tba.modules.preauthorization.domain;

import com.waad.tba.common.error.ErrorCode;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus.*;

/**
 * The one place that says which pre-authorization status may follow which.
 *
 * Before this class the rule lived in eight places that disagreed with each
 * other: three {@code canBeX()} predicates on the entity, two different
 * {@code startReview} guards, the ledger's APPROVABLE/CANCELLABLE lists, and
 * a review endpoint that copied whatever status the request body named. That
 * last one is why this exists -- an approved authorization could be flipped
 * to rejected, or a pending one jumped straight to approved, with the
 * reservation ledger never hearing about either.
 *
 * The matrix below is what the code already permitted, made explicit; it
 * widens nothing. Where two old guards disagreed, the ledger's view won,
 * because the ledger is what actually holds member money.
 *
 * Deliberately stateless and Spring-free: the entity's own mutators call it,
 * and a domain rule should not need a container to be true.
 */
public final class PreAuthStateMachine {

    private PreAuthStateMachine() {
    }

    /**
     * Nothing leaves these. REJECTED, CANCELLED and EXPIRED are decisions;
     * USED and CONSUMED mean a claim has posted against the hold, and a
     * status change here would strand that claim's consumption rows.
     */
    public static final Set<PreAuthStatus> TERMINAL = EnumSet.of(REJECTED, CANCELLED, EXPIRED, USED, CONSUMED);

    /**
     * Sitting in the reviewer's inbox, not yet picked up. Two start-review
     * endpoints used to disagree on this set (one accepted PENDING only);
     * the inbox query, and both of them, read it from here.
     */
    public static final Set<PreAuthStatus> AWAITING_REVIEW = EnumSet.of(PENDING, SUBMITTED, RESUBMITTED);

    private static final Map<PreAuthStatus, Set<PreAuthStatus>> TRANSITIONS = new EnumMap<>(PreAuthStatus.class);

    static {
        // DRAFT is the entity default and is never persisted by the create
        // path (which writes PENDING); kept so a legacy row can still enter.
        TRANSITIONS.put(DRAFT, EnumSet.of(PENDING, SUBMITTED));

        // The intake states. SUBMITTED and RESUBMITTED are no longer written
        // by any code path but exist in data; they behave as PENDING does
        // except that cancellation, per the ledger, acts from PENDING only.
        TRANSITIONS.put(PENDING, EnumSet.of(UNDER_REVIEW, APPROVAL_IN_PROGRESS, APPROVED, PARTIALLY_APPROVED,
                REJECTED, NEEDS_CORRECTION, CANCELLED));
        TRANSITIONS.put(SUBMITTED, EnumSet.of(UNDER_REVIEW, APPROVAL_IN_PROGRESS, APPROVED, PARTIALLY_APPROVED,
                REJECTED, NEEDS_CORRECTION));
        TRANSITIONS.put(RESUBMITTED, EnumSet.of(UNDER_REVIEW, APPROVAL_IN_PROGRESS, APPROVED, PARTIALLY_APPROVED,
                REJECTED, NEEDS_CORRECTION));

        // A provider that was asked to fix data resubmits into review.
        TRANSITIONS.put(NEEDS_CORRECTION, EnumSet.of(UNDER_REVIEW));
        TRANSITIONS.put(INFO_REQUESTED, EnumSet.of(UNDER_REVIEW));

        // Review. Approval may be synchronous (ledger writes APPROVED /
        // PARTIALLY_APPROVED directly from finalizeReview) or asynchronous
        // (APPROVAL_IN_PROGRESS first, then the ledger).
        TRANSITIONS.put(UNDER_REVIEW, EnumSet.of(APPROVAL_IN_PROGRESS, APPROVED, PARTIALLY_APPROVED,
                REJECTED, NEEDS_CORRECTION));
        TRANSITIONS.put(APPROVAL_IN_PROGRESS, EnumSet.of(APPROVED, PARTIALLY_APPROVED, REJECTED));

        // Granted. The only exits are the ledger's: acknowledged by the
        // provider, cancelled with a reason, expired by the sweep, or used by
        // the claim that converts it. Acknowledgement is currently offered
        // for full approvals only, as the acknowledge endpoint always was.
        TRANSITIONS.put(APPROVED, EnumSet.of(ACKNOWLEDGED, CANCELLED, EXPIRED, USED));
        TRANSITIONS.put(PARTIALLY_APPROVED, EnumSet.of(CANCELLED, EXPIRED, USED));
        TRANSITIONS.put(ACKNOWLEDGED, EnumSet.of(CANCELLED, EXPIRED, USED));

        for (PreAuthStatus terminal : TERMINAL) {
            TRANSITIONS.put(terminal, EnumSet.noneOf(PreAuthStatus.class));
        }
    }

    public static boolean canTransition(PreAuthStatus from, PreAuthStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return from == to || TRANSITIONS.getOrDefault(from, EnumSet.noneOf(PreAuthStatus.class)).contains(to);
    }

    public static Set<PreAuthStatus> targetsFrom(PreAuthStatus from) {
        return from == null ? EnumSet.noneOf(PreAuthStatus.class)
                : EnumSet.copyOf(TRANSITIONS.getOrDefault(from, EnumSet.noneOf(PreAuthStatus.class)));
    }

    public static boolean isTerminal(PreAuthStatus status) {
        return status != null && TERMINAL.contains(status);
    }

    /**
     * Validate and apply. Same-status is a no-op rather than an error so a
     * retried request (the async approval path retries) does not fail on
     * its own success.
     *
     * @throws BusinessRuleException with {@link ErrorCode#INVALID_PREAUTH_TRANSITION}
     */
    public static void transition(PreAuthorization preAuth, PreAuthStatus to) {
        if (preAuth == null) {
            throw new IllegalArgumentException("preAuth is required");
        }
        if (to == null) {
            throw new IllegalArgumentException("target status is required");
        }
        PreAuthStatus from = preAuth.getStatus();
        if (from == to) {
            return;
        }
        if (!canTransition(from, to)) {
            throw new BusinessRuleException(ErrorCode.INVALID_PREAUTH_TRANSITION, message(from, to));
        }
        preAuth.setStatus(to);
    }

    private static String message(PreAuthStatus from, PreAuthStatus to) {
        String fromLabel = from == null ? "غير محددة" : from.getArabicLabel();
        if (isTerminal(from)) {
            return "الموافقة المسبقة في حالة نهائية (" + fromLabel + ") ولا يمكن تغييرها.";
        }
        return "لا يمكن نقل الموافقة المسبقة من حالة «" + fromLabel + "» إلى «" + to.getArabicLabel() + "».";
    }
}
