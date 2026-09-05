package com.waad.tba.modules.claim.service.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption;
import com.waad.tba.modules.benefitpolicy.entity.ClaimLineLimitSnapshot;
import com.waad.tba.modules.benefitpolicy.enums.BeneficiaryScopeType;
import com.waad.tba.modules.benefitpolicy.enums.BenefitScopeType;
import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.benefitpolicy.enums.LimitPeriodType;
import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitRuleBucketRepository;
import com.waad.tba.modules.benefitpolicy.service.BenefitBucketLedgerService;
import com.waad.tba.modules.benefitpolicy.service.BenefitConsumptionEntryWriter;
import com.waad.tba.modules.benefitpolicy.service.LimitBalanceReader;
import com.waad.tba.modules.benefitpolicy.service.TimesLimitEvaluator;
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
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.MemberPolicyAssignment;
import com.waad.tba.modules.member.service.MemberPolicyResolver;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import jakarta.persistence.EntityManager;

/**
 * P1.11.6 — the final cross-cutting identity gate: for the SAME
 * {@code UnifiedLimitDecision} + {@code ResolvedLimitItem}s, the target
 * identities {@link ClaimLimitSnapshotFactory} writes into
 * {@code claim_line_limit_snapshots} and the target identities
 * {@link BenefitBucketLedgerService} commits into
 * {@code benefit_bucket_consumptions} are the EXACT SAME SET, matched by
 * {@code limitKey} -- never by array position, never by an independently
 * re-walked bucket hierarchy on either side.
 *
 * Each side already has its own unit tests (SMD5 for the snapshot side, CW4
 * for the ledger side); this test's only job is to prove they agree with
 * EACH OTHER on the same canonical decision, not merely each with itself.
 */
class SnapshotEqualsLedgerIdentityTest {

