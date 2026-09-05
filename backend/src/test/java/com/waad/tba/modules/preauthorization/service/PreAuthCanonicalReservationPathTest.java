package com.waad.tba.modules.preauthorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
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
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.benefitpolicy.service.BenefitBucketLimitService;
import com.waad.tba.modules.benefitpolicy.service.BenefitBucketLimitService.LimitSnapshot;
import com.waad.tba.modules.benefitpolicy.service.TimesLimitEvaluator;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.BindingConstraintType;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.BucketLimitSnapshotAdapter;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ReservationEvaluationMode;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ResolvedLimitMeasure;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitDecision;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitInput;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitResolver;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitStatus;
import com.waad.tba.modules.claim.service.finance.WaadFinancialEngine;
import com.waad.tba.modules.providercontract.enums.EncounterType;

/**
 * P1.12.2 step 5 — PA1 through PA10, end to end on the ISOLATED canonical
 * PreAuth reservation path:
 *
 * <pre>
 * BucketLimitSnapshotAdapter.buildForPreauthReservation
 *         -> UnifiedLimitResolver
 *         -> ResolvedLimitItems / ResolvedLimitMeasures
 *         -> WaadFinancialEngine
 *         -> PreAuthLimitHoldMapper
 *         -> PreAuthorizationDecision.LimitHold
 * </pre>
 *
 * {@code PreAuthorizationDecisionBuilder} is never referenced anywhere in
 * this file -- that live wiring is P1.12.3. This suite is the review that
 * must pass BEFORE that wiring, not after.
 */
@ExtendWith(MockitoExtension.class)
class PreAuthCanonicalReservationPathTest {

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
    private final WaadFinancialEngine financialEngine = new WaadFinancialEngine();
    private final PreAuthLimitHoldMapper mapper = new PreAuthLimitHoldMapper(new TimesLimitEvaluator());

    @BeforeEach
    void setUp() {
        adapter = new BucketLimitSnapshotAdapter(bucketLimitService, bucketRepository, consumptionRepository);
        lenient().when(consumptionRepository.aggregateAmountBalances(any(), any(), any())).thenReturn(List.of());
    }

    private BenefitLimitBucket bucketOwnedBy(long bucketId, long owningPolicyId, ConsumptionBasis basis) {
        BenefitPolicy policy = BenefitPolicy.builder().id(owningPolicyId).build();
        return BenefitLimitBucket.builder().id(bucketId).code("B" + bucketId).nameAr("وعاء").policy(policy)
                .consumptionBasis(basis).build();
    }

    /** The full pipeline, PreAuthorizationDecisionBuilder never in the call chain. */
    private record Outcome(boolean blocked, String blockReason, List<PreAuthorizationDecision.LimitHold> holds,
            UnifiedLimitDecision decision) {}

    private Outcome runCanonicalPath(int requestedQuantity, BigDecimal requestedAmount, BigDecimal contractPrice,
            int coveragePercent) {
        // At quantity=1, a per-unit price and the line's full total are the
        // same number -- every gate but PA3 requests exactly one unit.
        return runCanonicalPath(requestedQuantity, requestedAmount, contractPrice, coveragePercent, requestedAmount);
    }

