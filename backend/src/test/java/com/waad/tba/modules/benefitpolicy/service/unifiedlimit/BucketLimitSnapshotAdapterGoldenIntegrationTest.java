package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.benefitpolicy.service.BenefitBucketLimitService;
import com.waad.tba.modules.benefitpolicy.service.BenefitBucketLimitService.LimitSnapshot;
import com.waad.tba.modules.providercontract.enums.EncounterType;

/**
 * P1.5.0 closing gate: {@code UnifiedLimitResolverTest} proves the resolver
 * against hand-built {@link BucketLimitSnapshot} fixtures. That is not
 * sufficient on its own -- it does not prove {@link BucketLimitSnapshotAdapter}
 * builds those same shapes from what {@code BenefitBucketLimitService} and
 * the consumption repository actually return. This class closes that gap:
 * every G-scenario here mocks only the repositories/service the adapter
 * depends on, runs {@code adapter.buildForNormalClaim(...)} for real, and
 * feeds ITS OUTPUT into {@code UnifiedLimitResolver.resolve} -- reproducing
 * G1-G6 and G8 end to end. No test in this class hand-builds a
 * {@link BucketLimitSnapshot}.
 *
 * G7 (PREAUTHORIZED_CLAIM) is deliberately not attempted here:
 * {@code BucketLimitSnapshotAdapter.buildForNormalClaim} only implements
 * NORMAL mode (P1.5.0a). The PREAUTHORIZED_CLAIM adapter method (P1.5.0b,
 * required before P1.12) is a separate, not-yet-built unit of work -- its
 * own end-to-end proof belongs with it, not faked here against the wrong
 * adapter method.
 */
@ExtendWith(MockitoExtension.class)
class BucketLimitSnapshotAdapterGoldenIntegrationTest {

