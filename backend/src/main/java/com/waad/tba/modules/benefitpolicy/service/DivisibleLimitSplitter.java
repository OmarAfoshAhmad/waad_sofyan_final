package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The one definition of "how much of a line/decision is payable when a
 * times-limit falls short of what was requested" -- shared by claims
 * (CoverageEngineService) and pre-authorizations (PreAuthorizationDecisionBuilder)
 * so the same requested/remaining-times input never produces two different
 * money amounts depending on which path evaluated it.
 *
 * Only {@link CountingMethod#EACH_UNIT} is divisible: five of eight
 * physiotherapy sessions can be paid and three refused, because each unit is
 * its own occurrence. Every other counting method (EACH_LINE, PER_VISIT,
 * PER_DAY) is one indivisible occurrence -- a visit is not half-attended --
 * so a shortfall there refuses the whole amount, never a fraction of it.
 */
public final class DivisibleLimitSplitter {

    private DivisibleLimitSplitter() {
    }

    /** requestedUnits and remainingTimes are both >= 0 by construction here. */
    public record UnitSplit(int coveredUnits, int refusedUnits) {
    }

    /**
     * @param method         how this bucket counts occurrences
     * @param requestedUnits how many occurrences this line/decision asks for
     *                       (its quantity, for EACH_UNIT; 1 for every other
     *                       method, or whatever the caller's own occurrence
     *                       count already is)
     * @param remainingTimes timesLimit minus already-used times, clamped to
     *                       zero by the caller if negative. Only meaningful
     *                       when the limit was actually exceeded -- callers
     *                       must not invoke this when there is no shortfall.
     */
    public static UnitSplit splitUnits(CountingMethod method, int requestedUnits, long remainingTimes) {
        int safeRequested = Math.max(0, requestedUnits);
        long safeRemaining = Math.max(0, remainingTimes);
        if (method != CountingMethod.EACH_UNIT) {
            // Indivisible: either the whole occurrence fits, or none of it does.
            boolean fits = safeRemaining >= safeRequested;
            return fits ? new UnitSplit(safeRequested, 0) : new UnitSplit(0, safeRequested);
        }
        int covered = (int) Math.min(safeRequested, safeRemaining);
        return new UnitSplit(covered, safeRequested - covered);
    }

    /**
     * Splits a gross line/decision amount proportionally to a unit split.
     * Computes the COVERED share directly from covered/total units and
     * rounds once; the refused share is always derived by the caller
     * subtracting this from the original total -- never rounded
     * independently -- so covered + refused equals the original amount
     * exactly, with no reconciliation step needed.
     */
    public static BigDecimal coveredAmountFor(BigDecimal lineTotal, UnitSplit split) {
        BigDecimal safeTotal = lineTotal == null ? BigDecimal.ZERO : lineTotal;
        int totalUnits = split.coveredUnits() + split.refusedUnits();
        if (totalUnits <= 0 || split.coveredUnits() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (split.refusedUnits() <= 0) {
            return safeTotal.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal coveredFraction = BigDecimal.valueOf(split.coveredUnits())
                .divide(BigDecimal.valueOf(totalUnits), 6, RoundingMode.HALF_UP);
        return safeTotal.multiply(coveredFraction).setScale(2, RoundingMode.HALF_UP);
    }
}