    private static final LocalDate PERIOD_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 12, 31);
    private static final LocalDate SERVICE_DATE = LocalDate.of(2026, 3, 1);

    @Test
    @DisplayName("P1.11.6 — snapshot identities == ledger identities == canonical target identities (child + parent)")
    void snapshotAndLedgerAgreeOnTheSameCanonicalTargetIdentities() {
        // ── the one canonical decision, shared by both sides ──────────
        BucketLimitSnapshot child = new BucketLimitSnapshot(950L, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(500), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(500), PERIOD_START, PERIOD_END);
        BucketLimitSnapshot parent = new BucketLimitSnapshot(951L, 700L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, BigDecimal.valueOf(5000), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(5000), PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(700L, 42L, 10L, SERVICE_DATE, EncounterType.OUTPATIENT,
                0, 0, BigDecimal.ZERO, new BigDecimal("400.00"), null, ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(child, parent));

        ResolvedLimitDescriptor childDescriptor = new ResolvedLimitDescriptor("BUCKET:950", 950L,
                ClaimLineLimitSnapshot.SourceType.POLICY_DEFAULT, BenefitScopeType.CATEGORY,
                BeneficiaryScopeType.MEMBER, 42L, 15L, "ANNUAL", PERIOD_START, PERIOD_END);
        ResolvedLimitDescriptor parentDescriptor = new ResolvedLimitDescriptor("BUCKET:951", 951L,
                ClaimLineLimitSnapshot.SourceType.POLICY_DEFAULT, BenefitScopeType.GROUP,
                BeneficiaryScopeType.MEMBER, 42L, 15L, "POLICY_PERIOD", PERIOD_START, PERIOD_END);
        List<ResolvedLimitItem> items = List.of(
                new ResolvedLimitItem(child, childDescriptor), new ResolvedLimitItem(parent, parentDescriptor));

        // The canonical targets themselves -- the reference the other two must match.
        List<com.waad.tba.modules.benefitpolicy.service.unifiedlimit.CanonicalConsumptionTarget> canonicalTargets =
                com.waad.tba.modules.benefitpolicy.service.unifiedlimit.CanonicalConsumptionTargetBuilder.build(
                        decision, new BigDecimal("400.00"), items, SERVICE_DATE);
        Set<String> canonicalKeys = canonicalTargets.stream()
                .map(com.waad.tba.modules.benefitpolicy.service.unifiedlimit.CanonicalConsumptionTarget::limitKey)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(canonicalKeys).containsExactlyInAnyOrder("BUCKET:950", "BUCKET:951");

        // ── side A: ClaimLimitSnapshotFactory ──────────────────────────
        EntityManager entityManager = mock(EntityManager.class);
        MemberPolicyResolver snapshotMemberPolicyResolver = mock(MemberPolicyResolver.class);
        lenient().when(entityManager.getReference(any(Class.class), any())).thenReturn(null);
        ClaimLimitSnapshotFactory snapshotFactory =
                new ClaimLimitSnapshotFactory(entityManager, snapshotMemberPolicyResolver);

        Member snapshotMember = Member.builder().id(500L).build();
        Claim snapshotClaim = Claim.builder().member(snapshotMember).serviceDate(SERVICE_DATE).build();
        ClaimLine snapshotLine = new ClaimLine();
        snapshotLine.setUnifiedLimitDecision(decision);
        snapshotLine.setResolvedLimitItems(items);
        snapshotClaim.setLines(List.of(snapshotLine));
        MemberPolicyAssignment assignment = new MemberPolicyAssignment();
        assignment.setId(77L);
        when(snapshotMemberPolicyResolver.resolveAssignmentFor(snapshotMember, SERVICE_DATE))
                .thenReturn(Optional.of(assignment));
        var adjudication = new ClaimFinancialAdjudicationService.AdjudicationResult(List.of(
                new WaadFinancialEngine.Result(new BigDecimal("400.00"), new BigDecimal("400.00"), BigDecimal.ZERO,
                        new BigDecimal("400.00"), WaadFinancialEngine.LimitMode.LIMITED, new BigDecimal("5000.00"),
                        new BigDecimal("400.00"), BigDecimal.ZERO, new BigDecimal("400.00"), BigDecimal.ZERO, 100,
                        BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("400.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("400.00"), BigDecimal.ZERO, new BigDecimal("400.00"))));

        Set<String> snapshotKeys = snapshotFactory.build(snapshotClaim, adjudication).stream()
                .map(ClaimLineLimitSnapshot::getLimitSemanticKey)
                .collect(java.util.stream.Collectors.toSet());

        // ── side B: BenefitBucketLedgerService ─────────────────────────
        ClaimRepository claimRepository = mock(ClaimRepository.class);
        MemberPolicyResolver ledgerMemberPolicyResolver = mock(MemberPolicyResolver.class);
        BenefitPolicyRepository benefitPolicyRepository = mock(BenefitPolicyRepository.class);
        BenefitRuleBucketRepository ruleBucketRepository = mock(BenefitRuleBucketRepository.class);
        BenefitLimitBucketRepository bucketRepository = mock(BenefitLimitBucketRepository.class);
        BenefitBucketConsumptionRepository consumptionRepository = mock(BenefitBucketConsumptionRepository.class);
        LimitBalanceReader limitBalanceReader = mock(LimitBalanceReader.class);
        BenefitBucketLedgerService ledgerService = new BenefitBucketLedgerService(
                claimRepository, ledgerMemberPolicyResolver, benefitPolicyRepository, ruleBucketRepository,
                bucketRepository, consumptionRepository, new BenefitConsumptionEntryWriter(consumptionRepository),
                new TimesLimitEvaluator(), limitBalanceReader);

        BenefitPolicy policy = BenefitPolicy.builder().id(700L)
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 12, 31)).build();
        Member ledgerMember = Member.builder().id(10L).benefitPolicy(policy).build();
        ClaimLine ledgerLine = ClaimLine.builder().id(100L).appliedRuleId(50L)
                .limitConsumption(new BigDecimal("400.00")).calculationVersion(1).build();
        Claim ledgerClaim = Claim.builder().id(20L).member(ledgerMember).serviceDate(SERVICE_DATE)
                .lines(List.of(ledgerLine)).build();
        ledgerLine.setClaim(ledgerClaim);
        ledgerLine.setUnifiedLimitDecision(decision);
        ledgerLine.setResolvedLimitItems(items);

        BenefitLimitBucket childBucket = BenefitLimitBucket.builder().id(950L).policy(policy)
                .code("CHILD").nameAr("فرع").amountLimit(new BigDecimal("500.00")).periodType(LimitPeriodType.ANNUAL)
                .countingMethod(CountingMethod.EACH_LINE).consumptionBasis(ConsumptionBasis.ELIGIBLE_AMOUNT)
                .active(true).build();
        BenefitLimitBucket parentBucket = BenefitLimitBucket.builder().id(951L).policy(policy)
                .code("PARENT").nameAr("أصل").amountLimit(new BigDecimal("5000.00")).periodType(LimitPeriodType.ANNUAL)
                .countingMethod(CountingMethod.EACH_LINE).consumptionBasis(ConsumptionBasis.ELIGIBLE_AMOUNT)
                .active(true).build();

        lenient().when(ledgerMemberPolicyResolver.resolveFor(any(Member.class), any(LocalDate.class)))
                .thenReturn(Optional.of(policy));
        lenient().when(claimRepository.findById(20L)).thenReturn(Optional.of(ledgerClaim));
        lenient().when(bucketRepository.findByIdForUpdate(950L)).thenReturn(Optional.of(childBucket));
        lenient().when(bucketRepository.findByIdForUpdate(951L)).thenReturn(Optional.of(parentBucket));
        lenient().when(consumptionRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        lenient().when(consumptionRepository.existsUnledgeredApprovedBucketClaim(anyLong(), anyLong(), any()))
                .thenReturn(false);
        lenient().when(consumptionRepository.sumCommittedAmount(any(), any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(consumptionRepository.sumCommittedTimes(any(), any(), any(), any(), any())).thenReturn(0);
        lenient().when(consumptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ledgerService.commitClaim(20L);

        ArgumentCaptor<BenefitBucketConsumption> captor = ArgumentCaptor.forClass(BenefitBucketConsumption.class);
        org.mockito.Mockito.verify(consumptionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        Set<String> ledgerKeys = captor.getAllValues().stream()
                .map(row -> "BUCKET:" + row.getBucket().getId())
                .collect(java.util.stream.Collectors.toSet());

        // ── the gate itself ─────────────────────────────────────────────
        assertThat(snapshotKeys).as("snapshot target identities must equal the canonical targets' own identities")
                .isEqualTo(canonicalKeys);
        assertThat(ledgerKeys).as("ledger target identities must equal the canonical targets' own identities")
                .isEqualTo(canonicalKeys);
    }
}
