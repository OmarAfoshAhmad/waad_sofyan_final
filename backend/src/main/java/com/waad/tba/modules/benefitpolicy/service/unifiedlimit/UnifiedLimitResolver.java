package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.benefitpolicy.service.DivisibleLimitSplitter;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitDecision.LimitAxis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * P1.4.2/P1.5.0: an isolated skeleton. Takes already-read, already-reduced
 * bucket balances ({@link BucketLimitSnapshot}) and decides what may be
 * consumed -- no repository, no Spring bean, no live caller wired to it yet
 * (P1.4.0: the capability is built before any of CoverageDecisionService /
 * ClaimFinancialAdjudicationService / PreAuthorizationDecisionBuilder is
 * migrated to it, which happens in P1.12).
 *
 * P1.5.0 ownership boundary (reviewed and moved on purpose): {@code remaining}
 * on every {@link BucketLimitSnapshot} is computed ONCE, by whoever builds
 * that snapshot (the future adapter, backed by {@code LimitBalanceReader} --
 * the one live reader that already sees RESERVED for AMOUNT/TIMES). This
 * resolver never recomputes it and never re-applies a
 * {@link ReservationEvaluationMode} formula itself -- it only takes the
 * tightest already-computed {@code remaining} across buckets sharing an
 * axis. One owner for that number, not two.
 *
 * Deliberately knows nothing about money ownership (P1.3 §0): the only
 * number that leaves this class toward a financial engine is
 * {@code bindingAvailableAmount}.
 */
public final class UnifiedLimitResolver {

    private UnifiedLimitResolver() {
    }

