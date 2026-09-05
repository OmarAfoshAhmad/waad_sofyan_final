package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.providercontract.enums.EncounterType;

/**
 * P1.4.3/P1.5.0/P1.5.2: proves UnifiedLimitResolver -- the real Java
 * implementation, not the pure-formula fixtures -- reproduces every one of
 * G1-G8 exactly as fixed in P1_UNIFIED_LIMIT_DECISION_CONTRACT.md §4 and
 * P1_4_UNIFIED_LIMIT_RESOLVER_DESIGN.md. Both test files stay: the golden
 * formula tests guard the law, this file guards this specific
 * implementation of it.
 *
 * P1.5.0 ownership change: {@code remaining} on each fixture below is
 * computed HERE, by hand, exactly the way the future adapter will compute
 * it from LimitBalanceReader -- the resolver itself never recomputes it
 * from configured/committed/reserved anymore (that recomputation was
 * reviewed out in P1.5.0 to avoid a second owner of the same number).
 *
 * P1.5.2 (P1.3 Amendment #1): countingMethod now lives on each
 * {@link BucketLimitSnapshot}, not on {@link UnifiedLimitInput} -- proven
 * from BenefitLimitBucket.countingMethod being a bucket-level column and
 * CoverageEngineService.computeBucketUsage reading it per bucket. G2/G4/G5/
 * G7/G8 below pass their counting method into {@code timesAxis(...)} now,
 * not into {@code UnifiedLimitInput}.
 *
 * Isolated skeleton (P1.4.0/P1.4.2): no Spring context, no repository, no
 * live caller.
 */
class UnifiedLimitResolverTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 1);
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 12, 31);

    private static UnifiedLimitInput lineInput(Long ruleId, int requestedQuantity, int requestedDays,
            BigDecimal effectiveUnitPrice, BigDecimal eligibleAmount, Long excludeClaimId,
            ReservationEvaluationMode mode, Long preAuthId, Long assignmentId) {
        return new UnifiedLimitInput(700L, ruleId, 500L, DATE, EncounterType.OUTPATIENT,
                requestedQuantity, requestedDays, effectiveUnitPrice, eligibleAmount,
                excludeClaimId, mode, preAuthId, assignmentId);
    }

    private static UnifiedLimitInput normalInput(Long ruleId, int requestedQuantity, int requestedDays,
            BigDecimal effectiveUnitPrice, BigDecimal eligibleAmount) {
        return lineInput(ruleId, requestedQuantity, requestedDays, effectiveUnitPrice, eligibleAmount,
                null, ReservationEvaluationMode.NORMAL, null, null);
    }

    /** AMOUNT/DAYS axes never consult countingMethod -- EACH_LINE here is inert filler, not a claim about the field's meaning. */
    private static BucketLimitSnapshot amountAxis(long bucketId, long owningPolicyId,
            String configured, String committed, String reserved, String remaining) {
        return new BucketLimitSnapshot(bucketId, owningPolicyId, LimitAxisType.AMOUNT, CountingMethod.EACH_LINE,
                new BigDecimal(configured), new BigDecimal(committed), new BigDecimal(reserved),
                new BigDecimal(remaining), PERIOD_START, PERIOD_END);
    }

    private static BucketLimitSnapshot timesAxis(long bucketId, long owningPolicyId, CountingMethod countingMethod,
            int configured, int committed, int reserved, int remaining) {
        return new BucketLimitSnapshot(bucketId, owningPolicyId, LimitAxisType.TIMES, countingMethod,
                BigDecimal.valueOf(configured), BigDecimal.valueOf(committed), BigDecimal.valueOf(reserved),
                BigDecimal.valueOf(remaining), PERIOD_START, PERIOD_END);
    }

    private static BucketLimitSnapshot daysAxis(long bucketId, long owningPolicyId,
            int configured, int committed, int remaining) {
        // No reservation concept exists for days anywhere in the codebase
        // (no sumReservedDays query) -- activeReserved is always zero here,
        // not by coincidence. countingMethod is inert for DAYS (always atomic).
        return new BucketLimitSnapshot(bucketId, owningPolicyId, LimitAxisType.DAYS, CountingMethod.PER_DAY,
                BigDecimal.valueOf(configured), BigDecimal.valueOf(committed), BigDecimal.ZERO,
                BigDecimal.valueOf(remaining), PERIOD_START, PERIOD_END);
    }

    @Test
    @DisplayName("G1 — amount only, no occurrence dimension: the money itself is partially refused")
    void g1AmountOnly() {
        UnifiedLimitInput in = normalInput(900L, 1, 0, new BigDecimal("1000.00"), new BigDecimal("1000.00"));
        // configured=1000, committed=400, reserved=0 -> remaining=600 (NORMAL)
        BucketLimitSnapshot snapshot = amountAxis(931L, 700L, "1000.00", "400.00", "0.00", "600.00");

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of(snapshot));

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.PARTIAL);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.AMOUNT);
        assertThat(d.bindingAvailableAmount()).isEqualByComparingTo("600.00");
        assertThat(d.approvedQuantity()).isEqualTo(1); // the count itself is never refused here
    }

    @Test
    @DisplayName("G2 — the Physio gate: 3 requested, 2 remaining, EACH_UNIT divisible")
    void g2TimesPartialAcceptance() {
        UnifiedLimitInput in = normalInput(901L, 3, 0, new BigDecimal("100.00"), new BigDecimal("300.00"));
        // configured=20, committed=18, reserved=0 -> remaining=2 (NORMAL)
        BucketLimitSnapshot snapshot = timesAxis(932L, 700L, CountingMethod.EACH_UNIT, 20, 18, 0, 2);

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of(snapshot));

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.PARTIAL);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.TIMES);
        assertThat(d.approvedQuantity()).isEqualTo(2);
        assertThat(d.refusedQuantity()).isEqualTo(1);
        assertThat(d.bindingAvailableAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("G3 — days are atomic: one requested day, zero remaining, refused whole")
    void g3DaysAreAtomic() {
        UnifiedLimitInput in = lineInput(902L, 1, 1, new BigDecimal("300.00"), new BigDecimal("300.00"),
                null, ReservationEvaluationMode.NORMAL, null, null);
        // configured=10, committed=10 -> remaining=0
        BucketLimitSnapshot snapshot = daysAxis(933L, 700L, 10, 10, 0);

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of(snapshot));

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.EXHAUSTED);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.DAYS);
        assertThat(d.approvedDays()).isZero();
        assertThat(d.refusedDays()).isEqualTo(1);
        assertThat(d.bindingAvailableAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("G4 — amount and times bind together: whole-unit constraint wins over the fractional one")
    void g4AmountAndTimesBindTogether() {
        UnifiedLimitInput in = normalInput(903L, 5, 0, new BigDecimal("100.00"), new BigDecimal("500.00"));
        BucketLimitSnapshot amountSnapshot = amountAxis(934L, 700L, "250.00", "0.00", "0.00", "250.00"); // -> 2 whole units
        BucketLimitSnapshot timesSnapshot = timesAxis(934L, 700L, CountingMethod.EACH_UNIT, 20, 16, 0, 4);

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of(amountSnapshot, timesSnapshot));

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.PARTIAL);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.AMOUNT);
        assertThat(d.approvedQuantity()).isEqualTo(2);
        assertThat(d.bindingAvailableAmount()).isEqualByComparingTo("200.00");
        assertThat(d.bindingAvailableAmount()).isNotEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("G5 — shared bucket respects an active reservation from another approval")
    void g5SharedBucketRespectsReservation() {
        UnifiedLimitInput in = normalInput(904L, 8, 0, new BigDecimal("50.00"), new BigDecimal("400.00"));
        // configured=20, committed=10, reserved=4 -> remaining=6 (NORMAL)
        BucketLimitSnapshot snapshot = timesAxis(935L, 700L, CountingMethod.EACH_UNIT, 20, 10, 4, 6);

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of(snapshot));

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.PARTIAL);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.TIMES);
        assertThat(d.approvedQuantity()).isEqualTo(6);
        assertThat(d.refusedQuantity()).isEqualTo(2);
        assertThat(d.times().reserved()).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("G6 — a bucket owned by a different policy blocks the decision structurally")
    void g6PolicyOwnershipMismatchBlocks() {
        UnifiedLimitInput in = normalInput(905L, 1, 0, new BigDecimal("100.00"), new BigDecimal("100.00"));
        BucketLimitSnapshot foreignSnapshot = amountAxis(936L, 701L, // belongs to a DIFFERENT policy than input.policyId()=700
                "100.00", "0.00", "0.00", "100.00");

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of(foreignSnapshot));

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.BLOCKED);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.NONE);
        assertThat(d.bindingAvailableAmount()).isNull(); // never zero -- not computed at all
        assertThat(d.decisionReasons().get(0)).contains("BUCKET_POLICY_MISMATCH");
    }

    @Test
    @DisplayName("G7 — a claim owning part of the reservation sees its own hold returned, capped at actual remaining")
    void g7PreauthorizedClaimOwnsItsReservation() {
        UnifiedLimitInput in = lineInput(906L, 8, 0, new BigDecimal("50.00"), new BigDecimal("400.00"),
                123L, ReservationEvaluationMode.PREAUTHORIZED_CLAIM, 50L, 60L);
        // Limit=20, committed=10, reserved total=6, of which THIS preauth owns 4
        // -> actualRemaining=10, reservableAvailable=4, availableForThisClaim=min(10, 4+4)=8
        // `remaining` here is exactly what a PREAUTHORIZED_CLAIM-mode adapter
        // read would compute -- the resolver does not know or care which
        // formula produced it.
        BucketLimitSnapshot snapshot = timesAxis(937L, 700L, CountingMethod.EACH_UNIT, 20, 10, 6, 8);

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of(snapshot));

        assertThat(d.times().remaining()).isEqualByComparingTo("8");
        assertThat(d.approvedQuantity()).isEqualTo(8);
        assertThat(d.refusedQuantity()).isZero();
        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.LIMITED);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.NONE);
    }

    @Test
    @DisplayName("G8 — an atomic counting method (PER_VISIT) refuses the whole occurrence, never splits it, even though a TIMES axis exists")
    void g8AtomicCountingMethodWithOccurrenceDimensionRefusesWhole() {
        // Unlike G2 (EACH_UNIT), this bucket DOES configure a TIMES axis, but
        // the method is PER_VISIT -- indivisible. Requesting 3 against a
        // remaining of 2 must refuse everything, not approve 2.
        UnifiedLimitInput in = normalInput(908L, 3, 0, new BigDecimal("100.00"), new BigDecimal("300.00"));
        BucketLimitSnapshot snapshot = timesAxis(938L, 700L, CountingMethod.PER_VISIT, 20, 18, 0, 2);

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of(snapshot));

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.EXHAUSTED);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.TIMES);
        assertThat(d.approvedQuantity()).isZero();
        assertThat(d.refusedQuantity()).isEqualTo(3);
        assertThat(d.bindingAvailableAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("no buckets at all -> UNLIMITED, full request approved")
    void unlimitedWhenNoBucketApplies() {
        UnifiedLimitInput in = normalInput(907L, 3, 0, new BigDecimal("100.00"), new BigDecimal("300.00"));

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of());

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.UNLIMITED);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.NONE);
        assertThat(d.approvedQuantity()).isEqualTo(3);
        assertThat(d.bindingAvailableAmount()).isEqualByComparingTo("300.00");
    }

    // ── P1.5.2: CM1-CM5, bucket-level countingMethod ─────────────────────

    @Test
    @DisplayName("CM1 — one divisible (EACH_UNIT) bucket alone: requested 3, remaining 2 -> approved 2 (G2's shape, named)")
    void cm1DivisibleBucketAlonePartiallyApproves() {
        UnifiedLimitInput in = normalInput(909L, 3, 0, new BigDecimal("100.00"), new BigDecimal("300.00"));
        BucketLimitSnapshot snapshot = timesAxis(939L, 700L, CountingMethod.EACH_UNIT, 20, 18, 0, 2);

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of(snapshot));

        assertThat(d.approvedQuantity()).isEqualTo(2);
        assertThat(d.refusedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("CM2 — one atomic (PER_VISIT) bucket alone: requested 3, remaining 2 -> approved 0 (G8's shape, named)")
    void cm2AtomicBucketAloneRefusesWhole() {
        UnifiedLimitInput in = normalInput(910L, 3, 0, new BigDecimal("100.00"), new BigDecimal("300.00"));
        BucketLimitSnapshot snapshot = timesAxis(940L, 700L, CountingMethod.PER_VISIT, 20, 18, 0, 2);

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of(snapshot));

        assertThat(d.approvedQuantity()).isZero();
        assertThat(d.refusedQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("CM3 — two TIMES buckets, DIFFERENT counting methods, same line: each uses its OWN method, never the other's")
    void cm3DifferentBucketsUseTheirOwnCountingMethodIndependently() {
        // No AMOUNT axis at all -- isolates this case to the TIMES/TIMES
        // interaction the review specifically asked to prove.
        UnifiedLimitInput in = normalInput(911L, 3, 0, null, new BigDecimal("300.00"));
        BucketLimitSnapshot divisibleBucket = timesAxis(941L, 700L, CountingMethod.EACH_UNIT, 10, 8, 0, 2);
        BucketLimitSnapshot atomicBucket = timesAxis(942L, 700L, CountingMethod.PER_VISIT, 10, 8, 0, 2);

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of(divisibleBucket, atomicBucket));

        // Bucket A alone (divisible) would allow 2; bucket B alone (atomic)
        // allows 0 since 3 does not fit in its own remaining of 2. Neither
        // method leaks into the other's calculation -- only the two
        // ALREADY-COMPUTED results are compared, and the tightest (0) wins.
        assertThat(d.approvedQuantity()).isZero();
        assertThat(d.refusedQuantity()).isEqualTo(3);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.TIMES);
    }

    @Test
    @DisplayName("CM4 — AMOUNT only, no TIMES snapshot at all: a bucket's countingMethod never fragments a continuous money ceiling")
    void cm4AmountOnlyNeverConsultsCountingMethod() {
        UnifiedLimitInput in = normalInput(912L, 1, 0, new BigDecimal("1000.00"), new BigDecimal("1000.00"));
        BucketLimitSnapshot snapshot = amountAxis(943L, 700L, "1000.00", "400.00", "0.00", "600.00");

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of(snapshot));

        assertThat(d.bindingAvailableAmount()).isEqualByComparingTo("600.00");
        assertThat(d.approvedQuantity()).isEqualTo(1); // the count itself is never touched here, exactly like G1
    }
}
