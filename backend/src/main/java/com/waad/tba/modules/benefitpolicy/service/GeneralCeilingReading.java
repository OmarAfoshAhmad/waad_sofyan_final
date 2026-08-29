package com.waad.tba.modules.benefitpolicy.service;

import java.math.BigDecimal;

/**
 * One member's general ceiling as the screen must present it.
 *
 * The mode exists because three of the four outcomes have no number, and
 * rendering any of them as 0.00 states something false and actionable: a
 * reader deciding whether to approve treatment cannot tell "this member has
 * spent their whole ceiling" from "this policy sets no ceiling" from "the
 * balance could not be read". Only FOUND carries figures.
 *
 * {@code limit} is what applies to this member, and it is not always what
 * their policy says. An exceptional uplift raises one person's ceiling without
 * touching the policy every one of their colleagues shares, so the reading
 * carries both halves: {@code policyLimit} is the group's entitlement,
 * {@code uplift} is what was added for this member alone, and {@code limit} is
 * their sum. Keeping them apart is not presentation -- a ceiling nobody can
 * decompose is a ceiling nobody can check, and the whole point of an exception
 * is that someone can later ask why it was made.
 */
public record GeneralCeilingReading(
        Mode mode,
        BigDecimal limit,
        BigDecimal policyLimit,
        BigDecimal uplift,
        BigDecimal committed,
        BigDecimal reserved,
        BigDecimal actualRemaining,
        BigDecimal reservableAvailable,
        String detail) {

    public enum Mode {
        /** A ceiling applies and every figure below is real. */
        FOUND,
        /** The policy sets no monetary ceiling. Not a very large number, and not zero. */
        UNLIMITED,
        /** A policy applies but defines no general ceiling at all. */
        NOT_CONFIGURED,
        /** The read failed. Never to be displayed as a balance. */
        UNAVAILABLE
    }

    /** A ceiling with no exception on it: policyLimit is the whole of it. */
    public static GeneralCeilingReading found(BigDecimal limit, BigDecimal committed, BigDecimal reserved) {
        return found(limit, BigDecimal.ZERO, committed, reserved);
    }

    public static GeneralCeilingReading found(BigDecimal policyLimit, BigDecimal uplift,
            BigDecimal committed, BigDecimal reserved) {
        BigDecimal appliedUplift = uplift == null ? BigDecimal.ZERO : uplift;
        BigDecimal effectiveLimit = policyLimit.add(appliedUplift);
        BigDecimal actualRemaining = effectiveLimit.subtract(committed);
        return new GeneralCeilingReading(Mode.FOUND, effectiveLimit, policyLimit, appliedUplift,
                committed, reserved, actualRemaining, actualRemaining.subtract(reserved), null);
    }

    public static GeneralCeilingReading unlimited(BigDecimal committed, BigDecimal reserved) {
        // Consumption is still real and still worth showing; what does not
        // exist is a ceiling to measure it against, so the two remaining
        // figures stay null rather than being invented. An uplift on top of no
        // ceiling would be meaningless, which is why granting one is refused
        // in this mode rather than silently ignored here.
        return new GeneralCeilingReading(Mode.UNLIMITED, null, null, null, committed, reserved, null, null, null);
    }

    public static GeneralCeilingReading notConfigured(String detail) {
        return new GeneralCeilingReading(Mode.NOT_CONFIGURED, null, null, null, null, null, null, null, detail);
    }

    public static GeneralCeilingReading unavailable(String detail) {
        return new GeneralCeilingReading(Mode.UNAVAILABLE, null, null, null, null, null, null, null, detail);
    }

    public boolean hasFigures() {
        return mode == Mode.FOUND;
    }

    /** Whether an exception is raising this member's ceiling right now. */
    public boolean hasUplift() {
        return uplift != null && uplift.signum() > 0;
    }
}
