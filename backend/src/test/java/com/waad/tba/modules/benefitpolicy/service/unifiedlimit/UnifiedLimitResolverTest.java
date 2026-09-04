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
 * P1.4.3: proves UnifiedLimitResolver -- the real Java implementation, not
 * the pure-formula fixtures -- reproduces every one of G1-G7 exactly as
 * fixed in P1_UNIFIED_LIMIT_DECISION_CONTRACT.md §4 and
 * P1_4_UNIFIED_LIMIT_RESOLVER_DESIGN.md. Both test files stay: the golden
 * formula tests guard the law, this file guards this specific
 * implementation of it.
 *
 * Isolated skeleton (P1.4.0/P1.4.2): no Spring context, no repository, no
 * live caller. Inputs are built by hand exactly as a future integration
 * step would assemble them from BenefitBucketLimitService/
 * EffectiveLimitResolver/ApplicableCountingLimitResolver.
 */
class UnifiedLimitResolverTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 1);

    @Test
    @DisplayName("G1 — amount only, no occurrence dimension: the money itself is partially refused")
    void g1AmountOnly() {
        UnifiedLimitInput in = new UnifiedLimitInput(
                700L, 900L, 500L, DATE, EncounterType.OUTPATIENT,
                1, 0, CountingMethod.EACH_LINE, new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                null, ReservationEvaluationMode.NORMAL, null, null);
        BucketLimitSnapshot bucket = new BucketLimitSnapshot(
                931L, 700L,
                new BigDecimal("1000.00"), new BigDecimal("400.00"), BigDecimal.ZERO, null,
                null, null, null, null,
                null, null, null, null);

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of(bucket));

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.PARTIAL);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.AMOUNT);
        assertThat(d.bindingAvailableAmount()).isEqualByComparingTo("600.00");
        assertThat(d.approvedQuantity()).isEqualTo(1); // the count itself is never refused here
    }

    @Test
    @DisplayName("G2 — the Physio gate: 3 requested, 2 remaining, EACH_UNIT divisible")
    void g2TimesPartialAcceptance() {
        UnifiedLimitInput in = new UnifiedLimitInput(
                700L, 901L, 500L, DATE, EncounterType.OUTPATIENT,
                3, 0, CountingMethod.EACH_UNIT, new BigDecimal("100.00"), new BigDecimal("300.00"),
                null, ReservationEvaluationMode.NORMAL, null, null);
        BucketLimitSnapshot bucket = new BucketLimitSnapshot(
                932L, 700L,
                null, null, null, null,
                20, 18, 0, null, // configured=20, committed=18 -> remaining 2
                null, null, null, null);

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of(bucket));

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.PARTIAL);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.TIMES);
        assertThat(d.approvedQuantity()).isEqualTo(2);
        assertThat(d.refusedQuantity()).isEqualTo(1);
        assertThat(d.bindingAvailableAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("G3 — days are atomic: one requested day, zero remaining, refused whole")
    void g3DaysAreAtomic() {
        UnifiedLimitInput in = new UnifiedLimitInput(
                700L, 902L, 500L, DATE, EncounterType.INPATIENT,
                1, 1, CountingMethod.PER_DAY, new BigDecimal("300.00"), new BigDecimal("300.00"),
                null, ReservationEvaluationMode.NORMAL, null, null);
        BucketLimitSnapshot bucket = new BucketLimitSnapshot(
                933L, 700L,
                null, null, null, null,
                null, null, null, null,
                10, 10, 0, null); // configured=10, committed=10 -> remaining 0

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of(bucket));

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.EXHAUSTED);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.DAYS);
        assertThat(d.approvedDays()).isZero();
        assertThat(d.refusedDays()).isEqualTo(1);
        assertThat(d.bindingAvailableAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("G4 — amount and times bind together: whole-unit constraint wins over the fractional one")
    void g4AmountAndTimesBindTogether() {
        UnifiedLimitInput in = new UnifiedLimitInput(
                700L, 903L, 500L, DATE, EncounterType.OUTPATIENT,
                5, 0, CountingMethod.EACH_UNIT, new BigDecimal("100.00"), new BigDecimal("500.00"),
                null, ReservationEvaluationMode.NORMAL, null, null);
        BucketLimitSnapshot bucket = new BucketLimitSnapshot(
                934L, 700L,
                new BigDecimal("250.00"), BigDecimal.ZERO, BigDecimal.ZERO, null, // amount remaining 250 -> 2 whole units
                20, 16, 0, null, // times remaining 4
                null, null, null, null);

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of(bucket));

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.PARTIAL);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.AMOUNT);
        assertThat(d.approvedQuantity()).isEqualTo(2);
        assertThat(d.bindingAvailableAmount()).isEqualByComparingTo("200.00");
        assertThat(d.bindingAvailableAmount()).isNotEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("G5 — shared bucket respects an active reservation from another approval")
    void g5SharedBucketRespectsReservation() {
        UnifiedLimitInput in = new UnifiedLimitInput(
                700L, 904L, 500L, DATE, EncounterType.OUTPATIENT,
                8, 0, CountingMethod.EACH_UNIT, new BigDecimal("50.00"), new BigDecimal("400.00"),
                null, ReservationEvaluationMode.NORMAL, null, null);
        BucketLimitSnapshot bucket = new BucketLimitSnapshot(
                935L, 700L,
                null, null, null, null,
                20, 10, 4, null, // 20 - 10 - 4 = 6 available
                null, null, null, null);

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of(bucket));

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.PARTIAL);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.TIMES);
        assertThat(d.approvedQuantity()).isEqualTo(6);
        assertThat(d.refusedQuantity()).isEqualTo(2);
        assertThat(d.times().reserved()).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("G6 — a bucket owned by a different policy blocks the decision structurally")
    void g6PolicyOwnershipMismatchBlocks() {
        UnifiedLimitInput in = new UnifiedLimitInput(
                700L, 905L, 500L, DATE, EncounterType.OUTPATIENT,
                1, 0, CountingMethod.EACH_LINE, new BigDecimal("100.00"), new BigDecimal("100.00"),
                null, ReservationEvaluationMode.NORMAL, null, null);
        BucketLimitSnapshot foreignBucket = new BucketLimitSnapshot(
                936L, 701L, // belongs to a DIFFERENT policy than input.policyId()=700
                new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO, null,
                null, null, null, null, null, null, null, null);

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of(foreignBucket));

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.BLOCKED);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.NONE);
        assertThat(d.bindingAvailableAmount()).isNull(); // never zero -- not computed at all
        assertThat(d.decisionReasons().get(0)).contains("BUCKET_POLICY_MISMATCH");
    }

    @Test
    @DisplayName("G7 — a claim owning part of the reservation sees its own hold returned, capped at actual remaining")
    void g7PreauthorizedClaimOwnsItsReservation() {
        UnifiedLimitInput in = new UnifiedLimitInput(
                700L, 906L, 500L, DATE, EncounterType.OUTPATIENT,
                8, 0, CountingMethod.EACH_UNIT, new BigDecimal("50.00"), new BigDecimal("400.00"),
                123L, ReservationEvaluationMode.PREAUTHORIZED_CLAIM, 50L, 60L);
        // Limit=20, committed=10, reserved total=6, of which THIS preauth owns 4
        // -> actualRemaining=10, reservableAvailable=4, availableForThisClaim=min(10, 4+4)=8
        BucketLimitSnapshot bucket = new BucketLimitSnapshot(
                937L, 700L,
                null, null, null, null,
                20, 10, 6, 4,
                null, null, null, null);

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of(bucket));

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
        UnifiedLimitInput in = new UnifiedLimitInput(
                700L, 908L, 500L, DATE, EncounterType.OUTPATIENT,
                3, 0, CountingMethod.PER_VISIT, new BigDecimal("100.00"), new BigDecimal("300.00"),
                null, ReservationEvaluationMode.NORMAL, null, null);
        BucketLimitSnapshot bucket = new BucketLimitSnapshot(
                938L, 700L,
                null, null, null, null,
                20, 18, 0, null, // times remaining 2 -- enough for neither 3 nor a split
                null, null, null, null);

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of(bucket));

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.EXHAUSTED);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.TIMES);
        assertThat(d.approvedQuantity()).isZero();
        assertThat(d.refusedQuantity()).isEqualTo(3);
        assertThat(d.bindingAvailableAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("no buckets at all -> UNLIMITED, full request approved")
    void unlimitedWhenNoBucketApplies() {
        UnifiedLimitInput in = new UnifiedLimitInput(
                700L, 907L, 500L, DATE, EncounterType.OUTPATIENT,
                3, 0, CountingMethod.EACH_UNIT, new BigDecimal("100.00"), new BigDecimal("300.00"),
                null, ReservationEvaluationMode.NORMAL, null, null);

        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, List.of());

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.UNLIMITED);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.NONE);
        assertThat(d.approvedQuantity()).isEqualTo(3);
        assertThat(d.bindingAvailableAmount()).isEqualByComparingTo("300.00");
    }
}
