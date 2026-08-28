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
 */
public record GeneralCeilingReading(
        Mode mode,
        BigDecimal limit,
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

    public static GeneralCeilingReading found(BigDecimal limit, BigDecimal committed, BigDecimal reserved) {
        BigDecimal actualRemaining = limit.subtract(committed);
        return new GeneralCeilingReading(Mode.FOUND, limit, committed, reserved,
                actualRemaining, actualRemaining.subtract(reserved), null);
    }

    public static GeneralCeilingReading unlimited(BigDecimal committed, BigDecimal reserved) {
        // Consumption is still real and still worth showing; what does not
        // exist is a ceiling to measure it against, so the two remaining
        // figures stay null rather than being invented.
        return new GeneralCeilingReading(Mode.UNLIMITED, null, committed, reserved, null, null, null);
    }

    public static GeneralCeilingReading notConfigured(String detail) {
        return new GeneralCeilingReading(Mode.NOT_CONFIGURED, null, null, null, null, null, detail);
    }

    public static GeneralCeilingReading unavailable(String detail) {
        return new GeneralCeilingReading(Mode.UNAVAILABLE, null, null, null, null, null, detail);
    }

    public boolean hasFigures() {
        return mode == Mode.FOUND;
    }
}
