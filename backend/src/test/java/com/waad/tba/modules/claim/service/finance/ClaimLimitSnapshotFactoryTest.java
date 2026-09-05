package com.waad.tba.modules.claim.service.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import com.waad.tba.modules.benefitpolicy.entity.ClaimLineLimitSnapshot;
import com.waad.tba.modules.benefitpolicy.enums.BeneficiaryScopeType;
import com.waad.tba.modules.benefitpolicy.enums.BenefitScopeType;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.BucketLimitSnapshot;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.LimitAxisType;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ReservationEvaluationMode;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ResolvedLimitDescriptor;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ResolvedLimitItem;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitDecision;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitInput;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitResolver;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.MemberPolicyAssignment;
import com.waad.tba.modules.member.service.MemberPolicyResolver;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import jakarta.persistence.EntityManager;

/**
 * P1.6.x — Snapshot Metadata Decoupling gates SMD1/SMD2/SMD3/SMD5 (SMD4 lives
 * in {@link ClaimSaveHasNoLegacyLimitResolverTest}). Proves
 * {@link ClaimLimitSnapshotFactory} succeeds purely from
 * {@code Claim + UnifiedLimitDecision + ResolvedLimitItem}s -- no repository
 * dependency exists on the class at all -- and that every audit column
 * traces to either the decision's own {@code consumptionTargets()} or its
 * paired descriptor, matched by {@code limitKey}, never by array position or
 * a bucketId assumed to always exist.
 */
@ExtendWith(MockitoExtension.class)
class ClaimLimitSnapshotFactoryTest {

    @Mock EntityManager entityManager;
    @Mock MemberPolicyResolver memberPolicyResolver;