    /**
     * @param unitPrice        {@code UnifiedLimitInput.effectiveUnitPrice} --
     *                         genuinely PER UNIT (DivisibleLimitSplitter's
     *                         own contract)
     * @param lineTotalAmount  {@code WaadFinancialEngine.Input.contractualPrice}
     *                         and {@code requestedAmount} -- the LINE's full
     *                         total, never a per-unit price; conflating the
     *                         two is exactly the bug this overload exists to
     *                         keep PA3 from reintroducing
     */
    private Outcome runCanonicalPath(int requestedQuantity, BigDecimal requestedAmount, BigDecimal unitPrice,
            int coveragePercent, BigDecimal lineTotalAmount) {
        var evaluation = adapter.buildForPreauthReservation(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE,
                EncounterType.OUTPATIENT);
        if (evaluation.evaluation().blocked()) {
            return new Outcome(true, evaluation.evaluation().blockReason(), List.of(), null);
        }

        UnifiedLimitInput input = new UnifiedLimitInput(POLICY_ID, RULE_ID, MEMBER_ID, SERVICE_DATE,
                EncounterType.OUTPATIENT, requestedQuantity, 0, unitPrice, requestedAmount, null,
                ReservationEvaluationMode.PREAUTH_RESERVATION, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, evaluation.evaluation().snapshots());

        WaadFinancialEngine.LimitMode limitMode = decision.bindingAvailableAmount() == null
                ? WaadFinancialEngine.LimitMode.UNLIMITED : WaadFinancialEngine.LimitMode.LIMITED;
        WaadFinancialEngine.Result financial = financialEngine.evaluate(new WaadFinancialEngine.Input(
                lineTotalAmount, lineTotalAmount, limitMode, decision.bindingAvailableAmount(), coveragePercent,
                BigDecimal.ZERO, false, BigDecimal.ZERO, false, Math.max(1, requestedQuantity)));

        BigDecimal companyShare = financial.insurerFinalPayment();
        BigDecimal eligibleAmount = Optional.ofNullable(financial.insideLimit()).orElse(financial.settlementBase());

        List<PreAuthorizationDecision.LimitHold> holds = mapper.map(decision, evaluation.evaluation().items(),
                evaluation.measures(), companyShare, eligibleAmount, SERVICE_DATE, new HashSet<>());
        return new Outcome(false, null, holds, decision);
    }

    // ── PA1 — AMOUNT only ────────────────────────────────────────────────

    @Test
    @DisplayName("PA1 — AMOUNT: a request fitting within what is left is approved and held for the same figure")
    void pa1AmountOnlyApprovesAndHoldsWhatFits() {
        LimitSnapshot snapshot = new LimitSnapshot(931L, "b", new BigDecimal("1000.00"), null, null,
                new BigDecimal("300.00"), 0, 0, false, CountingMethod.EACH_LINE, ConsumptionBasis.COMPANY_SHARE,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(snapshot));
        when(bucketRepository.findAllById(any()))
                .thenReturn(List.of(bucketOwnedBy(931L, POLICY_ID, ConsumptionBasis.COMPANY_SHARE)));

        Outcome outcome = runCanonicalPath(1, new BigDecimal("300.00"), new BigDecimal("300.00"), 80);

        assertThat(outcome.blocked()).isFalse();
        assertThat(outcome.decision().status()).isEqualTo(UnifiedLimitStatus.LIMITED);
        assertThat(outcome.holds()).hasSize(1);
        var hold = outcome.holds().get(0);
        assertThat(hold.effectiveLimit()).isEqualByComparingTo("1000.00");
        assertThat(hold.amountReserved()).isEqualByComparingTo("240.00"); // 300 * 80%
    }

    // ── PA2 — TIMES partial ──────────────────────────────────────────────

    @Test
    @DisplayName("PA2 — TIMES: requested 3, only 2 remain -> approved/reserved times = 2")
    void pa2TimesPartialCapsAtWhatRemains() {
        LimitSnapshot snapshot = new LimitSnapshot(932L, "b", null, 5, null,
                BigDecimal.ZERO, 3, 0, false, CountingMethod.EACH_UNIT, ConsumptionBasis.COMPANY_SHARE,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(snapshot));
        when(bucketRepository.findAllById(any()))
                .thenReturn(List.of(bucketOwnedBy(932L, POLICY_ID, ConsumptionBasis.COMPANY_SHARE)));

        Outcome outcome = runCanonicalPath(3, new BigDecimal("300.00"), new BigDecimal("300.00"), 100);

        assertThat(outcome.decision().approvedQuantity()).isEqualTo(2);
        var hold = outcome.holds().get(0);
        assertThat(hold.timesLimit()).isEqualTo(5);
        assertThat(hold.timesReserved()).isEqualTo(2);
    }

    // ── PA3 — AMOUNT + TIMES together ────────────────────────────────────

