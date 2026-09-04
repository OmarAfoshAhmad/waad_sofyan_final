package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

/**
 * P1.4.1: which reservation-availability formula applies to this decision.
 *
 * Not a boolean. A claim converted from its own pre-authorization computes
 * "available" with a genuinely different equation than an ordinary claim --
 * {@code min(actualRemaining, reservableAvailable + ownActiveReservation)},
 * built from {@code LimitBalanceReader.readForPreauthorizedClaim}'s actual
 * formula -- not just a different input value on the same formula. See
 * docs/finance/P1_4_UNIFIED_LIMIT_RESOLVER_DESIGN.md.
 */
public enum ReservationEvaluationMode {

    /** An ordinary claim, unrelated to any live pre-authorization. */
    NORMAL,

    /**
     * A claim converted from its own pending pre-authorization: that
     * reservation's own hold is returned to it, capped at what actually
     * remains, so the claim is never penalized by -- or allowed to exceed
     * the real balance because of -- its own prior hold.
     */
    PREAUTHORIZED_CLAIM,

    /**
     * Creating or evaluating a pre-authorization itself, before any claim
     * exists to own a reservation yet. Numerically equal to NORMAL today,
     * kept distinct because the two may need to diverge for unrelated
     * reasons later (see design doc).
     */
    PREAUTH_RESERVATION
}
