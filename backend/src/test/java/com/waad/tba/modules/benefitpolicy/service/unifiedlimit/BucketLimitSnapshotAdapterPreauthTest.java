package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository.OwnActiveReservationProjection;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.benefitpolicy.service.BenefitBucketLimitService;
import com.waad.tba.modules.benefitpolicy.service.BenefitBucketLimitService.LimitSnapshot;
import com.waad.tba.modules.providercontract.enums.EncounterType;

/**
 * P1.5.0b: {@code buildForPreauthorizedClaim}'s own Java logic -- matching
 * the bulk own-reservation read back to the right (bucket, period) and
 * applying the availableForThisClaim formula. These are unit tests over
 * mocked repositories: they prove the ADAPTER's arithmetic and key-matching,
 * not the SQL's own filtering (that belongs to
 * {@code OwnActiveReservationBulkReadIntegrationTest} against a real
 * database, since status/assignment/period filtering lives in the query's
 * WHERE clause, not in Java).
 */
@ExtendWith(MockitoExtension.class)
class BucketLimitSnapshotAdapterPreauthTest {

    private static final Long POLICY_ID = 700L;
    private static final Long RULE_ID = 906L;
    private static final Long MEMBER_ID = 500L;
    private static final Long PREAUTH_ID = 50L;
    private static final Long ASSIGNMENT_ID = 60L;
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

    private void stubTimesOnlyBucket(long bucketId, int configured, int committed) {
        BenefitPolicy policy = BenefitPolicy.builder().id(POLICY_ID).build();
        when(bucketRepository.findAllById(any())).thenReturn(List.of(
                BenefitLimitBucket.builder().id(bucketId).code("B" + bucketId).nameAr("وعاء").policy(policy).build()));
        LimitSnapshot ls = new LimitSnapshot(bucketId, "b", null, configured, null,
                BigDecimal.ZERO, committed, 0, false, CountingMethod.EACH_UNIT, ConsumptionBasis.COMPANY_SHARE,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(eq(RULE_ID), eq(MEMBER_ID), eq(SERVICE_DATE),
                eq(EncounterType.OUTPATIENT), any())).thenReturn(List.of(ls));
    }

    private OwnActiveReservationProjection ownRow(Long bucketId, LocalDate start, LocalDate end, int times) {
        var row = org.mockito.Mockito.mock(OwnActiveReservationProjection.class);
        lenient().when(row.getBucketId()).thenReturn(bucketId);
        lenient().when(row.getPeriodStart()).thenReturn(start);
        lenient().when(row.getPeriodEnd()).thenReturn(end);
        lenient().when(row.getAmount()).thenReturn(BigDecimal.ZERO);
        lenient().when(row.getTimes()).thenReturn(times);
        return row;
    }

    @Test
    @DisplayName("B1/G7 — same preauth+allocation+bucket+period+ACTIVE: the own hold is released back, capped at actualRemaining")
    void b1SameEverythingReleasesOwnReservation() {
        stubTimesOnlyBucket(937L, 20, 10);
        when(consumptionRepository.sumReservedTimes(MEMBER_ID, 937L, PERIOD_START, PERIOD_END)).thenReturn(6);
        var ownRow = ownRow(937L, PERIOD_START, PERIOD_END, 4);
        when(consumptionRepository.aggregateOwnActiveReservation(MEMBER_ID, PREAUTH_ID, ASSIGNMENT_ID, List.of(937L)))
                .thenReturn(List.of(ownRow));

        var result = adapter.buildForPreauthorizedClaim(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE,
                EncounterType.OUTPATIENT, null, PREAUTH_ID, ASSIGNMENT_ID);

        assertThat(result.blocked()).isFalse();
        BucketLimitSnapshot s = result.snapshots().get(0);
        assertThat(s.limitType()).isEqualTo(LimitAxisType.TIMES);
        assertThat(s.configured()).isEqualByComparingTo("20");
        assertThat(s.committed()).isEqualByComparingTo("10");
        assertThat(s.activeReserved()).isEqualByComparingTo("6"); // total held by everyone, informational
        // actualRemaining=10, reservableAvailable=4, availableForThisClaim=min(10,4+4)=8
        assertThat(s.remaining()).isEqualByComparingTo("8");
    }