    @Test
    @DisplayName("PA3 — AMOUNT + TIMES on the same bucket: both dimensions land on one hold, "
            + "neither summed nor comparing to the other")
    void pa3AmountAndTimesWorkTogetherIndependently() {
        LimitSnapshot snapshot = new LimitSnapshot(933L, "b", new BigDecimal("1000.00"), 4, null,
                new BigDecimal("800.00"), 1, 0, false, CountingMethod.EACH_UNIT, ConsumptionBasis.COMPANY_SHARE,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(snapshot));
        when(bucketRepository.findAllById(any()))
                .thenReturn(List.of(bucketOwnedBy(933L, POLICY_ID, ConsumptionBasis.COMPANY_SHARE)));

        // 3 units at 60 each = 180 total, comfortably inside the 200 still
        // available -- money does not bind here, and neither does the
        // occurrence ceiling (3 of the 4 remaining). effectiveUnitPrice is
        // genuinely PER UNIT for the resolver (DivisibleLimitSplitter's own
        // contract), unlike WaadFinancialEngine's contractualPrice, which is
        // the LINE's full total -- the two must never be conflated.
        Outcome outcome = runCanonicalPath(3, new BigDecimal("180.00"), new BigDecimal("60.00"), 80,
                new BigDecimal("180.00"));

        assertThat(outcome.holds()).hasSize(1);
        var hold = outcome.holds().get(0);
        assertThat(hold.timesReserved()).isEqualTo(3);
        assertThat(hold.amountReserved()).isEqualByComparingTo("144.00"); // 180 * 80%
        assertThat(hold.timesLimit()).isEqualTo(4);
        assertThat(hold.effectiveLimit()).isEqualByComparingTo("1000.00");
    }

    // ── PA4 — DAYS ───────────────────────────────────────────────────────

    @Test
    @DisplayName("PA4 — DAYS: a days-limited bucket blocks the whole decision structurally, no hold at all")
    void pa4DaysBlocksStructurallyWithNoHold() {
        LimitSnapshot dayLimited = new LimitSnapshot(934L, "b", null, null, 5,
                BigDecimal.ZERO, 0, 0, false, CountingMethod.PER_DAY, ConsumptionBasis.COMPANY_SHARE,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(dayLimited));
        when(bucketRepository.findAllById(any())).thenReturn(List.of(
                BenefitLimitBucket.builder().id(934L).code("B934").nameAr("أيام")
                        .policy(BenefitPolicy.builder().id(POLICY_ID).build()).daysLimit(5).build()));

        Outcome outcome = runCanonicalPath(1, new BigDecimal("100.00"), new BigDecimal("100.00"), 100);

        assertThat(outcome.blocked()).isTrue();
        assertThat(outcome.blockReason()).contains("PREAUTH_DAY_LIMIT_UNSUPPORTED");
        assertThat(outcome.holds()).isEmpty();
    }

    // ── PA5 — foreign reservation reduces capacity ──────────────────────

    @Test
    @DisplayName("PA5 — a foreign approval's active reservation genuinely reduces what this decision may take")
    void pa5ForeignReservationReducesReservableCapacity() {
        LimitSnapshot snapshot = new LimitSnapshot(935L, "b", new BigDecimal("1000.00"), null, null,
                BigDecimal.ZERO, 0, 0, false, CountingMethod.EACH_LINE, ConsumptionBasis.COMPANY_SHARE,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(snapshot));
        when(bucketRepository.findAllById(any()))
                .thenReturn(List.of(bucketOwnedBy(935L, POLICY_ID, ConsumptionBasis.COMPANY_SHARE)));
        var foreignRow = mockAmountRow(935L, PERIOD_START, PERIOD_END, "RESERVED", new BigDecimal("600.00"));
        when(consumptionRepository.aggregateAmountBalances(eq(MEMBER_ID), any(), eq(null)))
                .thenReturn(List.of(foreignRow));

        Outcome outcome = runCanonicalPath(1, new BigDecimal("1000.00"), new BigDecimal("1000.00"), 100);

        // Only 400 is reservable (1000 - 0 committed - 600 foreign-reserved),
        // so the decision must be capped there, never at the full 1000.
        assertThat(outcome.decision().bindingAvailableAmount()).isEqualByComparingTo("400.00");
        assertThat(outcome.holds().get(0).reservableAvailableBefore()).isEqualByComparingTo("400.00");
        assertThat(outcome.holds().get(0).reservedBefore()).isEqualByComparingTo("600.00");
    }

