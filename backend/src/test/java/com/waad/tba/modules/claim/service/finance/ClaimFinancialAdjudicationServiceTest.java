package com.waad.tba.modules.claim.service.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
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

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.BucketLimitSnapshot;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.BucketLimitSnapshotAdapter;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.LimitAxisType;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ReservationEvaluationMode;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitDecision;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitInput;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitResolver;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.service.MemberPolicyResolver;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import com.waad.tba.modules.preauthorization.repository.PreauthDecisionSnapshotRepository;

/**
 * P1.6: proves the fix {@code ClaimFinancialAdjudicationServiceCharacterizationTest}
 * was written to demand (deleted by this commit, per its own header --
 * "P1 can delete this test with a comment pointing at the commit that fixed
 * it"). A times-limit refusal Save-A already decided no longer gets
 * silently overwritten to zero by Engine B: {@code ClaimLine.unifiedLimitDecision}
 * carries that exact decision straight into {@link WaadFinancialEngine},
 * with no second resolver in between.
 *
 * This class's constructor signature is itself the structural proof that
 * {@code EffectiveLimitResolver}, {@code ApplicableLimitResolver},
 * {@code LimitBalanceReader} and {@code MultiLineMultiBucketEngine} cannot
 * be invoked from here anymore -- none of them are dependencies of
 * {@link ClaimFinancialAdjudicationService} any longer. {@code bucketLimitSnapshotAdapter}
 * is the one canonical resolver this class may still call, and only for a
 * line with no rider from Save-A (verified explicitly below).
 */
@ExtendWith(MockitoExtension.class)
class ClaimFinancialAdjudicationServiceTest {

    @Mock BenefitPolicyRepository policyRepository;
    @Mock MemberPolicyResolver memberPolicyResolver;
    @Mock PreauthDecisionSnapshotRepository decisionSnapshotRepository;
    @Mock BucketLimitSnapshotAdapter bucketLimitSnapshotAdapter;