    /**
     * P1.12.5 (U6) — the SAME bucket, the SAME balances (configured=20,
     * committed=10, allActiveReserved=6, own=4 of that 6), through the SAME
     * adapter code, differing ONLY in which build method (and therefore
     * which {@code ReservationEvaluationMode}) is called: this is the whole
     * reason the three modes exist, made explicit as a single test rather
     * than left to be inferred from two separate ones.
     */
    @Test
    @DisplayName("U6 — mode alone flips the outcome: PREAUTHORIZED_CLAIM adds the own hold back (8), "
            + "PREAUTH_RESERVATION does not (4) -- same bucket, same balances, same adapter")
    void modeAloneDeterminesWhetherTheOwnHoldIsAddedBack() {
        stubTimesOnlyBucket(937L, 20, 10);
        when(consumptionRepository.sumReservedTimes(MEMBER_ID, 937L, PERIOD_START, PERIOD_END)).thenReturn(6);
        var ownRow = ownRow(937L, PERIOD_START, PERIOD_END, 4);
        when(consumptionRepository.aggregateOwnActiveReservation(MEMBER_ID, PREAUTH_ID, ASSIGNMENT_ID, List.of(937L)))
                .thenReturn(List.of(ownRow));

        var claimConversion = adapter.buildForPreauthorizedClaim(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE,
                EncounterType.OUTPATIENT, null, PREAUTH_ID, ASSIGNMENT_ID);
        var newReservation = adapter.buildForPreauthReservation(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE,
                EncounterType.OUTPATIENT);

        assertThat(claimConversion.snapshots().get(0).remaining())
                .as("PREAUTHORIZED_CLAIM: own hold (4) added back -> min(actualRemaining=10, reservable=4+own=4)=8")
                .isEqualByComparingTo("8");
        assertThat(newReservation.evaluation().snapshots().get(0).remaining())
                .as("PREAUTH_RESERVATION: no own-hold concept at all -> actualRemaining=10 - allReserved=6 = 4")
                .isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("B2 — a hold on a DIFFERENT bucket must not be released for this one")
    void b2DifferentBucketNotReleased() {
        stubTimesOnlyBucket(937L, 20, 10);
        when(consumptionRepository.sumReservedTimes(MEMBER_ID, 937L, PERIOD_START, PERIOD_END)).thenReturn(6);
        // The bulk read legitimately returns a row for a DIFFERENT bucket the
        // same preauth also holds -- the adapter must key on bucketId and not
        // attribute it to 937L.
        var ownRow = ownRow(999L, PERIOD_START, PERIOD_END, 4);
        when(consumptionRepository.aggregateOwnActiveReservation(MEMBER_ID, PREAUTH_ID, ASSIGNMENT_ID, List.of(937L)))
                .thenReturn(List.of(ownRow));

        var result = adapter.buildForPreauthorizedClaim(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE,
                EncounterType.OUTPATIENT, null, PREAUTH_ID, ASSIGNMENT_ID);

        BucketLimitSnapshot s = result.snapshots().get(0);
        // own=0 for 937L -> actualRemaining=10, reservableAvailable=4, available=min(10,4+0)=4
        assertThat(s.remaining()).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("B3 — a hold on the SAME bucket but a DIFFERENT resolved period must not be released")
    void b3DifferentPeriodNotReleased() {
        stubTimesOnlyBucket(937L, 20, 10);
        when(consumptionRepository.sumReservedTimes(MEMBER_ID, 937L, PERIOD_START, PERIOD_END)).thenReturn(6);
        // Same bucket, but a hold from a different (e.g. prior year's) period.
        var ownRow = ownRow(937L, PERIOD_START.minusYears(1), PERIOD_END.minusYears(1), 4);
        when(consumptionRepository.aggregateOwnActiveReservation(MEMBER_ID, PREAUTH_ID, ASSIGNMENT_ID, List.of(937L)))
                .thenReturn(List.of(ownRow));

        var result = adapter.buildForPreauthorizedClaim(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE,
                EncounterType.OUTPATIENT, null, PREAUTH_ID, ASSIGNMENT_ID);

        BucketLimitSnapshot s = result.snapshots().get(0);
        assertThat(s.remaining()).isEqualByComparingTo("4"); // own=0, same math as B2
    }

    @Test
    @DisplayName("no own reservation at all -> availableForThisClaim degrades to the ordinary reservableAvailable")
    void noOwnReservationDegradesToOrdinaryAvailable() {
        stubTimesOnlyBucket(937L, 20, 10);
        when(consumptionRepository.sumReservedTimes(MEMBER_ID, 937L, PERIOD_START, PERIOD_END)).thenReturn(6);
        when(consumptionRepository.aggregateOwnActiveReservation(MEMBER_ID, PREAUTH_ID, ASSIGNMENT_ID, List.of(937L)))
                .thenReturn(List.of());

        var result = adapter.buildForPreauthorizedClaim(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE,
                EncounterType.OUTPATIENT, null, PREAUTH_ID, ASSIGNMENT_ID);

        assertThat(result.snapshots().get(0).remaining()).isEqualByComparingTo("4");
    }

    @Test
    @DisplayName("G7 through the real adapter, feeding the resolver -- reproduces UnifiedLimitResolverTest.g7 exactly")
    void g7ThroughAdapterIntoResolver() {
        stubTimesOnlyBucket(937L, 20, 10);
        when(consumptionRepository.sumReservedTimes(MEMBER_ID, 937L, PERIOD_START, PERIOD_END)).thenReturn(6);
        var ownRow = ownRow(937L, PERIOD_START, PERIOD_END, 4);
        when(consumptionRepository.aggregateOwnActiveReservation(MEMBER_ID, PREAUTH_ID, ASSIGNMENT_ID, List.of(937L)))
                .thenReturn(List.of(ownRow));

        var result = adapter.buildForPreauthorizedClaim(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE,
                EncounterType.OUTPATIENT, null, PREAUTH_ID, ASSIGNMENT_ID);

        UnifiedLimitInput in = new UnifiedLimitInput(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE,
                EncounterType.OUTPATIENT, 8, 0, new BigDecimal("50.00"),
                new BigDecimal("400.00"), null, ReservationEvaluationMode.PREAUTHORIZED_CLAIM,
                PREAUTH_ID, ASSIGNMENT_ID);
        UnifiedLimitDecision d = UnifiedLimitResolver.resolve(in, result.snapshots());

        assertThat(d.times().remaining()).isEqualByComparingTo("8");
        assertThat(d.approvedQuantity()).isEqualTo(8);
        assertThat(d.refusedQuantity()).isZero();
        assertThat(d.status()).isEqualTo(UnifiedLimitStatus.LIMITED);
        assertThat(d.bindingConstraintType()).isEqualTo(BindingConstraintType.NONE);
    }
}