    // ── PA6 — own prior reservation, no add-back ─────────────────────────

    @Test
    @DisplayName("PA6 — PREAUTH_RESERVATION has no own-reservation concept: a RESERVED row is subtracted "
            + "identically whether it was placed by this same approval or a foreign one -- the adapter method "
            + "does not even accept a preauthId to distinguish them")
    void pa6OwnPriorReservationTreatedExactlyLikeForeign() {
        LimitSnapshot snapshot = new LimitSnapshot(936L, "b", new BigDecimal("1000.00"), null, null,
                BigDecimal.ZERO, 0, 0, false, CountingMethod.EACH_LINE, ConsumptionBasis.COMPANY_SHARE,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(snapshot));
        when(bucketRepository.findAllById(any()))
                .thenReturn(List.of(bucketOwnedBy(936L, POLICY_ID, ConsumptionBasis.COMPANY_SHARE)));
        // Same arithmetic as PA5 -- the row here stands in for THIS SAME
        // approval's own prior hold. buildForPreauthReservation has no
        // preauthId parameter at all, so it structurally CANNOT special-case
        // "mine" vs "someone else's".
        var ownRow = mockAmountRow(936L, PERIOD_START, PERIOD_END, "RESERVED", new BigDecimal("600.00"));
        when(consumptionRepository.aggregateAmountBalances(eq(MEMBER_ID), any(), eq(null)))
                .thenReturn(List.of(ownRow));

        Outcome outcome = runCanonicalPath(1, new BigDecimal("400.00"), new BigDecimal("400.00"), 100);

        assertThat(outcome.decision().bindingAvailableAmount()).isEqualByComparingTo("400.00");
        assertThat(outcome.holds().get(0).amountReserved())
                .as("the own hold is never added back -- available stays capped at 400, never 1000")
                .isEqualByComparingTo("400.00");
    }

    // ── PA7 — policy mismatch ────────────────────────────────────────────

    @Test
    @DisplayName("PA7 — a wrong-policy bucket blocks structurally, no raw exception, no hold")
    void pa7PolicyMismatchBlocksStructurallyWithNoException() {
        LimitSnapshot snapshot = new LimitSnapshot(937L, "b", new BigDecimal("100.00"), null, null,
                BigDecimal.ZERO, 0, 0, false, CountingMethod.EACH_LINE, ConsumptionBasis.COMPANY_SHARE,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(snapshot));
        when(bucketRepository.findAllById(any()))
                .thenReturn(List.of(bucketOwnedBy(937L, 999L, ConsumptionBasis.COMPANY_SHARE)));

        Outcome outcome = runCanonicalPath(1, new BigDecimal("100.00"), new BigDecimal("100.00"), 100);

        assertThat(outcome.blocked()).isTrue();
        assertThat(outcome.blockReason()).contains("BUCKET_POLICY_MISMATCH");
        assertThat(outcome.holds()).isEmpty();
    }

    // ── PA8 — child + shared/counting parent ────────────────────────────

