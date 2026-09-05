package com.waad.tba.modules.preauthorization.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.waad.tba.modules.benefitpolicy.entity.ClaimLineLimitSnapshot;
import com.waad.tba.modules.benefitpolicy.enums.BeneficiaryScopeType;
import com.waad.tba.modules.benefitpolicy.enums.BenefitScopeType;
import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.benefitpolicy.service.TimesLimitEvaluator;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.BucketLimitSnapshot;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.LimitAxisType;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ReservationEvaluationMode;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ResolvedLimitDescriptor;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ResolvedLimitItem;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ResolvedLimitMeasure;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitDecision;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitInput;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitResolver;
import com.waad.tba.modules.providercontract.enums.EncounterType;

/**
 * P1.12.2 — proves {@link PreAuthLimitHoldMapper} is pure measurement:
 * every "before" figure travels verbatim off the canonical
 * {@link BucketLimitSnapshot}, and the ONLY genuine PreAuth-specific
 * decision it makes is WHICH of the two already-final money figures a
 * bucket reserves, based on its own {@link ConsumptionBasis}.
 */
class PreAuthLimitHoldMapperTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 12, 31);
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 3, 1);

    private final PreAuthLimitHoldMapper mapper = new PreAuthLimitHoldMapper(new TimesLimitEvaluator());

    private static ResolvedLimitDescriptor bucketDescriptor(Long bucketId) {
        return new ResolvedLimitDescriptor(ResolvedLimitDescriptor.bucketKey(bucketId), bucketId,
                ClaimLineLimitSnapshot.SourceType.POLICY_DEFAULT, BenefitScopeType.CATEGORY,
                BeneficiaryScopeType.MEMBER, 42L, 9L, "ANNUAL", PERIOD_START, PERIOD_END);
    }

    private static ResolvedLimitDescriptor generalDescriptor(Long policyId) {
        return new ResolvedLimitDescriptor(ResolvedLimitDescriptor.policyGeneralKey(policyId), null,
                ClaimLineLimitSnapshot.SourceType.POLICY_DEFAULT, BenefitScopeType.POLICY_GENERAL,
                BeneficiaryScopeType.MEMBER, 42L, null, "ANNUAL", PERIOD_START, PERIOD_END);
    }

    @Test
    @DisplayName("Golden Gate — an ELIGIBLE_AMOUNT bucket and the POLICY_GENERAL ceiling reserve "
            + "DIFFERENT figures on the SAME decision: 1000 vs 800, never collapsed to one number "
            + "(P1.12.1's characterized invariant, now the canonical path's own gate)")
    void eligibleAmountBucketAndPolicyGeneralReserveDifferentFiguresOnTheSameDecision() {
        BucketLimitSnapshot bucketTarget = new BucketLimitSnapshot(910L, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(5000), BigDecimal.valueOf(1000), BigDecimal.ZERO,
                BigDecimal.valueOf(4000), PERIOD_START, PERIOD_END);
        BucketLimitSnapshot generalTarget = new BucketLimitSnapshot(null, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(1000000), BigDecimal.valueOf(200), BigDecimal.ZERO,
                BigDecimal.valueOf(999800), PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(700L, 42L, 500L, SERVICE_DATE, EncounterType.OUTPATIENT,
                0, 0, BigDecimal.ZERO, new BigDecimal("1000.00"), null,
                ReservationEvaluationMode.PREAUTH_RESERVATION, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(bucketTarget, generalTarget));

        List<ResolvedLimitItem> items = List.of(
                new ResolvedLimitItem(bucketTarget, bucketDescriptor(910L)),
                new ResolvedLimitItem(generalTarget, generalDescriptor(700L)));
        List<ResolvedLimitMeasure> measures = List.of(
                new ResolvedLimitMeasure(ResolvedLimitDescriptor.bucketKey(910L), ConsumptionBasis.ELIGIBLE_AMOUNT));

        List<PreAuthorizationDecision.LimitHold> holds = mapper.map(decision, items, measures,
                new BigDecimal("800.00"), new BigDecimal("1000.00"), SERVICE_DATE, new HashSet<>());

        assertThat(holds).hasSize(2);
        var bucketHold = holds.stream().filter(h -> "BUCKET".equals(h.limitScope())).findFirst().orElseThrow();
        var generalHold = holds.stream().filter(h -> "POLICY_GENERAL".equals(h.limitScope())).findFirst().orElseThrow();

        assertThat(bucketHold.amountReserved()).isEqualByComparingTo("1000.00");
        assertThat(bucketHold.consumptionBasis()).isEqualTo("ELIGIBLE_AMOUNT");
        assertThat(generalHold.amountReserved()).isEqualByComparingTo("800.00");
        assertThat(generalHold.consumptionBasis()).isEqualTo("COMPANY_SHARE");
        // Never the same number by accident.
        assertThat(bucketHold.amountReserved()).isNotEqualByComparingTo(generalHold.amountReserved());
    }

    @Test
    @DisplayName("A COMPANY_SHARE bucket reserves the company share, not the eligible amount")
    void companyShareBucketReservesTheCompanyShare() {
        BucketLimitSnapshot bucketTarget = new BucketLimitSnapshot(920L, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(5000), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(5000), PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(700L, 42L, 500L, SERVICE_DATE, EncounterType.OUTPATIENT,
                0, 0, BigDecimal.ZERO, new BigDecimal("300.00"), null,
                ReservationEvaluationMode.PREAUTH_RESERVATION, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(bucketTarget));

        List<ResolvedLimitItem> items = List.of(new ResolvedLimitItem(bucketTarget, bucketDescriptor(920L)));
        List<ResolvedLimitMeasure> measures = List.of(
                new ResolvedLimitMeasure(ResolvedLimitDescriptor.bucketKey(920L), ConsumptionBasis.COMPANY_SHARE));

        List<PreAuthorizationDecision.LimitHold> holds = mapper.map(decision, items, measures,
                new BigDecimal("240.00"), new BigDecimal("300.00"), SERVICE_DATE, new HashSet<>());

        assertThat(holds).hasSize(1);
        assertThat(holds.get(0).amountReserved()).isEqualByComparingTo("240.00");
    }

    @Test
    @DisplayName("Every 'before' figure is read verbatim off the canonical snapshot, "
            + "and actualRemainingBefore/reservableAvailableBefore stay genuinely different")
    void beforeFiguresComeVerbatimFromTheCanonicalSnapshotAndNeverCollapse() {
        BucketLimitSnapshot bucketTarget = new BucketLimitSnapshot(930L, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(1000), BigDecimal.valueOf(800), BigDecimal.valueOf(0),
                BigDecimal.valueOf(200), PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(700L, 42L, 500L, SERVICE_DATE, EncounterType.OUTPATIENT,
                0, 0, BigDecimal.ZERO, new BigDecimal("100.00"), null,
                ReservationEvaluationMode.PREAUTH_RESERVATION, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(bucketTarget));

        List<ResolvedLimitItem> items = List.of(new ResolvedLimitItem(bucketTarget, bucketDescriptor(930L)));
        List<ResolvedLimitMeasure> measures = List.of(
                new ResolvedLimitMeasure(ResolvedLimitDescriptor.bucketKey(930L), ConsumptionBasis.COMPANY_SHARE));

        var hold = mapper.map(decision, items, measures, new BigDecimal("100.00"), new BigDecimal("100.00"),
                SERVICE_DATE, new HashSet<>()).get(0);

        assertThat(hold.effectiveLimit()).isEqualByComparingTo("1000");
        assertThat(hold.committedBefore()).isEqualByComparingTo("800");
        assertThat(hold.actualRemainingBefore()).isEqualByComparingTo("200");
        assertThat(hold.reservableAvailableBefore()).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("TIMES: occurrences are translated per-bucket through the SAME shared "
            + "TimesLimitEvaluator claims and legacy PreAuth already use -- EACH_UNIT divides")
    void timesAreTranslatedThroughTheSharedEvaluator() {
        BucketLimitSnapshot bucketTarget = new BucketLimitSnapshot(940L, 700L, LimitAxisType.TIMES,
                CountingMethod.EACH_UNIT, BigDecimal.valueOf(10), BigDecimal.valueOf(3), BigDecimal.valueOf(2),
                BigDecimal.valueOf(5), PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(700L, 42L, 500L, SERVICE_DATE, EncounterType.OUTPATIENT,
                4, 0, BigDecimal.ZERO, BigDecimal.ZERO, null,
                ReservationEvaluationMode.PREAUTH_RESERVATION, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(bucketTarget));
        assertThat(decision.approvedQuantity()).isEqualTo(4);

        List<ResolvedLimitItem> items = List.of(new ResolvedLimitItem(bucketTarget, bucketDescriptor(940L)));

        var hold = mapper.map(decision, items, List.of(), BigDecimal.ZERO, BigDecimal.ZERO,
                SERVICE_DATE, new HashSet<>()).get(0);

        assertThat(hold.timesLimit()).isEqualTo(10);
        assertThat(hold.committedTimesBefore()).isEqualTo(3);
        assertThat(hold.reservedTimesBefore()).isEqualTo(2);
        assertThat(hold.actualRemainingTimesBefore()).isEqualTo(7);
        assertThat(hold.reservableTimesBefore()).isEqualTo(5);
        assertThat(hold.timesReserved()).isEqualTo(4); // EACH_UNIT: divides, all 4 approved units
        assertThat(hold.amountReserved()).isNull();
        assertThat(hold.consumptionBasis()).isNull();
    }

    @Test
    @DisplayName("A mixed bucket (AMOUNT + TIMES) lands on ONE hold row, never two")
    void mixedBucketLandsOnOneHoldRow() {
        BucketLimitSnapshot amountTarget = new BucketLimitSnapshot(950L, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(1000), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(1000), PERIOD_START, PERIOD_END);
        BucketLimitSnapshot timesTarget = new BucketLimitSnapshot(950L, 700L, LimitAxisType.TIMES,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(4), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(4), PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(700L, 42L, 500L, SERVICE_DATE, EncounterType.OUTPATIENT,
                0, 0, BigDecimal.ZERO, new BigDecimal("100.00"), null,
                ReservationEvaluationMode.PREAUTH_RESERVATION, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(amountTarget, timesTarget));

        List<ResolvedLimitItem> items = List.of(
                new ResolvedLimitItem(amountTarget, bucketDescriptor(950L)),
                new ResolvedLimitItem(timesTarget, bucketDescriptor(950L)));
        List<ResolvedLimitMeasure> measures = List.of(
                new ResolvedLimitMeasure(ResolvedLimitDescriptor.bucketKey(950L), ConsumptionBasis.COMPANY_SHARE));

        List<PreAuthorizationDecision.LimitHold> holds = mapper.map(decision, items, measures,
                new BigDecimal("100.00"), new BigDecimal("100.00"), SERVICE_DATE, new HashSet<>());

        assertThat(holds).hasSize(1);
        assertThat(holds.get(0).amountReserved()).isEqualByComparingTo("100.00");
        assertThat(holds.get(0).timesLimit()).isEqualTo(4);
    }
}
