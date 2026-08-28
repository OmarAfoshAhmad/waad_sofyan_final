package com.waad.tba.modules.member.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One member's current general ceiling, shaped for a list row.
 *
 * Carries both the date it answers for and the instant it was read. The list
 * and the drawer are separate requests, so a claim approved between them makes
 * their figures differ honestly; without a read timestamp that difference
 * looks like one of the two screens being wrong.
 *
 * The headline figure a reader acts on is reservableAvailable -- what may
 * still be committed -- not actualRemaining. Someone deciding whether to
 * approve treatment has to see money already held against the ceiling as
 * unavailable, because committing it twice is exactly the mistake the hold
 * exists to prevent. actualRemaining travels alongside for reconciliation,
 * where a hold is correctly not a payment.
 */
public record CurrentGeneralLimitSummary(
        LocalDate asOfDate,
        LocalDateTime readAt,
        Mode mode,
        /** The policy these figures belong to, null unless mode is FOUND or UNLIMITED. */
        Long policyId,
        BigDecimal limit,
        BigDecimal committed,
        BigDecimal reserved,
        BigDecimal actualRemaining,
        BigDecimal reservableAvailable,
        BigDecimal utilizationPercent,
        AlertStatus alertStatus) {

    /** Mirrors GeneralCeilingReading.Mode; repeated here so the API contract stands alone. */
    public enum Mode { FOUND, UNLIMITED, NOT_CONFIGURED, UNAVAILABLE }

    public enum AlertStatus {
        NORMAL,
        /** 20% or less of the ceiling may still be committed. */
        WARNING,
        /** 10% or less may still be committed. */
        CRITICAL,
        /** Nothing further may be committed, though nothing has been overspent. */
        EXHAUSTED,
        /** Spending has passed the ceiling; the overage is actualRemaining, negative. */
        EXCEEDED,
        /** No ceiling applies, so there is nothing to be near the end of. */
        UNLIMITED,
        /** The balance could not be read. Deliberately not a financial state. */
        UNAVAILABLE
    }
}
