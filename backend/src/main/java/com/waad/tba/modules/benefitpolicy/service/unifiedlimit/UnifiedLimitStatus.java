package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

/**
 * P1.3 §2: exactly five states, never {@code null} in place of any of them.
 * {@code EXHAUSTED} and {@code BLOCKED} are deliberately distinct -- the
 * first is an ordinary, expected business outcome (a limit ran out);
 * the second is a structural failure (inconsistent data) that must never
 * be presented as though it were the first.
 */
public enum UnifiedLimitStatus {

    /** No limit of any kind applies to this consumption. */
    UNLIMITED,

    /** A limit applies, and the full request fits inside it. */
    LIMITED,

    /** A limit applies, and only part of the request is approved. */
    PARTIAL,

    /** A limit applies, and nothing is available. */
    EXHAUSTED,

    /**
     * The decision was rejected structurally before any balance was
     * computed (e.g. BUCKET_POLICY_MISMATCH). No numeric field on the
     * decision is meaningful when this is the status.
     */
    BLOCKED
}