    @Test
    @DisplayName("PA8 — a monetary child and a counting-only parent each get their own hold, "
            + "correct identities, never summed")
    void pa8ChildAndSharedParentGetSeparateHolds() {
        LimitSnapshot child = new LimitSnapshot(938L, "child", new BigDecimal("1000.00"), null, null,
                new BigDecimal("200.00"), 0, 0, false, CountingMethod.EACH_LINE, ConsumptionBasis.COMPANY_SHARE,
                true, PERIOD_START, PERIOD_END);
        LimitSnapshot parent = new LimitSnapshot(939L, "parent", null, 5, null,
                BigDecimal.ZERO, 2, 0, false, CountingMethod.PER_VISIT, ConsumptionBasis.COMPANY_SHARE,
                false, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(child, parent));
        when(bucketRepository.findAllById(any())).thenReturn(List.of(
                bucketOwnedBy(938L, POLICY_ID, ConsumptionBasis.COMPANY_SHARE),
                bucketOwnedBy(939L, POLICY_ID, ConsumptionBasis.COMPANY_SHARE)));

        Outcome outcome = runCanonicalPath(1, new BigDecimal("300.00"), new BigDecimal("300.00"), 100);

        assertThat(outcome.holds()).hasSize(2);
        var childHold = outcome.holds().stream().filter(h -> h.bucketId().equals(938L)).findFirst().orElseThrow();
        var parentHold = outcome.holds().stream().filter(h -> h.bucketId().equals(939L)).findFirst().orElseThrow();
        assertThat(childHold.amountReserved()).isEqualByComparingTo("300.00");
        assertThat(childHold.timesLimit()).isNull();
        assertThat(parentHold.amountReserved()).isNull();
        assertThat(parentHold.timesLimit()).isEqualTo(5);
        assertThat(parentHold.timesReserved()).isEqualTo(1);
    }

    // ── PA9 — POLICY_GENERAL ─────────────────────────────────────────────

