package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.benefitpolicy.service.DivisibleLimitSplitter;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitDecision.LimitAxis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * P1.4.2: an isolated skeleton. Takes already-read bucket data
 * ({@link BucketLimitSnapshot}) and decides what may be consumed -- no
 * repository, no Spring bean, no live caller wired to it yet (P1.4.0: the
 * capability is built before any of CoverageDecisionService /
 * ClaimFinancialAdjudicationService / PreAuthorizationDecisionBuilder is
 * migrated to it, which happens in P1.12).
 *
 * Deliberately knows nothing about money ownership (P1.3 §0): the only
 * number that leaves this class toward a financial engine is
 * {@code bindingAvailableAmount}.
 */
public final class UnifiedLimitResolver {

    private UnifiedLimitResolver() {
    }

    public static UnifiedLimitDecision resolve(UnifiedLimitInput input, List<BucketLimitSnapshot> buckets) {
        // BLOCKED first, before any balance is computed at all (P1.3 §2):
        // a structural failure is never allowed to surface as an ordinary
        // exhausted-limit result.
        for (BucketLimitSnapshot bucket : buckets) {
            if (!input.policyId().equals(bucket.owningPolicyId())) {
                return UnifiedLimitDecision.blocked(input.ruleId(), List.of(
                        "BUCKET_POLICY_MISMATCH: bucket id=" + bucket.bucketId()
                                + " belongs to policy id=" + bucket.owningPolicyId()
                                + ", not the requested policy id=" + input.policyId()));
            }
        }

        List<Long> appliedBucketIds = buckets.stream().map(BucketLimitSnapshot::bucketId).toList();
        List<String> reasons = new ArrayList<>();

        LimitAxis amount = reduceAxis(buckets, input.reservationMode(),
                BucketLimitSnapshot::amountConfigured, BucketLimitSnapshot::amountCommitted,
                BucketLimitSnapshot::amountReserved, BucketLimitSnapshot::amountOwnActiveReservation);
        LimitAxis times = reduceAxis(buckets, input.reservationMode(),
                b -> asBigDecimal(b.timesConfigured()), b -> asBigDecimal(b.timesCommitted()),
                b -> asBigDecimal(b.timesReserved()), b -> asBigDecimal(b.timesOwnActiveReservation()));
        LimitAxis days = reduceAxis(buckets, input.reservationMode(),
                b -> asBigDecimal(b.daysConfigured()), b -> asBigDecimal(b.daysCommitted()),
                b -> asBigDecimal(b.daysReserved()), b -> asBigDecimal(b.daysOwnActiveReservation()));

        // ── quantity ──────────────────────────────────────────────────
        // Two genuinely different shapes, not one formula with an edge case:
        //
        // (a) No TIMES axis configured at all (G1): there is no "unit" --
        //     the service is priced and capped as a continuous amount. The
        //     occurrence count itself is never refused; only money is.
        // (b) A TIMES axis exists (G2/G4): a unit has a real, fixed price,
        //     so AMOUNT can only ever afford WHOLE units -- 2.5 sessions is
        //     not a purchasable quantity (G4).
        int approvedQuantity;
        int refusedQuantity;
        BindingConstraintType quantityBindingType;
        boolean occurrenceDimensionExists = times.configured() != null;

        if (!occurrenceDimensionExists) {
            approvedQuantity = input.requestedQuantity();
            refusedQuantity = 0;
            quantityBindingType = amount.configured() != null
                    && amount.remaining().compareTo(scale2(input.eligibleAmount())) < 0
                    ? BindingConstraintType.AMOUNT : BindingConstraintType.NONE;
        } else {
            int unitsAffordableByTimes = times.remaining().max(BigDecimal.ZERO).intValue();
            int unitsAffordableByAmount = amount.remaining() == null || input.effectiveUnitPrice() == null
                    || input.effectiveUnitPrice().signum() <= 0
                    ? Integer.MAX_VALUE
                    : amount.remaining().max(BigDecimal.ZERO)
                            .divideToIntegralValue(input.effectiveUnitPrice()).intValue();

            boolean divisible = input.countingMethod() == CountingMethod.EACH_UNIT;
            if (!divisible) {
                // Atomic occurrence (EACH_LINE/PER_VISIT/PER_DAY): either the
                // whole request fits under both axes, or none of it is approved.
                boolean fits = input.requestedQuantity() <= unitsAffordableByTimes
                        && input.requestedQuantity() <= unitsAffordableByAmount;
                approvedQuantity = fits ? input.requestedQuantity() : 0;
                quantityBindingType = fits ? BindingConstraintType.NONE
                        : (unitsAffordableByAmount < unitsAffordableByTimes ? BindingConstraintType.AMOUNT : BindingConstraintType.TIMES);
            } else {
                int tightest = Math.min(input.requestedQuantity(), Math.min(unitsAffordableByTimes, unitsAffordableByAmount));
                approvedQuantity = Math.max(0, tightest);
                quantityBindingType = approvedQuantity >= input.requestedQuantity() ? BindingConstraintType.NONE
                        : (unitsAffordableByAmount <= unitsAffordableByTimes ? BindingConstraintType.AMOUNT : BindingConstraintType.TIMES);
            }
            refusedQuantity = input.requestedQuantity() - approvedQuantity;
        }

        // ── days: always atomic (P1.3 §1.3, G3) ──────────────────────
        int daysAvailable = days.remaining() == null ? Integer.MAX_VALUE : days.remaining().max(BigDecimal.ZERO).intValue();
        boolean daysFit = input.requestedDays() <= daysAvailable;
        int approvedDays = daysFit ? input.requestedDays() : 0;
        int refusedDays = input.requestedDays() - approvedDays;

        // ── binding: quantity/amount constraint wins unless only days bound (simple first cut; revisit if a real case needs both at once) ──
        BindingConstraintType bindingConstraintType = quantityBindingType != BindingConstraintType.NONE
                ? quantityBindingType
                : (refusedDays > 0 ? BindingConstraintType.DAYS : BindingConstraintType.NONE);

        Long bindingBucketId = bindingConstraintType == BindingConstraintType.NONE ? null
                : buckets.isEmpty() ? null : buckets.get(0).bucketId();

        // ── the ONE money number that reaches WaadFinancialEngine (P1.3 rule 3) ──
        BigDecimal bindingAvailableAmount;
        if (bindingConstraintType == BindingConstraintType.DAYS) {
            bindingAvailableAmount = approvedDays > 0 ? scale2(input.eligibleAmount()) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else if (!occurrenceDimensionExists) {
            // G1: no unit to split -- AMOUNT caps the money continuously.
            bindingAvailableAmount = amount.configured() == null
                    ? scale2(input.eligibleAmount())
                    : scale2(input.eligibleAmount()).min(scale2(amount.remaining().max(BigDecimal.ZERO)));
        } else if (quantityBindingType == BindingConstraintType.NONE) {
            bindingAvailableAmount = scale2(input.eligibleAmount());
        } else {
            var split = new DivisibleLimitSplitter.UnitSplit(approvedQuantity, refusedQuantity);
            bindingAvailableAmount = DivisibleLimitSplitter.coveredAmountFor(scale2(input.eligibleAmount()), split);
        }

        // Whether anything was actually refused. For the no-occurrence-dimension
        // shape (G1) refusedQuantity is always 0 by construction (the count
        // itself is never limited there) -- the money itself is what was
        // partially/fully refused, so it must enter this check too.
        BigDecimal requestedEligible = scale2(input.eligibleAmount());
        boolean moneyFullyApproved = occurrenceDimensionExists
                || bindingConstraintType != BindingConstraintType.AMOUNT
                || bindingAvailableAmount.compareTo(requestedEligible) >= 0;
        boolean moneyFullyRefused = !occurrenceDimensionExists
                && bindingConstraintType == BindingConstraintType.AMOUNT
                && bindingAvailableAmount.signum() == 0;

        boolean anyAxisConfigured = amount.configured() != null || times.configured() != null || days.configured() != null;
        UnifiedLimitStatus status;
        if (!anyAxisConfigured) {
            status = UnifiedLimitStatus.UNLIMITED;
        } else if (refusedQuantity == 0 && refusedDays == 0 && moneyFullyApproved) {
            status = UnifiedLimitStatus.LIMITED;
        } else if ((input.requestedQuantity() > 0 && approvedQuantity == 0)
                || (input.requestedDays() > 0 && approvedDays == 0 && refusedDays > 0 && quantityBindingType == BindingConstraintType.NONE)
                || moneyFullyRefused) {
            status = UnifiedLimitStatus.EXHAUSTED;
        } else {
            status = UnifiedLimitStatus.PARTIAL;
        }

        if (bindingConstraintType != BindingConstraintType.NONE) {
            reasons.add(bindingConstraintType + "_BINDS bucket=" + bindingBucketId
                    + " approvedQuantity=" + approvedQuantity + " approvedDays=" + approvedDays);
        }

        return new UnifiedLimitDecision(
                input.ruleId(), appliedBucketIds, reasons,
                input.requestedQuantity(), approvedQuantity, refusedQuantity,
                input.requestedDays(), approvedDays, refusedDays,
                amount, times, days,
                bindingConstraintType, bindingBucketId, bindingAvailableAmount, status);
    }

    /**
     * P1.3 §1.2 / §3.1: one reduction, reused verbatim for AMOUNT/TIMES/DAYS.
     * The most restrictive bucket's remaining balance wins (min across all
     * buckets that configure this axis); axes nobody configures are simply
     * absent (null), never zero.
     */
    private static LimitAxis reduceAxis(
            List<BucketLimitSnapshot> buckets,
            ReservationEvaluationMode mode,
            java.util.function.Function<BucketLimitSnapshot, BigDecimal> configuredOf,
            java.util.function.Function<BucketLimitSnapshot, BigDecimal> committedOf,
            java.util.function.Function<BucketLimitSnapshot, BigDecimal> reservedOf,
            java.util.function.Function<BucketLimitSnapshot, BigDecimal> ownReservedOf) {

        LimitAxis tightest = null;
        for (BucketLimitSnapshot bucket : buckets) {
            BigDecimal configured = configuredOf.apply(bucket);
            if (configured == null) continue; // this bucket sets no limit of this kind

            BigDecimal committed = orZero(committedOf.apply(bucket));
            BigDecimal reserved = orZero(reservedOf.apply(bucket));
            BigDecimal ownReserved = orZero(ownReservedOf.apply(bucket));

            BigDecimal actualRemaining = configured.subtract(committed);
            BigDecimal remaining = switch (mode) {
                case NORMAL, PREAUTH_RESERVATION -> actualRemaining.subtract(reserved);
                case PREAUTHORIZED_CLAIM -> actualRemaining.min(
                        actualRemaining.subtract(reserved).add(ownReserved));
            };

            LimitAxis candidate = new LimitAxis(configured, committed, reserved, remaining);
            if (tightest == null || remaining.compareTo(tightest.remaining()) < 0) {
                tightest = candidate;
            }
        }
        return tightest == null ? LimitAxis.unconfigured() : tightest;
    }

    private static BigDecimal asBigDecimal(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal scale2(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