    private ClaimLimitSnapshotFactory factory;
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 12, 31);

    @BeforeEach
    void setUp() {
        factory = new ClaimLimitSnapshotFactory(entityManager, memberPolicyResolver);
        lenient().when(entityManager.getReference(any(Class.class), any())).thenReturn(null);
    }

    private Claim claimWithOneLine(ClaimLine line) {
        Member member = Member.builder().id(500L).build();
        Claim claim = Claim.builder().member(member).serviceDate(PERIOD_START).build();
        claim.setLines(List.of(line));
        MemberPolicyAssignment assignment = new MemberPolicyAssignment();
        assignment.setId(77L);
        when(memberPolicyResolver.resolveAssignmentFor(member, PERIOD_START)).thenReturn(Optional.of(assignment));
        return claim;
    }

    private static WaadFinancialEngine.Result financial(BigDecimal bindingAvailableLimit, BigDecimal consumption) {
        return new WaadFinancialEngine.Result(new BigDecimal("60.00"), new BigDecimal("60.00"), BigDecimal.ZERO,
                new BigDecimal("60.00"), WaadFinancialEngine.LimitMode.LIMITED, bindingAvailableLimit,
                new BigDecimal("60.00"), BigDecimal.ZERO, consumption, BigDecimal.ZERO, 100, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("60.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("60.00"), BigDecimal.ZERO, new BigDecimal("60.00"));
    }

    @Test
    @DisplayName("SMD1 — a real-bucket AMOUNT consumption target writes its row entirely from the decision + its descriptor")
    void smd1_normalBucketSnapshotParity() {
        BucketLimitSnapshot target = new BucketLimitSnapshot(910L, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(500), BigDecimal.valueOf(100), BigDecimal.ZERO,
                BigDecimal.valueOf(400), PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(700L, 42L, 500L, PERIOD_START, EncounterType.OUTPATIENT,
                0, 0, BigDecimal.ZERO, new BigDecimal("60.00"), null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(target));

        ResolvedLimitDescriptor descriptor = new ResolvedLimitDescriptor(ResolvedLimitDescriptor.bucketKey(910L),
                910L, ClaimLineLimitSnapshot.SourceType.POLICY_DEFAULT, BenefitScopeType.CATEGORY,
                BeneficiaryScopeType.MEMBER, 42L, 88L, "ANNUAL", PERIOD_START, PERIOD_END);

        ClaimLine line = new ClaimLine();
        line.setUnifiedLimitDecision(decision);
        line.setResolvedLimitItems(List.of(new ResolvedLimitItem(target, descriptor)));
        Claim claim = claimWithOneLine(line);
        var adjudication = new ClaimFinancialAdjudicationService.AdjudicationResult(
                List.of(financial(new BigDecimal("400.00"), new BigDecimal("60.00"))));

        List<ClaimLineLimitSnapshot> rows = factory.build(claim, adjudication);

        assertThat(rows).hasSize(1);
        ClaimLineLimitSnapshot row = rows.get(0);
        assertThat(row.getBenefitScopeType()).isEqualTo(BenefitScopeType.CATEGORY);
        assertThat(row.getBeneficiaryScopeType()).isEqualTo(BeneficiaryScopeType.MEMBER);
        assertThat(row.getLimitSemanticKey()).isEqualTo("BUCKET:910");
        assertThat(row.getPeriodType()).isEqualTo("ANNUAL");
        assertThat(row.getSourceType()).isEqualTo(ClaimLineLimitSnapshot.SourceType.POLICY_DEFAULT);
        assertThat(row.getEffectiveLimit()).isEqualByComparingTo("500");
        assertThat(row.getConsumedBefore()).isEqualByComparingTo("100");
        assertThat(row.getAvailableBefore()).isEqualByComparingTo("400");
        assertThat(row.getAvailableAfter()).isEqualByComparingTo("340.00");
        assertThat(row.getMemberPolicyAssignmentId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("SMD2 — the POLICY_GENERAL ceiling writes with no bucketId, keyed POLICY_GENERAL:<policyId>")
    void smd2_policyGeneralDescriptorWithoutBucketId() {
        BucketLimitSnapshot target = new BucketLimitSnapshot(null, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(1000), BigDecimal.valueOf(200), BigDecimal.ZERO,
                BigDecimal.valueOf(800), PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(700L, 42L, 500L, PERIOD_START, EncounterType.OUTPATIENT,
                0, 0, BigDecimal.ZERO, new BigDecimal("60.00"), null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(target));

        ResolvedLimitDescriptor descriptor = new ResolvedLimitDescriptor(
                ResolvedLimitDescriptor.policyGeneralKey(700L), null,
                ClaimLineLimitSnapshot.SourceType.POLICY_DEFAULT, BenefitScopeType.POLICY_GENERAL,
                BeneficiaryScopeType.MEMBER, 42L, null, "ANNUAL", PERIOD_START, PERIOD_END);

        ClaimLine line = new ClaimLine();
        line.setUnifiedLimitDecision(decision);
        line.setResolvedLimitItems(List.of(new ResolvedLimitItem(target, descriptor)));
        Claim claim = claimWithOneLine(line);
        var adjudication = new ClaimFinancialAdjudicationService.AdjudicationResult(
                List.of(financial(new BigDecimal("800.00"), new BigDecimal("60.00"))));

        List<ClaimLineLimitSnapshot> rows = factory.build(claim, adjudication);

        assertThat(rows).hasSize(1);
        ClaimLineLimitSnapshot row = rows.get(0);
        assertThat(row.getLimitSemanticKey()).isEqualTo("POLICY_GENERAL:700");
        assertThat(row.getBenefitScopeType()).isEqualTo(BenefitScopeType.POLICY_GENERAL);
        assertThat(row.getBucket()).isNull();
        assertThat(row.getBenefitGroup()).isNull();
    }

    @Test
    @DisplayName("SMD3 — POLICY_GENERAL's beneficiaryScopeType default (MEMBER) survives onto the row, not left null")
    void smd3_beneficiaryScopeTypeDefaultMemberPreserved() {
        BucketLimitSnapshot target = new BucketLimitSnapshot(null, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(1000), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(1000), PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(700L, 42L, 500L, PERIOD_START, EncounterType.OUTPATIENT,
                0, 0, BigDecimal.ZERO, new BigDecimal("60.00"), null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(target));

        // Exactly what BucketLimitSnapshotAdapter.descriptorFor synthesizes for
        // a null bucketId -- no policy-general FAMILY sharing exists today.
        ResolvedLimitDescriptor descriptor = new ResolvedLimitDescriptor(
                ResolvedLimitDescriptor.policyGeneralKey(700L), null,
                ClaimLineLimitSnapshot.SourceType.POLICY_DEFAULT, BenefitScopeType.POLICY_GENERAL,
                BeneficiaryScopeType.MEMBER, 42L, null, "ANNUAL", PERIOD_START, PERIOD_END);

        ClaimLine line = new ClaimLine();
        line.setUnifiedLimitDecision(decision);
        line.setResolvedLimitItems(List.of(new ResolvedLimitItem(target, descriptor)));
        Claim claim = claimWithOneLine(line);
        var adjudication = new ClaimFinancialAdjudicationService.AdjudicationResult(
                List.of(financial(new BigDecimal("1000.00"), new BigDecimal("60.00"))));

        ClaimLineLimitSnapshot row = factory.build(claim, adjudication).get(0);

        assertThat(row.getBeneficiaryScopeType()).isEqualTo(BeneficiaryScopeType.MEMBER);
    }

    @Test
    @DisplayName("SMD5 — two consumed buckets never cross-wire: each row's numbers and metadata come from the SAME bucket")
    void smd5_metadataAndNumericDecisionShareTheSameCanonicalLimitKey() {
        BucketLimitSnapshot bucketA = new BucketLimitSnapshot(101L, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(300), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(300), PERIOD_START, PERIOD_END);
        BucketLimitSnapshot bucketB = new BucketLimitSnapshot(102L, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(900), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(900), PERIOD_START, PERIOD_END);
        ResolvedLimitDescriptor descriptorA = new ResolvedLimitDescriptor(ResolvedLimitDescriptor.bucketKey(101L),
                101L, ClaimLineLimitSnapshot.SourceType.POLICY_DEFAULT, BenefitScopeType.SERVICE,
                BeneficiaryScopeType.MEMBER, 42L, 11L, "ANNUAL", PERIOD_START, PERIOD_END);
        ResolvedLimitDescriptor descriptorB = new ResolvedLimitDescriptor(ResolvedLimitDescriptor.bucketKey(102L),
                102L, ClaimLineLimitSnapshot.SourceType.POLICY_DEFAULT, BenefitScopeType.GROUP,
                BeneficiaryScopeType.MEMBER, 42L, 22L, "POLICY_PERIOD", PERIOD_START, PERIOD_END);

        // Both bucketA and bucketB were merely EVALUATED (two parent/child
        // ceilings on the same rule), but only bucketA is a real
        // consumptionTarget -- built by hand here since that decision is an
        // internal UnifiedLimitResolver computation this test does not need
        // to reproduce; what matters is the FACTORY's own key-based lookup,
        // not how the resolver decided which bucket binds.
        UnifiedLimitDecision decision = new UnifiedLimitDecision(42L, List.of(101L, 102L), List.of(), 0, 0, 0, 0, 0,
                0, new UnifiedLimitDecision.LimitAxis(BigDecimal.valueOf(300), BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.valueOf(300)),
                UnifiedLimitDecision.LimitAxis.unconfigured(), UnifiedLimitDecision.LimitAxis.unconfigured(),
                com.waad.tba.modules.benefitpolicy.service.unifiedlimit.BindingConstraintType.AMOUNT, 101L,
                new BigDecimal("60.00"), com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitStatus.LIMITED,
                List.of(bucketA));

        ClaimLine line = new ClaimLine();
        line.setUnifiedLimitDecision(decision);
        line.setResolvedLimitItems(List.of(new ResolvedLimitItem(bucketA, descriptorA),
                new ResolvedLimitItem(bucketB, descriptorB)));
        Claim claim = claimWithOneLine(line);
        var adjudication = new ClaimFinancialAdjudicationService.AdjudicationResult(
                List.of(financial(new BigDecimal("300.00"), new BigDecimal("60.00"))));

        List<ClaimLineLimitSnapshot> rows = factory.build(claim, adjudication);

        // Only bucketA was consumed -- bucketB (merely evaluated) gets no row.
        assertThat(rows).hasSize(1);
        ClaimLineLimitSnapshot row = rows.get(0);
        assertThat(row.getLimitSemanticKey()).isEqualTo("BUCKET:101");
        assertThat(row.getBenefitScopeType()).isEqualTo(BenefitScopeType.SERVICE); // bucketA's own scope, never bucketB's GROUP
        assertThat(row.getEffectiveLimit()).isEqualByComparingTo("300"); // bucketA's own configured, never bucketB's 900
    }

    @Test
    @DisplayName("a consumed limit with no matching descriptor fails closed instead of guessing")
    void missingDescriptorFailsClosed() {
        BucketLimitSnapshot target = new BucketLimitSnapshot(910L, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(500), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(500), PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(700L, 42L, 500L, PERIOD_START, EncounterType.OUTPATIENT,
                0, 0, BigDecimal.ZERO, new BigDecimal("60.00"), null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(target));

        ClaimLine line = new ClaimLine();
        line.setUnifiedLimitDecision(decision);
        line.setResolvedLimitItems(List.of()); // no descriptor for bucket 910
        Claim claim = claimWithOneLine(line);
        var adjudication = new ClaimFinancialAdjudicationService.AdjudicationResult(
                List.of(financial(new BigDecimal("500.00"), new BigDecimal("60.00"))));

        assertThatThrownBy(() -> factory.build(claim, adjudication))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LIMIT_SNAPSHOT_MISSING_DESCRIPTOR");
    }
}