    public static UnifiedLimitDecision resolve(UnifiedLimitInput input, List<BucketLimitSnapshot> snapshots) {
        // BLOCKED first, before any balance is read at all (P1.3 §2): a
        // structural failure is never allowed to surface as an ordinary
        // exhausted-limit result. The adapter that builds `snapshots` is
        // expected to have already turned any BUCKET_POLICY_MISMATCH
        // exception from ApplicableLimitResolver/EffectiveLimitResolver into
        // exactly this same check -- this guard is the resolver's own,
        // independent proof that it never trusts a mismatched snapshot.
        for (BucketLimitSnapshot snapshot : snapshots) {
            if (!input.policyId().equals(snapshot.owningPolicyId())) {
                return UnifiedLimitDecision.blocked(input.ruleId(), List.of(
                        "BUCKET_POLICY_MISMATCH: bucket id=" + snapshot.bucketId()
                                + " belongs to policy id=" + snapshot.owningPolicyId()
                                + ", not the requested policy id=" + input.policyId()));
            }
        }

        List<Long> appliedBucketIds = snapshots.stream().map(BucketLimitSnapshot::bucketId).distinct().toList();
        List<String> reasons = new ArrayList<>();

        LimitAxis amount = reduceAxis(snapshots, LimitAxisType.AMOUNT);
        LimitAxis times = reduceAxis(snapshots, LimitAxisType.TIMES);
        LimitAxis days = reduceAxis(snapshots, LimitAxisType.DAYS);

        // ── quantity ──────────────────────────────────────────────────
        // Two genuinely different shapes, not one formula with an edge case:
        //
        // (a) No TIMES axis configured at all (G1/CM4): there is no "unit" --
        //     the service is priced and capped as a continuous amount. The
        //     occurrence count itself is never refused; only money is.
        // (b) One or more TIMES axes exist (G2/G4, and CM1-CM3 for more than
        //     one bucket): a unit has a real, fixed price ONLY when the
        //     amount ceiling constraining it lives on the SAME bucket --
        //     that is the one case where "money can only buy whole units" is
        //     actually true (G4: one bucket sells a priced session). A
        //     SEPARATE/general/parent amount ceiling on a DIFFERENT bucket
        //     is not a per-unit price relationship at all (a plain policy-
        //     wide cap coexisting with an unrelated occurrence limit) --
        //     found the hard way while wiring this into CoverageEngineService:
        //     the old per-bucket combinedCapacity used ONE globally-shared
        //     amount capacity, which silently turned "50 left on the annual
        //     ceiling" into "afford zero whole $100 sessions" and refused a
        //     line the old computeBucketUsage always partially approved.
        //     Each TIMES-configuring bucket therefore only ever divides by
        //     an amount snapshot sharing its OWN bucketId; any other amount
        //     ceiling is applied afterward, as a continuous cap on the
        //     already-decided quantity's money -- never a further quantity cut.
        int approvedQuantity;
        int refusedQuantity;
        BindingConstraintType quantityBindingType;
        Long quantityBindingBucketId = null;
        List<BucketLimitSnapshot> timesSnapshots = snapshots.stream()
                .filter(s -> s.limitType() == LimitAxisType.TIMES && s.configured() != null).toList();
        boolean occurrenceDimensionExists = !timesSnapshots.isEmpty();

        if (!occurrenceDimensionExists) {
            approvedQuantity = input.requestedQuantity();
            refusedQuantity = 0;
            quantityBindingType = BindingConstraintType.NONE;
        } else {
            approvedQuantity = input.requestedQuantity();
            quantityBindingType = BindingConstraintType.NONE;
            for (BucketLimitSnapshot ts : timesSnapshots) {
                int unitsAffordableByThisBucket = ts.remaining().max(BigDecimal.ZERO).intValue();
                BucketLimitSnapshot sameBucketAmount = snapshots.stream()
                        .filter(s -> s.limitType() == LimitAxisType.AMOUNT
                                && java.util.Objects.equals(s.bucketId(), ts.bucketId()))
                        .findFirst().orElse(null);
                int unitsAffordableBySameBucketAmount = sameBucketAmount == null
                        || input.effectiveUnitPrice() == null || input.effectiveUnitPrice().signum() <= 0
                        ? Integer.MAX_VALUE
                        : sameBucketAmount.remaining().max(BigDecimal.ZERO)
                                .divideToIntegralValue(input.effectiveUnitPrice()).intValue();
                int combinedCapacity = Math.min(unitsAffordableByThisBucket, unitsAffordableBySameBucketAmount);
                boolean divisible = ts.countingMethod() == CountingMethod.EACH_UNIT;
                int approvedByThisBucket = !divisible
                        ? (input.requestedQuantity() <= combinedCapacity ? input.requestedQuantity() : 0)
                        : Math.min(input.requestedQuantity(), combinedCapacity);
                if (approvedByThisBucket < approvedQuantity) {
                    approvedQuantity = approvedByThisBucket;
                    quantityBindingBucketId = ts.bucketId();
                    quantityBindingType = unitsAffordableBySameBucketAmount < unitsAffordableByThisBucket
                            ? BindingConstraintType.AMOUNT : BindingConstraintType.TIMES;
                }
            }
            refusedQuantity = input.requestedQuantity() - approvedQuantity;
        }

        // ── days: always atomic (P1.3 §1.3, G3). No reservation concept
        // exists for days in this product at all (no sumReservedDays query
        // anywhere) -- the snapshot's own `remaining` already reflects that. ──
        int daysAvailable = days.remaining() == null ? Integer.MAX_VALUE : days.remaining().max(BigDecimal.ZERO).intValue();
        boolean daysFit = input.requestedDays() <= daysAvailable;
        int approvedDays = daysFit ? input.requestedDays() : 0;
        int refusedDays = input.requestedDays() - approvedDays;

        // ── money for whatever quantity was just decided, BEFORE any
        // separate/general amount ceiling is applied ──
        BigDecimal moneyForApprovedQuantity;
        if (!occurrenceDimensionExists || (quantityBindingType == BindingConstraintType.NONE
                && approvedQuantity >= input.requestedQuantity())) {
            moneyForApprovedQuantity = scale2(input.eligibleAmount());
        } else {
            var split = new DivisibleLimitSplitter.UnitSplit(approvedQuantity, refusedQuantity);
            moneyForApprovedQuantity = DivisibleLimitSplitter.coveredAmountFor(scale2(input.eligibleAmount()), split);
        }

        // ── the GLOBAL amount ceiling (tightest across every AMOUNT-configured
        // bucket, same-bucket ones included) applied as a final continuous cap.
        // A same-bucket ceiling was already reflected in combinedCapacity above,
        // so this is a no-op there (moneyForApprovedQuantity already fits); a
        // separate/general ceiling binds here for the first time. ──
        BigDecimal moneyAfterGeneralCeiling = amount.configured() == null
                ? moneyForApprovedQuantity
                : moneyForApprovedQuantity.min(scale2(amount.remaining().max(BigDecimal.ZERO)));
        boolean generalAmountCeilingBinds = amount.configured() != null
                && moneyAfterGeneralCeiling.compareTo(moneyForApprovedQuantity) < 0;

        BindingConstraintType bindingConstraintTypeBeforeDays = quantityBindingType != BindingConstraintType.NONE
                ? quantityBindingType
                : (generalAmountCeilingBinds ? BindingConstraintType.AMOUNT : BindingConstraintType.NONE);

        // ── binding: quantity/amount constraint wins unless only days bound (simple first cut; revisit if a real case needs both at once) ──
        BindingConstraintType bindingConstraintType = bindingConstraintTypeBeforeDays != BindingConstraintType.NONE
                ? bindingConstraintTypeBeforeDays
                : (refusedDays > 0 ? BindingConstraintType.DAYS : BindingConstraintType.NONE);

        // P1.5.2: for a quantity-bound decision, use the bucket whose OWN
        // calculation was actually tightest (tracked above), not merely the
        // first applied bucket -- a general-ceiling or DAYS-bound decision
        // still falls back to that placeholder since neither is tracked
        // per-bucket here.
        Long bindingBucketId = bindingConstraintType == BindingConstraintType.NONE ? null
                : bindingConstraintType == quantityBindingType && quantityBindingBucketId != null
                        ? quantityBindingBucketId
                        : appliedBucketIds.isEmpty() ? null : appliedBucketIds.get(0);

        // ── the ONE money number that reaches WaadFinancialEngine (P1.3 rule 3) ──
        BigDecimal bindingAvailableAmount = bindingConstraintType == BindingConstraintType.DAYS
                ? (approvedDays > 0 ? scale2(input.eligibleAmount()) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                : moneyAfterGeneralCeiling;

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
     * P1.5.0: pure aggregation, no arithmetic on balances. Filters the flat
     * snapshot list to one axis and takes the row with the tightest
     * already-computed {@code remaining} -- never recomputes it from
     * configured/committed/reserved, which would create a second owner of
     * that number (exactly the bug this step was reviewed to prevent).
     * An axis nobody configures has no matching row at all -- absent, not zero.
     */
    private static LimitAxis reduceAxis(List<BucketLimitSnapshot> snapshots, LimitAxisType axisType) {
        LimitAxis tightest = null;
        for (BucketLimitSnapshot snapshot : snapshots) {
            if (snapshot.limitType() != axisType || snapshot.configured() == null) continue;

            LimitAxis candidate = new LimitAxis(
                    snapshot.configured(), orZero(snapshot.committed()),
                    orZero(snapshot.activeReserved()), snapshot.remaining());
            if (tightest == null || candidate.remaining().compareTo(tightest.remaining()) < 0) {
                tightest = candidate;
            }
        }
        return tightest == null ? LimitAxis.unconfigured() : tightest;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal scale2(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
