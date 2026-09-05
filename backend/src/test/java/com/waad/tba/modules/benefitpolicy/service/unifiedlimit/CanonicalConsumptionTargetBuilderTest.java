package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.waad.tba.modules.benefitpolicy.entity.ClaimLineLimitSnapshot;
import com.waad.tba.modules.benefitpolicy.enums.BeneficiaryScopeType;
import com.waad.tba.modules.benefitpolicy.enums.BenefitScopeType;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.providercontract.enums.EncounterType;

/**
 * P1.11.1 — the CanonicalConsumptionTarget contract: every movement a
 * ledger writer will ever need is fully decided here, from
 * UnifiedLimitDecision + the financial engine's own limitConsumption +
 * ResolvedLimitItem alone. No wiring into BenefitBucketLedgerService yet
 * (P1.11.2) — these are pure gates on the contract itself.
 */
class CanonicalConsumptionTargetBuilderTest {

    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 3, 1);
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 12, 31);

    private static ResolvedLimitDescriptor descriptorFor(String limitKey, Long bucketId, Long groupId) {
        return new ResolvedLimitDescriptor(limitKey, bucketId, ClaimLineLimitSnapshot.SourceType.POLICY_DEFAULT,
                BenefitScopeType.CATEGORY, BeneficiaryScopeType.MEMBER, 42L, groupId, "ANNUAL",
                PERIOD_START, PERIOD_END);
    }

    @Test
    @DisplayName("CT1 — Amount: the decision's own money consumption lands on the target unchanged")
    void ct1_amount() {
        BucketLimitSnapshot amountSnapshot = new BucketLimitSnapshot(910L, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(1000), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(1000), PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(700L, 42L, 500L, SERVICE_DATE, EncounterType.OUTPATIENT,
                0, 0, BigDecimal.ZERO, new BigDecimal("600.00"), null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(amountSnapshot));
        var items = List.of(new ResolvedLimitItem(amountSnapshot, descriptorFor("BUCKET:910", 910L, 11L)));

        List<CanonicalConsumptionTarget> targets = CanonicalConsumptionTargetBuilder.build(
                decision, new BigDecimal("600.00"), items, SERVICE_DATE);

        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).amountToConsume()).isEqualByComparingTo("600.00");
        assertThat(targets.get(0).timesToConsume()).isNull();
    }

    @Test
    @DisplayName("CT2 — Times: requested 3, only 2 approved -> target consumes 2, not 3")
    void ct2_times() {
        BucketLimitSnapshot timesSnapshot = new BucketLimitSnapshot(920L, 700L, LimitAxisType.TIMES,
                CountingMethod.EACH_UNIT, BigDecimal.valueOf(2), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(2), PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(700L, 42L, 500L, SERVICE_DATE, EncounterType.OUTPATIENT,
                3, 0, new BigDecimal("100.00"), new BigDecimal("300.00"), null, ReservationEvaluationMode.NORMAL,
                null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(timesSnapshot));
        assertThat(decision.approvedQuantity()).isEqualTo(2);
        var items = List.of(new ResolvedLimitItem(timesSnapshot, descriptorFor("BUCKET:920", 920L, 12L)));

        List<CanonicalConsumptionTarget> targets = CanonicalConsumptionTargetBuilder.build(
                decision, new BigDecimal("200.00"), items, SERVICE_DATE);

        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).timesToConsume()).isEqualTo(2);
        assertThat(targets.get(0).amountToConsume()).isNull();
    }

    @Test
    @DisplayName("CT3 — Days rejected: approvedDays=0 produces no target for a DAYS-only bucket at all")
    void ct3_daysRejected() {
        BucketLimitSnapshot daysSnapshot = new BucketLimitSnapshot(930L, 700L, LimitAxisType.DAYS,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(5), BigDecimal.valueOf(5), BigDecimal.ZERO,
                BigDecimal.ZERO, PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(700L, 42L, 500L, SERVICE_DATE, EncounterType.OUTPATIENT,
                0, 1, BigDecimal.ZERO, new BigDecimal("100.00"), null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(daysSnapshot));
        assertThat(decision.approvedDays()).isZero();
        var items = List.of(new ResolvedLimitItem(daysSnapshot, descriptorFor("BUCKET:930", 930L, 13L)));

        List<CanonicalConsumptionTarget> targets = CanonicalConsumptionTargetBuilder.build(
                decision, BigDecimal.ZERO, items, SERVICE_DATE);

        assertThat(targets).isEmpty();
    }

    @Test
    @DisplayName("CT4 — Days accepted: approvedDays>0 produces a target with consumeDay=true")
    void ct4_daysAccepted() {
        BucketLimitSnapshot daysSnapshot = new BucketLimitSnapshot(940L, 700L, LimitAxisType.DAYS,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(5), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(5), PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(700L, 42L, 500L, SERVICE_DATE, EncounterType.OUTPATIENT,
                0, 1, BigDecimal.ZERO, new BigDecimal("100.00"), null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(daysSnapshot));
        assertThat(decision.approvedDays()).isEqualTo(1);
        var items = List.of(new ResolvedLimitItem(daysSnapshot, descriptorFor("BUCKET:940", 940L, 14L)));

        List<CanonicalConsumptionTarget> targets = CanonicalConsumptionTargetBuilder.build(
                decision, BigDecimal.ZERO, items, SERVICE_DATE);

        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).consumeDay()).isTrue();
    }

    @Test
    @DisplayName("CT5 — child + parent: both appear as separate targets, sharing the same money")
    void ct5_childAndParent() {
        BucketLimitSnapshot child = new BucketLimitSnapshot(950L, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(500), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(500), PERIOD_START, PERIOD_END);
        BucketLimitSnapshot parent = new BucketLimitSnapshot(951L, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(5000), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(5000), PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(700L, 42L, 500L, SERVICE_DATE, EncounterType.OUTPATIENT,
                0, 0, BigDecimal.ZERO, new BigDecimal("600.00"), null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(child, parent));
        var items = List.of(
                new ResolvedLimitItem(child, descriptorFor("BUCKET:950", 950L, 15L)),
                new ResolvedLimitItem(parent, descriptorFor("BUCKET:951", 951L, 15L)));

        List<CanonicalConsumptionTarget> targets = CanonicalConsumptionTargetBuilder.build(
                decision, new BigDecimal("600.00"), items, SERVICE_DATE);

        assertThat(targets).hasSize(2);
        assertThat(targets).extracting(CanonicalConsumptionTarget::limitKey)
                .containsExactlyInAnyOrder("BUCKET:950", "BUCKET:951");
        assertThat(targets).allSatisfy(t -> assertThat(t.amountToConsume()).isEqualByComparingTo("600.00"));
    }

    @Test
    @DisplayName("CT6 — shared parent: the same parent bucket always resolves to the same canonical limitKey")
    void ct6_sharedParentStableIdentity() {
        // Two lines under two different rules both reach the same shared
        // parent bucket (951) -- the key must not depend on which rule got there.
        BucketLimitSnapshot sharedParent = new BucketLimitSnapshot(951L, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(5000), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(5000), PERIOD_START, PERIOD_END);
        UnifiedLimitInput inputA = new UnifiedLimitInput(700L, 42L, 500L, SERVICE_DATE, EncounterType.OUTPATIENT,
                0, 0, BigDecimal.ZERO, new BigDecimal("100.00"), null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision decisionA = UnifiedLimitResolver.resolve(inputA, List.of(sharedParent));
        var itemsA = List.of(new ResolvedLimitItem(sharedParent, descriptorFor("BUCKET:951", 951L, 15L)));

        UnifiedLimitInput inputB = new UnifiedLimitInput(700L, 43L, 500L, SERVICE_DATE, EncounterType.OUTPATIENT,
                0, 0, BigDecimal.ZERO, new BigDecimal("200.00"), null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision decisionB = UnifiedLimitResolver.resolve(inputB, List.of(sharedParent));
        var itemsB = List.of(new ResolvedLimitItem(sharedParent, descriptorFor("BUCKET:951", 951L, 15L)));

        String keyA = CanonicalConsumptionTargetBuilder.build(
                decisionA, new BigDecimal("100.00"), itemsA, SERVICE_DATE).get(0).limitKey();
        String keyB = CanonicalConsumptionTargetBuilder.build(
                decisionB, new BigDecimal("200.00"), itemsB, SERVICE_DATE).get(0).limitKey();

        assertThat(keyA).isEqualTo(keyB).isEqualTo("BUCKET:951");
    }

    @Test
    @DisplayName("CT7 — POLICY_GENERAL: represented with bucketId=null, keyed by owning policy, not a bucket id")
    void ct7_policyGeneral() {
        BucketLimitSnapshot general = new BucketLimitSnapshot(null, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(1000000), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(1000000), PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(700L, 42L, 500L, SERVICE_DATE, EncounterType.OUTPATIENT,
                0, 0, BigDecimal.ZERO, new BigDecimal("150.00"), null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(general));
        var items = List.of(new ResolvedLimitItem(general,
                new ResolvedLimitDescriptor(ResolvedLimitDescriptor.policyGeneralKey(700L), null,
                        ClaimLineLimitSnapshot.SourceType.POLICY_DEFAULT, BenefitScopeType.POLICY_GENERAL,
                        BeneficiaryScopeType.MEMBER, 42L, null, "ANNUAL", PERIOD_START, PERIOD_END)));

        List<CanonicalConsumptionTarget> targets = CanonicalConsumptionTargetBuilder.build(
                decision, new BigDecimal("150.00"), items, SERVICE_DATE);

        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).bucketId()).isNull();
        assertThat(targets.get(0).limitKey()).isEqualTo("POLICY_GENERAL:700");
    }

    @Test
    @DisplayName("CT8 — an evaluated-but-not-consumed bucket (e.g. blocked/refused axis) must NOT appear as a target")
    void ct8_nonConsumedEvaluatedBucketIsExcluded() {
        // Hand-built: a decision whose appliedBucketIds includes bucket 960
        // (it was inspected) but whose consumptionTargets omits it entirely
        // (nothing was actually consumed there) -- exactly the shape a
        // merely-evaluated parent produces when it never bound.
        UnifiedLimitDecision decision = new UnifiedLimitDecision(42L, List.of(960L), List.of(),
                0, 0, 0, 0, 0, 0,
                UnifiedLimitDecision.LimitAxis.unconfigured(), UnifiedLimitDecision.LimitAxis.unconfigured(),
                UnifiedLimitDecision.LimitAxis.unconfigured(),
                BindingConstraintType.NONE, null, BigDecimal.ZERO, UnifiedLimitStatus.LIMITED,
                List.of()); // consumptionTargets is empty -- bucket 960 was evaluated, never consumed

        BucketLimitSnapshot evaluatedOnly = new BucketLimitSnapshot(960L, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(500), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(500), PERIOD_START, PERIOD_END);
        var items = List.of(new ResolvedLimitItem(evaluatedOnly, descriptorFor("BUCKET:960", 960L, 16L)));

        List<CanonicalConsumptionTarget> targets = CanonicalConsumptionTargetBuilder.build(
                decision, BigDecimal.ZERO, items, SERVICE_DATE);

        assertThat(targets).isEmpty();
    }

    @Test
    @DisplayName("a consumption target with no matching descriptor fails closed instead of guessing")
    void missingDescriptorFailsClosed() {
        BucketLimitSnapshot amountSnapshot = new BucketLimitSnapshot(970L, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(500), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(500), PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(700L, 42L, 500L, SERVICE_DATE, EncounterType.OUTPATIENT,
                0, 0, BigDecimal.ZERO, new BigDecimal("100.00"), null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(amountSnapshot));

        assertThatThrownBy(() -> CanonicalConsumptionTargetBuilder.build(
                decision, new BigDecimal("100.00"), List.of(), SERVICE_DATE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CANONICAL_CONSUMPTION_TARGET_MISSING_DESCRIPTOR");
    }
}
