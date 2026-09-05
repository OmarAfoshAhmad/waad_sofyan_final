package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
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
 * P1.5.0a: proves the adapter turns BenefitBucketLimitService's own output
 * (already correct for AMOUNT/TIMES/DAYS selection+committed+period, per
 * P1.1) plus the exact reserved-reading queries LimitBalanceReader already
 * uses into P1.3-shaped snapshots -- zero new queries, one owner of
 * `remaining` (this adapter; UnifiedLimitResolver never recomputes it,
 * P1.5.0).
 */
@ExtendWith(MockitoExtension.class)
class BucketLimitSnapshotAdapterTest {

    private static final Long POLICY_ID = 700L;
    private static final Long RULE_ID = 900L;
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
    }

    private BenefitLimitBucket bucketOwnedBy(long bucketId, long owningPolicyId) {
        BenefitPolicy policy = BenefitPolicy.builder().id(owningPolicyId).build();
        return BenefitLimitBucket.builder().id(bucketId).code("B" + bucketId).nameAr("وعاء").policy(policy).build();
    }

    @Test
    @DisplayName("S1 — amount only: configured/committed/reserved/remaining come from the exact live queries")
    void s1AmountOnlyProducesConfiguredCommittedReservedRemaining() {
        LimitSnapshot snapshot = new LimitSnapshot(931L, "b", new BigDecimal("1000.00"), null, null,
                new BigDecimal("300.00"), 0, 0, false, CountingMethod.EACH_LINE, ConsumptionBasis.ELIGIBLE_AMOUNT,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(snapshot));
        when(bucketRepository.findAllById(any())).thenReturn(java.util.List.of(bucketOwnedBy(931L, POLICY_ID)));
        var row = mockAmountRow(931L, PERIOD_START, PERIOD_END, "RESERVED", new BigDecimal("100.00"));
        when(consumptionRepository.aggregateAmountBalances(eq(MEMBER_ID), any(), eq(null)))
                .thenReturn(List.of(row));

        var result = adapter.buildForNormalClaim(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE,
                EncounterType.OUTPATIENT, null);

        assertThat(result.blocked()).isFalse();
        BucketLimitSnapshot s = result.snapshots().get(0);
        assertThat(s.limitType()).isEqualTo(LimitAxisType.AMOUNT);
        assertThat(s.configured()).isEqualByComparingTo("1000.00");
        assertThat(s.committed()).isEqualByComparingTo("300.00");
        assertThat(s.activeReserved()).isEqualByComparingTo("100.00");
        assertThat(s.remaining()).isEqualByComparingTo("600.00"); // 1000 - 300 - 100
    }

    @Test
    void s2TimesOnlyReadsReservedTimesDirectly() {
        LimitSnapshot snapshot = new LimitSnapshot(932L, "b", null, 20, null,
                BigDecimal.ZERO, 10, 0, false, CountingMethod.EACH_UNIT, ConsumptionBasis.COMPANY_SHARE,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(snapshot));
        when(bucketRepository.findAllById(any())).thenReturn(java.util.List.of(bucketOwnedBy(932L, POLICY_ID)));
        when(consumptionRepository.aggregateAmountBalances(eq(MEMBER_ID), any(), eq(null))).thenReturn(List.of());
        when(consumptionRepository.sumReservedTimes(MEMBER_ID, 932L, PERIOD_START, PERIOD_END)).thenReturn(4);

        var result = adapter.buildForNormalClaim(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE,
                EncounterType.OUTPATIENT, null);

        BucketLimitSnapshot s = result.snapshots().get(0);
        assertThat(s.limitType()).isEqualTo(LimitAxisType.TIMES);
        assertThat(s.configured()).isEqualByComparingTo("20");
        assertThat(s.committed()).isEqualByComparingTo("10");
        assertThat(s.activeReserved()).isEqualByComparingTo("4");
        assertThat(s.remaining()).isEqualByComparingTo("6"); // 20 - 10 - 4
    }

    @Test
    void s3DaysHaveNoReservationConcept() {
        LimitSnapshot snapshot = new LimitSnapshot(933L, "b", null, null, 10,
                BigDecimal.ZERO, 0, 4, false, CountingMethod.PER_DAY, ConsumptionBasis.ELIGIBLE_AMOUNT,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.INPATIENT, null))
                .thenReturn(List.of(snapshot));
        when(bucketRepository.findAllById(any())).thenReturn(java.util.List.of(bucketOwnedBy(933L, POLICY_ID)));
        when(consumptionRepository.aggregateAmountBalances(eq(MEMBER_ID), any(), eq(null))).thenReturn(List.of());

        var result = adapter.buildForNormalClaim(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE,
                EncounterType.INPATIENT, null);

        BucketLimitSnapshot s = result.snapshots().get(0);
        assertThat(s.limitType()).isEqualTo(LimitAxisType.DAYS);
        assertThat(s.activeReserved()).isEqualByComparingTo("0"); // no capability, not measured-and-zero
        assertThat(s.remaining()).isEqualByComparingTo("6"); // 10 - 4
        // No sumReservedDays call is ever made -- there is no such method in the repository.
    }

    @Test
    void s5WrongPolicyBucketBlocksBeforeAnyBalanceRead() {
        LimitSnapshot snapshot = new LimitSnapshot(936L, "b", new BigDecimal("100.00"), null, null,
                BigDecimal.ZERO, 0, 0, false, CountingMethod.EACH_LINE, ConsumptionBasis.ELIGIBLE_AMOUNT,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(snapshot));
        when(bucketRepository.findAllById(any())).thenReturn(java.util.List.of(bucketOwnedBy(936L, 701L)));

        var result = adapter.buildForNormalClaim(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE,
                EncounterType.OUTPATIENT, null);

        assertThat(result.blocked()).isTrue();
        assertThat(result.snapshots()).isEmpty();
        assertThat(result.blockReason()).contains("BUCKET_POLICY_MISMATCH");
        // No balance query is ever reached once blocked.
        verify(consumptionRepository, org.mockito.Mockito.never()).aggregateAmountBalances(any(), any(), any());
    }

    @Test
    @DisplayName("S4 — a REVERSED (cancelled) reservation never contributes to activeReserved")
    void s4CancelledReservationExcludedFromActiveReserved() {
        LimitSnapshot snapshot = new LimitSnapshot(934L, "b", new BigDecimal("1000.00"), null, null,
                new BigDecimal("300.00"), 0, 0, false, CountingMethod.EACH_LINE, ConsumptionBasis.ELIGIBLE_AMOUNT,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(snapshot));
        when(bucketRepository.findAllById(any())).thenReturn(java.util.List.of(bucketOwnedBy(934L, POLICY_ID)));
        var reversedRow = mockAmountRow(934L, PERIOD_START, PERIOD_END, "REVERSED", new BigDecimal("250.00"));
        var reservedRow = mockAmountRow(934L, PERIOD_START, PERIOD_END, "RESERVED", new BigDecimal("100.00"));
        when(consumptionRepository.aggregateAmountBalances(eq(MEMBER_ID), any(), eq(null)))
                .thenReturn(List.of(reversedRow, reservedRow));

        var result = adapter.buildForNormalClaim(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE,
                EncounterType.OUTPATIENT, null);

        BucketLimitSnapshot s = result.snapshots().get(0);
        // Only the RESERVED row counts; the REVERSED (cancelled) row is invisible to activeReserved.
        assertThat(s.activeReserved()).isEqualByComparingTo("100.00");
        assertThat(s.remaining()).isEqualByComparingTo("600.00"); // 1000 - 300 - 100, not - 350
    }

    @Test
    @DisplayName("S6 — a bucket shared across two applicable rows (same BucketChainWalker parent, same period) "
            + "is read once from the ledger and both rows see the same reserved amount")
    void s6SharedParentBucketReadOnceAcrossMultipleApplicableRows() {
        Long sharedBucketId = 935L;
        // Two categories/rules resolve to the SAME shared/parent bucket for the same period --
        // exactly what BucketChainWalker produces when a category's own bucket has no ceiling
        // of its own and the walk climbs to a shared parent.
        LimitSnapshot fromCategoryA = new LimitSnapshot(sharedBucketId, "shared", new BigDecimal("500.00"), null,
                null, new BigDecimal("120.00"), 0, 0, false, CountingMethod.EACH_LINE,
                ConsumptionBasis.ELIGIBLE_AMOUNT, true, PERIOD_START, PERIOD_END);
        LimitSnapshot fromCategoryB = new LimitSnapshot(sharedBucketId, "shared", new BigDecimal("500.00"), null,
                null, new BigDecimal("120.00"), 0, 0, false, CountingMethod.EACH_LINE,
                ConsumptionBasis.ELIGIBLE_AMOUNT, true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(fromCategoryA, fromCategoryB));
        when(bucketRepository.findAllById(any()))
                .thenReturn(java.util.List.of(bucketOwnedBy(sharedBucketId, POLICY_ID)));
        var reservedRow = mockAmountRow(sharedBucketId, PERIOD_START, PERIOD_END, "RESERVED", new BigDecimal("50.00"));
        when(consumptionRepository.aggregateAmountBalances(eq(MEMBER_ID), eq(List.of(sharedBucketId)), eq(null)))
                .thenReturn(List.of(reservedRow));

        var result = adapter.buildForNormalClaim(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE,
                EncounterType.OUTPATIENT, null);

        assertThat(result.snapshots()).hasSize(2);
        for (BucketLimitSnapshot s : result.snapshots()) {
            assertThat(s.bucketId()).isEqualTo(sharedBucketId);
            assertThat(s.activeReserved()).isEqualByComparingTo("50.00");
            assertThat(s.remaining()).isEqualByComparingTo("330.00"); // 500 - 120 - 50
        }
        // The ledger read happens once per distinct bucket, not once per applicable row.
        verify(consumptionRepository).aggregateAmountBalances(eq(MEMBER_ID), eq(List.of(sharedBucketId)), eq(null));
        verify(bucketRepository).findAllById(List.of(sharedBucketId));
    }

    @Test
    void s7ExcludeClaimIdPropagatesToBucketSelectionAndReservedRead() {
        Long excludeClaimId = 4242L;
        LimitSnapshot snapshot = new LimitSnapshot(931L, "b", new BigDecimal("1000.00"), null, null,
                new BigDecimal("300.00"), 0, 0, false, CountingMethod.EACH_LINE, ConsumptionBasis.ELIGIBLE_AMOUNT,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, excludeClaimId))
                .thenReturn(List.of(snapshot));
        when(bucketRepository.findAllById(any())).thenReturn(java.util.List.of(bucketOwnedBy(931L, POLICY_ID)));
        when(consumptionRepository.aggregateAmountBalances(MEMBER_ID, List.of(931L), excludeClaimId))
                .thenReturn(List.of());

        adapter.buildForNormalClaim(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, excludeClaimId);

        verify(bucketLimitService).findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, excludeClaimId);
        verify(consumptionRepository).aggregateAmountBalances(MEMBER_ID, List.of(931L), excludeClaimId);
    }

    // ── P1.12.2: buildForPreauthReservation ─────────────────────────────

    @Test
    @DisplayName("PR1 — AMOUNT: identical arithmetic to buildForNormalClaim -- a RESERVED row "
            + "(even one placed by this same pre-authorization) simply reduces what is reservable, "
            + "there is no own-reservation add-back (P1.12.1 PA6 parity)")
    void pr1AmountUsesTheSameArithmeticAsNormalClaimWithNoOwnReservationConcept() {
        LimitSnapshot snapshot = new LimitSnapshot(931L, "b", new BigDecimal("1000.00"), null, null,
                new BigDecimal("300.00"), 0, 0, false, CountingMethod.EACH_LINE, ConsumptionBasis.ELIGIBLE_AMOUNT,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(snapshot));
        BenefitLimitBucket bucket = bucketOwnedBy(931L, POLICY_ID);
        bucket.setConsumptionBasis(ConsumptionBasis.ELIGIBLE_AMOUNT);
        when(bucketRepository.findAllById(any())).thenReturn(java.util.List.of(bucket));
        var row = mockAmountRow(931L, PERIOD_START, PERIOD_END, "RESERVED", new BigDecimal("100.00"));
        when(consumptionRepository.aggregateAmountBalances(eq(MEMBER_ID), any(), eq(null)))
                .thenReturn(List.of(row));

        var outcome = adapter.buildForPreauthReservation(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE,
                EncounterType.OUTPATIENT);

        assertThat(outcome.evaluation().blocked()).isFalse();
        BucketLimitSnapshot s = outcome.evaluation().snapshots().get(0);
        assertThat(s.configured()).isEqualByComparingTo("1000.00");
        assertThat(s.committed()).isEqualByComparingTo("300.00");
        assertThat(s.activeReserved()).isEqualByComparingTo("100.00");
        assertThat(s.remaining()).isEqualByComparingTo("600.00"); // 1000 - 300 - 100, whoever placed the 100

        // The bucket's own configured measure travels alongside its identity,
        // read from the same entity already fetched for BUCKET_POLICY_MISMATCH.
        assertThat(outcome.measures()).containsExactly(
                new ResolvedLimitMeasure(ResolvedLimitDescriptor.bucketKey(931L), ConsumptionBasis.ELIGIBLE_AMOUNT));
    }

    @Test
    @DisplayName("PR2 — TIMES: read exactly like buildForNormalClaim")
    void pr2TimesReadsReservedTimesDirectly() {
        LimitSnapshot snapshot = new LimitSnapshot(932L, "b", null, 20, null,
                BigDecimal.ZERO, 10, 0, false, CountingMethod.EACH_UNIT, ConsumptionBasis.COMPANY_SHARE,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(snapshot));
        when(bucketRepository.findAllById(any())).thenReturn(java.util.List.of(bucketOwnedBy(932L, POLICY_ID)));
        when(consumptionRepository.aggregateAmountBalances(eq(MEMBER_ID), any(), eq(null))).thenReturn(List.of());
        when(consumptionRepository.sumReservedTimes(MEMBER_ID, 932L, PERIOD_START, PERIOD_END)).thenReturn(4);

        var outcome = adapter.buildForPreauthReservation(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE,
                EncounterType.OUTPATIENT);

        BucketLimitSnapshot s = outcome.evaluation().snapshots().get(0);
        assertThat(s.limitType()).isEqualTo(LimitAxisType.TIMES);
        assertThat(s.remaining()).isEqualByComparingTo("6"); // 20 - 10 - 4
    }

    @Test
    @DisplayName("PR3 — a bucket with a days limit blocks the WHOLE decision, structured, not thrown "
            + "(P1.12.1 PA4 preserved as a genuine constraint, P1.12.1 PA7's raw-exception style deliberately NOT repeated)")
    void pr3DayLimitedBucketBlocksInsteadOfThrowing() {
        LimitSnapshot amountOnly = new LimitSnapshot(940L, "money", new BigDecimal("1000.00"), null, null,
                BigDecimal.ZERO, 0, 0, false, CountingMethod.EACH_LINE, ConsumptionBasis.ELIGIBLE_AMOUNT,
                true, PERIOD_START, PERIOD_END);
        LimitSnapshot dayLimited = new LimitSnapshot(941L, "days", null, null, 5,
                BigDecimal.ZERO, 0, 0, false, CountingMethod.PER_DAY, ConsumptionBasis.ELIGIBLE_AMOUNT,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(amountOnly, dayLimited));
        when(bucketRepository.findAllById(any())).thenReturn(java.util.List.of(
                bucketOwnedBy(940L, POLICY_ID),
                BenefitLimitBucket.builder().id(941L).code("B941").nameAr("أيام")
                        .policy(BenefitPolicy.builder().id(POLICY_ID).build()).daysLimit(5).build()));

        var outcome = adapter.buildForPreauthReservation(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE,
                EncounterType.OUTPATIENT);

        assertThat(outcome.evaluation().blocked()).isTrue();
        assertThat(outcome.evaluation().snapshots()).isEmpty();
        assertThat(outcome.evaluation().blockReason()).contains("PREAUTH_DAY_LIMIT_UNSUPPORTED");
        assertThat(outcome.measures()).isEmpty();
        // Blocked before any balance is read -- same discipline as BUCKET_POLICY_MISMATCH.
        verify(consumptionRepository, org.mockito.Mockito.never()).aggregateAmountBalances(any(), any(), any());
    }

    @Test
    @DisplayName("PR4 — a wrong-policy bucket blocks structurally, same as buildForNormalClaim "
            + "(P1.12.1 PA7's target behavior, not today's raw IllegalStateException)")
    void pr4WrongPolicyBucketBlocksStructurally() {
        LimitSnapshot snapshot = new LimitSnapshot(936L, "b", new BigDecimal("100.00"), null, null,
                BigDecimal.ZERO, 0, 0, false, CountingMethod.EACH_LINE, ConsumptionBasis.ELIGIBLE_AMOUNT,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(snapshot));
        when(bucketRepository.findAllById(any())).thenReturn(java.util.List.of(bucketOwnedBy(936L, 701L)));

        var outcome = adapter.buildForPreauthReservation(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE,
                EncounterType.OUTPATIENT);

        assertThat(outcome.evaluation().blocked()).isTrue();
        assertThat(outcome.evaluation().blockReason()).contains("BUCKET_POLICY_MISMATCH");
    }

    private BenefitBucketConsumptionRepository.BucketAmountBalanceProjection mockAmountRow(
            Long bucketId, LocalDate start, LocalDate end, String status, BigDecimal amount) {
        var row = org.mockito.Mockito.mock(BenefitBucketConsumptionRepository.BucketAmountBalanceProjection.class);
        lenient().when(row.getBucketId()).thenReturn(bucketId);
        lenient().when(row.getPeriodStart()).thenReturn(start);
        lenient().when(row.getPeriodEnd()).thenReturn(end);
        lenient().when(row.getStatus()).thenReturn(status);
        lenient().when(row.getAmount()).thenReturn(amount);
        return row;
    }
}