    private static final Long POLICY_ID = 700L;
    private static final Long MEMBER_ID = 500L;
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 3, 1);
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 12, 31);

    @Mock BenefitBucketLimitService bucketLimitService;
    @Mock BenefitLimitBucketRepository bucketRepository;
    @Mock BenefitBucketConsumptionRepository consumptionRepository;

    private BucketLimitSnapshotAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new BucketLimitSnapshotAdapter(bucketLimitService, bucketRepository, consumptionRepository);
        lenient().when(consumptionRepository.aggregateAmountBalances(any(), any(), any())).thenReturn(List.of());
    }

    private void stubBucket(long bucketId) {
        BenefitPolicy policy = BenefitPolicy.builder().id(POLICY_ID).build();
        when(bucketRepository.findAllById(any()))
                .thenReturn(List.of(BenefitLimitBucket.builder().id(bucketId).code("B" + bucketId)
                        .nameAr("وعاء").policy(policy).build()));
    }

    private List<BucketLimitSnapshot> snapshotsFor(Long ruleId, EncounterType encounterType,
            LimitSnapshot... applicable) {
        when(bucketLimitService.findApplicable(eq(ruleId), eq(MEMBER_ID), eq(SERVICE_DATE), eq(encounterType), any()))
                .thenReturn(List.of(applicable));
        var result = adapter.buildForNormalClaim(POLICY_ID, ruleId, MEMBER_ID, SERVICE_DATE, encounterType, null);
        assertThat(result.blocked()).isFalse();
        return result.snapshots();
    }

    @Test
    @DisplayName("G1 through the real adapter — amount only, no occurrence dimension")
    void g1AmountOnlyThroughAdapter() {
        stubBucket(931L);
        LimitSnapshot ls = new LimitSnapshot(931L, "b", new BigDecimal("1000.00"), null, null,
                new BigDecimal("400.00"), 0, 0, false, CountingMethod.EACH_LINE, ConsumptionBasis.ELIGIBLE_AMOUNT,
                true, PERIOD_START, PERIOD_END);
        List<BucketLimitSnapshot> snapshots = snapshotsFor(900L, EncounterType.OUTPATIENT, ls);

        UnifiedLimitInput in = new UnifiedLimitInput(POLICY_ID, 900L, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT,
                1, 0, new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, snapshots);

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.PARTIAL);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.AMOUNT);
        assertThat(d.bindingAvailableAmount()).isEqualByComparingTo("600.00");
        assertThat(d.approvedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("G2 through the real adapter — the Physio gate: 3 requested, 2 remaining")
    void g2PhysioGateThroughAdapter() {
        stubBucket(932L);
        LimitSnapshot ls = new LimitSnapshot(932L, "b", null, 20, null,
                BigDecimal.ZERO, 18, 0, false, CountingMethod.EACH_UNIT, ConsumptionBasis.COMPANY_SHARE,
                true, PERIOD_START, PERIOD_END);
        when(consumptionRepository.sumReservedTimes(MEMBER_ID, 932L, PERIOD_START, PERIOD_END)).thenReturn(0);
        List<BucketLimitSnapshot> snapshots = snapshotsFor(901L, EncounterType.OUTPATIENT, ls);

        UnifiedLimitInput in = new UnifiedLimitInput(POLICY_ID, 901L, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT,
                3, 0, new BigDecimal("100.00"), new BigDecimal("300.00"),
                null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, snapshots);

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.PARTIAL);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.TIMES);
        assertThat(d.approvedQuantity()).isEqualTo(2);
        assertThat(d.refusedQuantity()).isEqualTo(1);
        assertThat(d.bindingAvailableAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("G3 through the real adapter — days are atomic, fully exhausted")
    void g3DaysAtomicThroughAdapter() {
        stubBucket(933L);
        LimitSnapshot ls = new LimitSnapshot(933L, "b", null, null, 10,
                BigDecimal.ZERO, 0, 10, false, CountingMethod.PER_DAY, ConsumptionBasis.ELIGIBLE_AMOUNT,
                true, PERIOD_START, PERIOD_END);
        List<BucketLimitSnapshot> snapshots = snapshotsFor(902L, EncounterType.INPATIENT, ls);

        UnifiedLimitInput in = new UnifiedLimitInput(POLICY_ID, 902L, MEMBER_ID, SERVICE_DATE, EncounterType.INPATIENT,
                1, 1, new BigDecimal("300.00"), new BigDecimal("300.00"),
                null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, snapshots);

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.EXHAUSTED);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.DAYS);
        assertThat(d.approvedDays()).isZero();
        assertThat(d.refusedDays()).isEqualTo(1);
        assertThat(d.bindingAvailableAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("G4 through the real adapter — one bucket configures both AMOUNT and TIMES; the whole-unit constraint wins")
    void g4AmountAndTimesTogetherThroughAdapter() {
        stubBucket(934L);
        LimitSnapshot ls = new LimitSnapshot(934L, "b", new BigDecimal("250.00"), 20, null,
                BigDecimal.ZERO, 16, 0, false, CountingMethod.EACH_UNIT, ConsumptionBasis.ELIGIBLE_AMOUNT,
                true, PERIOD_START, PERIOD_END);
        when(consumptionRepository.sumReservedTimes(MEMBER_ID, 934L, PERIOD_START, PERIOD_END)).thenReturn(0);
        List<BucketLimitSnapshot> snapshots = snapshotsFor(903L, EncounterType.OUTPATIENT, ls);
        assertThat(snapshots).hasSize(2); // one AMOUNT row, one TIMES row, same bucket

        UnifiedLimitInput in = new UnifiedLimitInput(POLICY_ID, 903L, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT,
                5, 0, new BigDecimal("100.00"), new BigDecimal("500.00"),
                null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, snapshots);

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.PARTIAL);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.AMOUNT);
        assertThat(d.approvedQuantity()).isEqualTo(2);
        assertThat(d.bindingAvailableAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("G5 through the real adapter — an active reservation from another approval narrows what's left")
    void g5ActiveReservationThroughAdapter() {
        stubBucket(935L);
        LimitSnapshot ls = new LimitSnapshot(935L, "b", null, 20, null,
                BigDecimal.ZERO, 10, 0, false, CountingMethod.EACH_UNIT, ConsumptionBasis.COMPANY_SHARE,
                true, PERIOD_START, PERIOD_END);
        when(consumptionRepository.sumReservedTimes(MEMBER_ID, 935L, PERIOD_START, PERIOD_END)).thenReturn(4);
        List<BucketLimitSnapshot> snapshots = snapshotsFor(904L, EncounterType.OUTPATIENT, ls);

        UnifiedLimitInput in = new UnifiedLimitInput(POLICY_ID, 904L, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT,
                8, 0, new BigDecimal("50.00"), new BigDecimal("400.00"),
                null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, snapshots);

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.PARTIAL);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.TIMES);
        assertThat(d.approvedQuantity()).isEqualTo(6);
        assertThat(d.refusedQuantity()).isEqualTo(2);
        assertThat(d.times().reserved()).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("G6 through the real adapter — a policy-mismatched bucket is BLOCKED at the adapter, "
            + "before the resolver ever runs (defense-in-depth, not a bypass)")
    void g6PolicyMismatchThroughAdapter() {
        BenefitPolicy foreignPolicy = BenefitPolicy.builder().id(701L).build();
        when(bucketRepository.findAllById(any())).thenReturn(List.of(
                BenefitLimitBucket.builder().id(936L).code("B936").nameAr("وعاء").policy(foreignPolicy).build()));
        LimitSnapshot ls = new LimitSnapshot(936L, "b", new BigDecimal("100.00"), null, null,
                BigDecimal.ZERO, 0, 0, false, CountingMethod.EACH_LINE, ConsumptionBasis.ELIGIBLE_AMOUNT,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(eq(905L), eq(MEMBER_ID), eq(SERVICE_DATE),
                eq(EncounterType.OUTPATIENT), any())).thenReturn(List.of(ls));

        var result = adapter.buildForNormalClaim(POLICY_ID, 905L, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null);

        assertThat(result.blocked()).isTrue();
        assertThat(result.blockReason()).contains("BUCKET_POLICY_MISMATCH");
        assertThat(result.snapshots()).isEmpty();
        // In production this is where the decision stops -- UnifiedLimitResolver
        // is never called with a mismatched snapshot. Its own BLOCKED guard
        // (UnifiedLimitResolverTest.g6PolicyOwnershipMismatchBlocks) protects
        // against a future caller that skips the adapter, not this path.
    }

    @Test
    @DisplayName("G8 through the real adapter — PER_VISIT is atomic even though a TIMES axis exists: refuse the whole request")
    void g8AtomicCountingMethodThroughAdapter() {
        stubBucket(938L);
        LimitSnapshot ls = new LimitSnapshot(938L, "b", null, 20, null,
                BigDecimal.ZERO, 18, 0, false, CountingMethod.PER_VISIT, ConsumptionBasis.COMPANY_SHARE,
                true, PERIOD_START, PERIOD_END);
        when(consumptionRepository.sumReservedTimes(MEMBER_ID, 938L, PERIOD_START, PERIOD_END)).thenReturn(0);
        List<BucketLimitSnapshot> snapshots = snapshotsFor(908L, EncounterType.OUTPATIENT, ls);

        UnifiedLimitInput in = new UnifiedLimitInput(POLICY_ID, 908L, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT,
                3, 0, new BigDecimal("100.00"), new BigDecimal("300.00"),
                null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, snapshots);

        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.EXHAUSTED);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.TIMES);
        assertThat(d.approvedQuantity()).isZero();
        assertThat(d.refusedQuantity()).isEqualTo(3);
        assertThat(d.bindingAvailableAmount()).isEqualByComparingTo("0.00");
    }
}