    @Test
    @DisplayName("PA9 — POLICY_GENERAL: no bucketId, always measured as the company share")
    void pa9PolicyGeneralHasNoBucketAndMeasuresCompanyShare() {
        LimitSnapshot general = new LimitSnapshot(null, "السقف السنوي العام", new BigDecimal("1000000.00"), null,
                null, new BigDecimal("200.00"), 0, 0, false, CountingMethod.EACH_LINE, ConsumptionBasis.COMPANY_SHARE,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(general));
        when(consumptionRepository.sumGeneralScopeReserved(eq(MEMBER_ID), eq(POLICY_ID), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        Outcome outcome = runCanonicalPath(1, new BigDecimal("300.00"), new BigDecimal("300.00"), 80);

        assertThat(outcome.holds()).hasSize(1);
        var hold = outcome.holds().get(0);
        assertThat(hold.bucketId()).isNull();
        assertThat(hold.limitScope()).isEqualTo("POLICY_GENERAL");
        assertThat(hold.consumptionBasis()).isEqualTo("COMPANY_SHARE");
        assertThat(hold.amountReserved()).isEqualByComparingTo("240.00");
        // No bucket fetch for a limit with no bucket.
        org.mockito.Mockito.verify(bucketRepository, org.mockito.Mockito.never()).findAllById(any());
    }

    // ── PA10 — LimitHold audit parity ───────────────────────────────────

    @Test
    @DisplayName("PA10 — audit parity: timesLimit/committedTimesBefore/reservedTimesBefore stay distinct from "
            + "actualRemainingTimesBefore/reservableTimesBefore -- 10/3/2 -> 7 and 5, never collapsed to one number")
    void pa10AuditFieldsStayDistinct() {
        LimitSnapshot snapshot = new LimitSnapshot(940L, "b", null, 10, null,
                BigDecimal.ZERO, 3, 0, false, CountingMethod.EACH_UNIT, ConsumptionBasis.COMPANY_SHARE,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(snapshot));
        when(bucketRepository.findAllById(any()))
                .thenReturn(List.of(bucketOwnedBy(940L, POLICY_ID, ConsumptionBasis.COMPANY_SHARE)));
        when(consumptionRepository.sumReservedTimes(MEMBER_ID, 940L, PERIOD_START, PERIOD_END)).thenReturn(2);

        Outcome outcome = runCanonicalPath(4, new BigDecimal("100.00"), new BigDecimal("100.00"), 100);

        var hold = outcome.holds().get(0);
        assertThat(hold.timesLimit()).isEqualTo(10);
        assertThat(hold.committedTimesBefore()).isEqualTo(3);
        assertThat(hold.reservedTimesBefore()).isEqualTo(2);
        assertThat(hold.actualRemainingTimesBefore()).isEqualTo(7); // 10 - 3, ignores the foreign reservation
        assertThat(hold.reservableTimesBefore()).isEqualTo(5); // 10 - 3 - 2, the one that actually binds
        assertThat(hold.actualRemainingTimesBefore())
                .as("actualRemaining and reservableAvailable must never collapse to the same number")
                .isNotEqualTo(hold.reservableTimesBefore());
    }

    @Test
    @DisplayName("PA10 golden gate — an ELIGIBLE_AMOUNT bucket and POLICY_GENERAL reserve DIFFERENT figures "
            + "on the SAME canonical decision: 1000 vs 800, never collapsed by unifying the decision")
    void pa10GoldenGateEligibleAmountVersusPolicyGeneral() {
        LimitSnapshot bucketSnapshot = new LimitSnapshot(941L, "eligible", new BigDecimal("5000.00"), null, null,
                new BigDecimal("1000.00"), 0, 0, false, CountingMethod.EACH_LINE, ConsumptionBasis.ELIGIBLE_AMOUNT,
                true, PERIOD_START, PERIOD_END);
        LimitSnapshot generalSnapshot = new LimitSnapshot(null, "السقف السنوي العام", new BigDecimal("1000000.00"),
                null, null, new BigDecimal("200.00"), 0, 0, false, CountingMethod.EACH_LINE,
                ConsumptionBasis.COMPANY_SHARE, true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(bucketSnapshot, generalSnapshot));
        when(bucketRepository.findAllById(any()))
                .thenReturn(List.of(bucketOwnedBy(941L, POLICY_ID, ConsumptionBasis.ELIGIBLE_AMOUNT)));
        when(consumptionRepository.sumGeneralScopeReserved(eq(MEMBER_ID), eq(POLICY_ID), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        // requestedAmount=1000, contractPrice=1000, coverage=80% ->
        // insideLimit/settlementBase=1000 (eligible), insurerFinalPayment=800 (company share).
        Outcome outcome = runCanonicalPath(1, new BigDecimal("1000.00"), new BigDecimal("1000.00"), 80);

        assertThat(outcome.holds()).hasSize(2);
        var bucketHold = outcome.holds().stream().filter(h -> "BUCKET".equals(h.limitScope())).findFirst()
                .orElseThrow();
        var generalHold = outcome.holds().stream().filter(h -> "POLICY_GENERAL".equals(h.limitScope())).findFirst()
                .orElseThrow();
        assertThat(bucketHold.amountReserved()).isEqualByComparingTo("1000.00");
        assertThat(generalHold.amountReserved()).isEqualByComparingTo("800.00");
        assertThat(bucketHold.amountReserved()).isNotEqualByComparingTo(generalHold.amountReserved());
    }

    // ── architectural guard ──────────────────────────────────────────────

    @Test
    @DisplayName("architectural guard — the whole canonical path never binds on a TIMES bucket when NONE apply, "
            + "and BindingConstraintType/UnifiedLimitStatus come from the resolver alone")
    void resolverAloneDecidesTheBindingConstraint() {
        LimitSnapshot snapshot = new LimitSnapshot(942L, "b", new BigDecimal("1000000.00"), null, null,
                BigDecimal.ZERO, 0, 0, false, CountingMethod.EACH_LINE, ConsumptionBasis.COMPANY_SHARE,
                true, PERIOD_START, PERIOD_END);
        when(bucketLimitService.findApplicable(RULE_ID, MEMBER_ID, SERVICE_DATE, EncounterType.OUTPATIENT, null))
                .thenReturn(List.of(snapshot));
        when(bucketRepository.findAllById(any()))
                .thenReturn(List.of(bucketOwnedBy(942L, POLICY_ID, ConsumptionBasis.COMPANY_SHARE)));

        Outcome outcome = runCanonicalPath(1, new BigDecimal("50.00"), new BigDecimal("50.00"), 100);

        assertThat(outcome.decision().bindingConstraintType()).isEqualTo(BindingConstraintType.NONE);
        assertThat(outcome.decision().status()).isEqualTo(UnifiedLimitStatus.LIMITED);
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