    private ClaimFinancialAdjudicationService service;
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 3, 1);
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 12, 31);

    @BeforeEach
    void setUp() {
        // A REAL WaadFinancialEngine (no dependencies of its own) -- proving
        // genuine end-to-end money, not a mocked stand-in for the one
        // formula this whole phase exists to stop bypassing.
        service = new ClaimFinancialAdjudicationService(policyRepository, memberPolicyResolver,
                decisionSnapshotRepository, bucketLimitSnapshotAdapter, new WaadFinancialEngine());
    }

    private Claim claimWith(ClaimLine line) {
        Member member = Member.builder().id(500L).build();
        Claim claim = Claim.builder().member(member).serviceDate(SERVICE_DATE)
                .encounterType(EncounterType.OUTPATIENT).build();
        claim.setLines(List.of(line));
        BenefitPolicy policy = BenefitPolicy.builder().id(700L).build();
        when(memberPolicyResolver.resolveForOrFail(member, SERVICE_DATE)).thenReturn(policy);
        return claim;
    }

    @Test
    @DisplayName("P1.6 fix — the Physio gate: Save-A's own times-limit refusal now reaches the saved money, not silently erased")
    void savedMoneyMatchesTheDecisionSaveAAlreadyMade() {
        ClaimLine line = new ClaimLine();
        line.setAppliedRuleId(900L);
        line.setCoveragePercentSnapshot(75);
        line.setRequestedTotal(new BigDecimal("300.00"));
        line.setContractUnitPrice(new BigDecimal("100.00"));
        line.setQuantity(3);
        Claim claim = claimWith(line);

        // Exactly what CoverageEngineService (Save-A) would have decided for
        // "price=100, requested=3, remaining times=2": approved 2, refused 1.
        BucketLimitSnapshot snapshot = new BucketLimitSnapshot(932L, 700L, LimitAxisType.TIMES,
                CountingMethod.EACH_UNIT, BigDecimal.valueOf(20), BigDecimal.valueOf(18), BigDecimal.ZERO,
                BigDecimal.valueOf(2), PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(700L, 900L, 500L, SERVICE_DATE, EncounterType.OUTPATIENT,
                3, 0, new BigDecimal("100.00"), new BigDecimal("300.00"),
                null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision saveADecision = UnifiedLimitResolver.resolve(input, List.of(snapshot));
        line.setUnifiedLimitDecision(saveADecision);

        service.adjudicate(claim);

        // Allowed = 200 (2 of 3 units), company = 150 (75%), copay = 50, non-covered = 100.
        // patientShare is patientTotalResponsibility: copay (50) + the non-covered
        // limit excess (100) the member bears for the refused unit -- 150 total.
        assertThat(line.getLimitRefused()).isEqualByComparingTo("100.00");
        assertThat(line.getCompanyShare()).isEqualByComparingTo("150.00");
        assertThat(line.getPatientShare()).isEqualByComparingTo("150.00");
        assertThat(line.getApprovedQuantity()).isEqualTo(2);

        // The canonical resolver is never invoked when Save-A already
        // supplied the decision -- no re-resolution of any kind for this line.
        verifyNoInteractions(bucketLimitSnapshotAdapter);
    }

    @Test
    @DisplayName("a BLOCKED decision halts before WaadFinancialEngine ever runs, never surfaces as an ordinary approval")
    void blockedDecisionFailsClosed() {
        ClaimLine line = new ClaimLine();
        line.setAppliedRuleId(900L);
        line.setCoveragePercentSnapshot(100);
        line.setRequestedTotal(new BigDecimal("100.00"));
        line.setContractUnitPrice(new BigDecimal("100.00"));
        line.setQuantity(1);
        Claim claim = claimWith(line);
        line.setUnifiedLimitDecision(UnifiedLimitDecision.blocked(900L, List.of("BUCKET_POLICY_MISMATCH: test")));

        assertThatThrownBy(() -> service.adjudicate(claim))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIMIT_DECISION_BLOCKED");
    }

    @Test
    @DisplayName("no rider from Save-A -> resolves fresh through the SAME canonical resolver (BucketLimitSnapshotAdapter), not a legacy path")
    void missingRiderResolvesFreshThroughTheCanonicalAdapter() {
        ClaimLine line = new ClaimLine();
        line.setAppliedRuleId(900L);
        line.setCoveragePercentSnapshot(100);
        line.setRequestedTotal(new BigDecimal("400.00"));
        line.setContractUnitPrice(new BigDecimal("100.00"));
        line.setQuantity(4);
        Claim claim = claimWith(line);
        // No line.setUnifiedLimitDecision(...) -- simulates the approval-time
        // re-verification path, where a freshly loaded ClaimLine naturally
        // carries no transient state from Save-A.

        BucketLimitSnapshot snapshot = new BucketLimitSnapshot(940L, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(1000), BigDecimal.valueOf(700), BigDecimal.ZERO,
                BigDecimal.valueOf(300), PERIOD_START, PERIOD_END);
        when(bucketLimitSnapshotAdapter.buildForNormalClaim(700L, 900L, 500L, SERVICE_DATE,
                EncounterType.OUTPATIENT, null))
                .thenReturn(BucketLimitSnapshotAdapter.Result.of(List.of(
                        new com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ResolvedLimitItem(snapshot,
                                new com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ResolvedLimitDescriptor(
                                        "BUCKET:940", 940L,
                                        com.waad.tba.modules.benefitpolicy.entity.ClaimLineLimitSnapshot.SourceType.POLICY_DEFAULT,
                                        com.waad.tba.modules.benefitpolicy.enums.BenefitScopeType.CATEGORY,
                                        com.waad.tba.modules.benefitpolicy.enums.BeneficiaryScopeType.MEMBER,
                                        900L, 1L, "ANNUAL", PERIOD_START, PERIOD_END)))));

        service.adjudicate(claim);

        // 1000 configured - 700 committed = 300 actually available -- only
        // 300 of the requested 400 could be approved.
        assertThat(line.getLimitRefused()).isEqualByComparingTo("100.00");
        assertThat(line.getCompanyShare()).isEqualByComparingTo("300.00");
        assertThat(line.getUnifiedLimitDecision()).isNotNull();
    }

    @Test
    @DisplayName("P1.11.3 (D3/Batch) — two lines, no rider, same day-limited bucket + same service date: only the first line spends the day")
    void freshResolveAppliesTheSameOncePerBatchDayRuleAcrossLines() {
        ClaimLine line1 = new ClaimLine();
        line1.setAppliedRuleId(900L);
        line1.setCoveragePercentSnapshot(100);
        line1.setRequestedTotal(new BigDecimal("50.00"));
        line1.setContractUnitPrice(new BigDecimal("50.00"));
        line1.setQuantity(1);
        ClaimLine line2 = new ClaimLine();
        line2.setAppliedRuleId(900L);
        line2.setCoveragePercentSnapshot(100);
        line2.setRequestedTotal(new BigDecimal("50.00"));
        line2.setContractUnitPrice(new BigDecimal("50.00"));
        line2.setQuantity(1);
        // No unifiedLimitDecision on either line -- both fall back to
        // resolveCanonically, sharing ONE freshResolveContext for the claim.

        Member member = Member.builder().id(500L).build();
        Claim claim = Claim.builder().member(member).serviceDate(SERVICE_DATE)
                .encounterType(EncounterType.OUTPATIENT).build();
        claim.setLines(List.of(line1, line2));
        BenefitPolicy policy = BenefitPolicy.builder().id(700L).build();
        when(memberPolicyResolver.resolveForOrFail(member, SERVICE_DATE)).thenReturn(policy);

        BucketLimitSnapshot daysSnapshot = new BucketLimitSnapshot(933L, 700L, LimitAxisType.DAYS,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(5), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(5), PERIOD_START, PERIOD_END);
        var descriptor = new com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ResolvedLimitDescriptor(
                "BUCKET:933", 933L, com.waad.tba.modules.benefitpolicy.entity.ClaimLineLimitSnapshot.SourceType.POLICY_DEFAULT,
                com.waad.tba.modules.benefitpolicy.enums.BenefitScopeType.CATEGORY,
                com.waad.tba.modules.benefitpolicy.enums.BeneficiaryScopeType.MEMBER, 900L, 1L, "ANNUAL",
                PERIOD_START, PERIOD_END);
        // Both lines share appliedRuleId=900L, so the SAME stub answers both
        // of resolveCanonically's two calls -- exactly like two lines
        // genuinely reading the same live DB state twice.
        when(bucketLimitSnapshotAdapter.buildForNormalClaim(700L, 900L, 500L, SERVICE_DATE,
                EncounterType.OUTPATIENT, null))
                .thenReturn(BucketLimitSnapshotAdapter.Result.of(List.of(
                        new com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ResolvedLimitItem(
                                daysSnapshot, descriptor))));

        service.adjudicate(claim);

        assertThat(line1.getUnifiedLimitDecision().approvedDays())
                .as("the first line to reach this bucket+date spends the day").isEqualTo(1);
        assertThat(line2.getUnifiedLimitDecision().approvedDays())
                .as("the second line, same bucket + same service date, must not spend a second day").isZero();
    }

    /**
     * P1.12.5 (U3) — the SAME numbers P1.12.3's PA3a proved for PreAuth
     * (bucket amount 1000/800 committed, times 4/1 committed, requested 3
     * units at 400 total -> approvedQuantity=1), reproduced on the Claims
     * side through the identical {@link UnifiedLimitResolver}. Same-bucket
     * AMOUNT+TIMES cross-axis whole-unit constraint is ONE canonical
     * behavior, not a PreAuth-only rule: a pre-authorization that promised 3
     * units this same bucket's own balance can only fund 1 of would leave
     * the claim it converts into honoring only 1 -- exactly the
     * cross-mode split P1.12 exists to remove.
     */
    @Test
    @DisplayName("U3 — same-bucket AMOUNT+TIMES cross-axis whole-unit constraint: PreAuth's PA3a numbers, on Claims")
    void sameBucketAmountAndTimesApplyTheCrossAxisWholeUnitConstraintOnClaimsToo() {
        ClaimLine line = new ClaimLine();
        line.setAppliedRuleId(900L);
        line.setCoveragePercentSnapshot(80);
        line.setRequestedTotal(new BigDecimal("400.00"));
        line.setContractUnitPrice(new BigDecimal("133.33"));
        line.setQuantity(3);
        Claim claim = claimWith(line);

        BucketLimitSnapshot amountAxis = new BucketLimitSnapshot(950L, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_UNIT, BigDecimal.valueOf(1000), BigDecimal.valueOf(800), BigDecimal.ZERO,
                BigDecimal.valueOf(200), PERIOD_START, PERIOD_END);
        BucketLimitSnapshot timesAxis = new BucketLimitSnapshot(950L, 700L, LimitAxisType.TIMES,
                CountingMethod.EACH_UNIT, BigDecimal.valueOf(4), BigDecimal.valueOf(1), BigDecimal.ZERO,
                BigDecimal.valueOf(3), PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(700L, 900L, 500L, SERVICE_DATE, EncounterType.OUTPATIENT,
                3, 0, new BigDecimal("133.33"), new BigDecimal("400.00"),
                null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(amountAxis, timesAxis));
        assertThat(decision.approvedQuantity())
                .as("200 remaining / (400/3 per unit) affords only 1 whole unit, tighter than the 3 the "
                        + "occurrence ceiling alone would allow")
                .isEqualTo(1);
        line.setUnifiedLimitDecision(decision);

        service.adjudicate(claim);

        assertThat(line.getApprovedQuantity()).isEqualTo(1);
    }
}
