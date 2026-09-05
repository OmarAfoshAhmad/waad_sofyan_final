package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.entity.*;
import com.waad.tba.modules.benefitpolicy.enums.*;
import com.waad.tba.modules.benefitpolicy.repository.*;
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
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BenefitBucketLedgerServiceTest {

    @Mock ClaimRepository claimRepository;
    @Mock BenefitPolicyRepository benefitPolicyRepository;
    @Mock BenefitRuleBucketRepository ruleBucketRepository;
    @Mock BenefitLimitBucketRepository bucketRepository;
    @Mock BenefitBucketConsumptionRepository consumptionRepository;
    @Mock LimitBalanceReader limitBalanceReader;

    private BenefitBucketLedgerService service;
    private BenefitPolicy policy;
    private BenefitLimitBucket bucket;
    private com.waad.tba.modules.member.service.MemberPolicyResolver memberPolicyResolver;
    private Claim claim;
    private ClaimLine line;

    @BeforeEach
    void setUp() {
        memberPolicyResolver = org.mockito.Mockito.mock(
                com.waad.tba.modules.member.service.MemberPolicyResolver.class);
        // The real gate, over the same mocked repository: the extraction must
        // not change what is written, only where the write is issued from.
        service = new BenefitBucketLedgerService(
                claimRepository, memberPolicyResolver, benefitPolicyRepository, ruleBucketRepository,
                bucketRepository, consumptionRepository,
                new BenefitConsumptionEntryWriter(consumptionRepository),
                new TimesLimitEvaluator(), limitBalanceReader);

        policy = BenefitPolicy.builder()
                .id(1L)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .build();
        Member member = Member.builder().id(10L).benefitPolicy(policy).build();
        line = ClaimLine.builder()
                .id(100L)
                .appliedRuleId(50L)
                .companyShare(new BigDecimal("200.00"))
                .approvedAmount(new BigDecimal("200.00"))
                .limitConsumption(new BigDecimal("200.00"))
                .totalPrice(new BigDecimal("400.00"))
                .limitRefused(new BigDecimal("200.00"))
                .quantity(1)
                .calculationVersion(1)
                .build();
        claim = Claim.builder()
                .id(20L)
                .member(member)
                .serviceDate(LocalDate.of(2026, 7, 20))
                .lines(List.of(line))
                .build();
        line.setClaim(claim);

        bucket = BenefitLimitBucket.builder()
                .id(70L)
                .policy(policy)
                .code("MRI")
                .nameAr("سقف الرنين")
                .amountLimit(new BigDecimal("1500.00"))
                .periodType(LimitPeriodType.ANNUAL)
                .countingMethod(CountingMethod.EACH_LINE)
                .consumptionBasis(ConsumptionBasis.ELIGIBLE_AMOUNT)
                .active(true)
                .build();
        BenefitRuleBucket link = BenefitRuleBucket.builder().bucket(bucket).build();

        lenient().when(memberPolicyResolver.resolveFor(any(Member.class), any(LocalDate.class)))
                .thenReturn(Optional.of(policy));
        lenient().when(claimRepository.findById(20L)).thenReturn(Optional.of(claim));
        lenient().when(ruleBucketRepository.findByRuleIdOrderByConsumptionOrder(50L))
                .thenReturn(List.of(link));
        lenient().when(bucketRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(bucket));
        lenient().when(consumptionRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        lenient().when(consumptionRepository.existsUnledgeredApprovedBucketClaim(anyLong(), anyLong(), any()))
                .thenReturn(false);
        lenient().when(consumptionRepository.existsUnledgeredApprovedGeneralClaim(anyLong(), anyLong(), any()))
                .thenReturn(false);
        lenient().when(consumptionRepository.sumCommittedAmount(any(), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("1300.00"));
        lenient().when(consumptionRepository.sumCommittedTimes(any(), any(), any(), any(), any()))
                .thenReturn(1);
        lenient().when(consumptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("الاعتماد الجزئي يسجل المبلغ المسموح بعد السقف لا إجمالي الخدمة")
    void partialApprovalConsumesPostLimitEligibleAmount() {
        service.commitClaim(20L);

        ArgumentCaptor<BenefitBucketConsumption> captor =
                ArgumentCaptor.forClass(BenefitBucketConsumption.class);
        verify(consumptionRepository).save(captor.capture());
        BenefitBucketConsumption saved = captor.getValue();

        assertEquals(0, new BigDecimal("200.00").compareTo(saved.getApprovedAmount()));
        assertEquals(BenefitBucketConsumption.Status.COMMITTED, saved.getStatus());
        assertEquals(LocalDate.of(2026, 1, 1), saved.getPeriodStart());
        assertEquals(LocalDate.of(2026, 12, 31), saved.getPeriodEnd());
    }

    @Test
    @DisplayName("وثيقة جهة العمل تُستخدم في الدفتر عندما لا توجد وثيقة مباشرة للمستفيد")
    void employerPolicyFallbackIsCommitted() {
        // The ledger no longer resolves the policy itself -- it asks
        // MemberPolicyResolver for the policy in force on the claim's SERVICE
        // DATE, and any employer-level fallback now lives there (and is tested
        // in MemberPolicyResolverIntegrationTest against a real database). What
        // this test still pins is that the ledger commits a consumption row for
        // whatever the resolver returns, including for a member whose own
        // pointer is null.
        claim.getMember().setBenefitPolicy(null);
        claim.getMember().setEmployer(Employer.builder().id(5L).build());
        when(memberPolicyResolver.resolveFor(any(Member.class), eq(LocalDate.of(2026, 7, 20))))
                .thenReturn(Optional.of(policy));

        service.commitClaim(20L);

        verify(consumptionRepository).save(any(BenefitBucketConsumption.class));
    }

    @Test
    @DisplayName("يُقفل وعاء المنفعة قبل فحص الرصيد لمنع اعتمادين متزامنين")
    void bucketIsLockedBeforeBalanceValidation() {
        service.commitClaim(20L);

        var order = inOrder(bucketRepository, consumptionRepository);
        order.verify(bucketRepository).findByIdForUpdate(70L);
        order.verify(consumptionRepository).sumCommittedAmount(
                eq(10L), eq(70L), any(), any(), isNull());
        order.verify(consumptionRepository).save(any(BenefitBucketConsumption.class));
    }

    @Test
    @DisplayName("مفتاح عدم التكرار يمنع تسجيل استهلاك المطالبة مرتين")
    void idempotencyPreventsDuplicateConsumption() {
        when(consumptionRepository.existsByIdempotencyKey(anyString())).thenReturn(true);

        service.commitClaim(20L);

        verify(consumptionRepository, never()).save(any());
        verify(consumptionRepository, never()).sumCommittedAmount(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("تغير الرصيد أثناء الاعتماد يوقف المطالبة ولا يتجاوز السقف")
    void stalePreviewCannotOverdrawAtCommit() {
        when(consumptionRepository.sumCommittedAmount(any(), any(), any(), any(), any()))
                .thenReturn(new BigDecimal("1400.00"));

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.commitClaim(20L));

        assertTrue(error.getMessage().contains("تغير الرصيد"));
        verify(consumptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("مطالبة معتمدة سابقة بلا قيد سقف توقف الاعتماد اللاحق")
    void unledgeredPreviousApprovedClaimFailsClosed() {
        when(consumptionRepository.existsUnledgeredApprovedBucketClaim(eq(10L), eq(20L), any())).thenReturn(true);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.commitClaim(20L));

        assertTrue(error.getMessage().contains("لم تُرحّل إلى دفتر سقوف المنافع"));
        verify(bucketRepository, never()).findByIdForUpdate(anyLong());
        verify(consumptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("مطالبة معتمدة سابقة بلا قيد سقف عام توقف الاعتماد اللاحق")
    void unledgeredPreviousApprovedGeneralClaimFailsClosed() {
        when(consumptionRepository.existsUnledgeredApprovedGeneralClaim(eq(10L), eq(20L), any())).thenReturn(true);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.commitClaim(20L));

        assertTrue(error.getMessage().contains("لم تُرحّل استهلاكها إلى السقف العام"));
        verify(bucketRepository, never()).findByIdForUpdate(anyLong());
        verify(consumptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("السقف العام يُقرأ من الدفتر لا من claim_lines، ويوقف الاعتماد عند تجاوزه")
    void generalCeilingReadFromLedgerBlocksOverdraw() {
        policy.setAnnualLimit(new BigDecimal("1000.00"));
        when(benefitPolicyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(policy));
        when(limitBalanceReader.readGeneralCeiling(eq(10L), eq(1L), eq(new BigDecimal("1000.00")), any(), any(), eq(20L)))
                .thenReturn(new LimitBalanceReader.GeneralCeilingBalance(
                        new BigDecimal("1000.00"), new BigDecimal("1000.00"), BigDecimal.ZERO, new BigDecimal("900.00"), BigDecimal.ZERO,
                        new BigDecimal("100.00"), new BigDecimal("100.00")));

        // this claim's own line consumes 200, which added to the 900 already
        // committed (per the ledger) exceeds the 1000 ceiling.
        RuntimeException error = assertThrows(RuntimeException.class, () -> service.commitClaim(20L));

        assertTrue(error.getMessage().contains("تجاوز السقف العام"));
        verify(consumptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("إلغاء المطالبة يعكس الاستهلاك ويحفظ أثراً تدقيقياً")
    void reversalLeavesTheOriginalIntactAndPostsACompensatingMovement() {
        BenefitBucketConsumption original = BenefitBucketConsumption.builder()
                .id(90L)
                .claim(claim)
                .claimLine(line)
                .policy(policy)
                .memberId(10L)
                .bucket(bucket)
                .periodStart(LocalDate.of(2026, 1, 1))
                .periodEnd(LocalDate.of(2026, 12, 31))
                .approvedAmount(new BigDecimal("200.00"))
                .timesConsumed(1)
                .status(BenefitBucketConsumption.Status.COMMITTED)
                .calculationVersion(1)
                .idempotencyKey("ORIGINAL")
                .build();
        when(consumptionRepository.findByClaimIdAndStatus(
                20L, BenefitBucketConsumption.Status.COMMITTED))
                .thenReturn(List.of(original));

        service.reverseClaim(20L);

        // The original is NEVER touched: the ledger is append-only, and the
        // balance effect comes from the compensating row via
        // net = original - SUM(reversals). Flipping it (the old behaviour)
        // erased the fact that the amount had been committed at all, and made
        // partial reversal impossible to express.
        assertEquals(BenefitBucketConsumption.Status.COMMITTED, original.getStatus());
        assertNull(original.getReversedAt());

        ArgumentCaptor<BenefitBucketConsumption> captor =
                ArgumentCaptor.forClass(BenefitBucketConsumption.class);
        verify(consumptionRepository, times(1)).save(captor.capture());
        BenefitBucketConsumption reversal = captor.getValue();
        assertEquals(original, reversal.getReversalOf());
        assertEquals("ORIGINAL:REVERSAL", reversal.getIdempotencyKey());
        assertEquals(BenefitBucketConsumption.Status.REVERSED, reversal.getStatus());
        assertEquals(BenefitBucketConsumption.ReversalReason.CLAIM_REVERSAL, reversal.getReversalReason());
        assertEquals(original.getApprovedAmount(), reversal.getApprovedAmount());
    }

    @Test
    @DisplayName("الفترة الشهرية تحفظ أول وآخر يوم من شهر الخدمة")
    void monthlyPeriodUsesCalendarMonth() {
        bucket.setPeriodType(LimitPeriodType.MONTHLY);

        service.commitClaim(20L);

        ArgumentCaptor<BenefitBucketConsumption> captor =
                ArgumentCaptor.forClass(BenefitBucketConsumption.class);
        verify(consumptionRepository).save(captor.capture());
        assertEquals(LocalDate.of(2026, 7, 1), captor.getValue().getPeriodStart());
        assertEquals(LocalDate.of(2026, 7, 31), captor.getValue().getPeriodEnd());
    }

    @Test
    @DisplayName("الفترة متعددة السنوات ترتكز على بداية الوثيقة لا السنة الميلادية")
    void multiYearPeriodAnchorsToPolicyStart() {
        policy.setStartDate(LocalDate.of(2025, 4, 1));
        policy.setEndDate(LocalDate.of(2029, 3, 31));
        bucket.setPeriodType(LimitPeriodType.MULTI_YEAR_POLICY);
        bucket.setPeriodValue(2);

        service.commitClaim(20L);

        ArgumentCaptor<BenefitBucketConsumption> captor =
                ArgumentCaptor.forClass(BenefitBucketConsumption.class);
        verify(consumptionRepository).save(captor.capture());
        assertEquals(LocalDate.of(2025, 4, 1), captor.getValue().getPeriodStart());
        assertEquals(LocalDate.of(2027, 3, 31), captor.getValue().getPeriodEnd());
    }
    @Test
    @DisplayName("الفترة الأسبوعية ترتكز على بداية الوثيقة لا على السبت التقويمي")
    void weeklyPeriodAnchorsToPolicyStartNotCalendarSaturday() {
        // policy starts 2026-01-01 (a Thursday); WEEKLY is a rolling 7-day
        // window anchored to that date, not the calendar week (was
        // previously hardcoded to Saturday-Friday regardless of policy start).
        claim.setServiceDate(LocalDate.of(2026, 7, 27));
        bucket.setPeriodType(LimitPeriodType.WEEKLY);

        service.commitClaim(20L);

        ArgumentCaptor<BenefitBucketConsumption> captor =
                ArgumentCaptor.forClass(BenefitBucketConsumption.class);
        verify(consumptionRepository).save(captor.capture());
        assertEquals(LocalDate.of(2026, 7, 23), captor.getValue().getPeriodStart());
        assertEquals(LocalDate.of(2026, 7, 29), captor.getValue().getPeriodEnd());
    }

    @Test
    @DisplayName("الفترة الربعية ترتكز على بداية الوثيقة لا على السنة الميلادية")
    void quarterlyPeriodAnchorsToPolicyStartNotCalendarYear() {
        // A policy starting mid-year must reset quarterly buckets every 3
        // months from ITS start date, not from January — otherwise a
        // policy starting 2026-03-18 would get a Q1 boundary on Jan 1
        // that has nothing to do with when coverage actually began.
        policy.setStartDate(LocalDate.of(2026, 3, 18));
        policy.setEndDate(LocalDate.of(2027, 3, 17));
        claim.setServiceDate(LocalDate.of(2026, 7, 27));
        bucket.setPeriodType(LimitPeriodType.QUARTERLY);

        service.commitClaim(20L);

        ArgumentCaptor<BenefitBucketConsumption> captor =
                ArgumentCaptor.forClass(BenefitBucketConsumption.class);
        verify(consumptionRepository).save(captor.capture());
        // Quarters from 2026-03-18: Q1 [03-18,06-17], Q2 [06-18,09-17] <- 07-27 falls here
        assertEquals(LocalDate.of(2026, 6, 18), captor.getValue().getPeriodStart());
        assertEquals(LocalDate.of(2026, 9, 17), captor.getValue().getPeriodEnd());
    }

    @Test
    @DisplayName("الفترة المخصصة بالأشهر ترتكز على بداية الوثيقة")
    void customMonthsPeriodAnchorsToPolicyStart() {
        policy.setStartDate(LocalDate.of(2026, 3, 18));
        policy.setEndDate(LocalDate.of(2027, 3, 17));
        claim.setServiceDate(LocalDate.of(2026, 7, 27));
        bucket.setPeriodType(LimitPeriodType.CUSTOM_MONTHS);
        bucket.setPeriodValue(6);

        service.commitClaim(20L);

        ArgumentCaptor<BenefitBucketConsumption> captor =
                ArgumentCaptor.forClass(BenefitBucketConsumption.class);
        verify(consumptionRepository).save(captor.capture());
        assertEquals(LocalDate.of(2026, 3, 18), captor.getValue().getPeriodStart());
        assertEquals(LocalDate.of(2026, 9, 17), captor.getValue().getPeriodEnd());
    }

    @Test
    @DisplayName("الفترة المخصصة بالسنوات تدعم سقف كل خمس سنوات")
    void customYearsPeriodSupportsFiveYearLimit() {
        policy.setStartDate(LocalDate.of(2026, 3, 18));
        policy.setEndDate(LocalDate.of(2036, 3, 17));
        claim.setServiceDate(LocalDate.of(2030, 4, 1));
        bucket.setPeriodType(LimitPeriodType.CUSTOM_YEARS);
        bucket.setPeriodValue(5);

        service.commitClaim(20L);

        ArgumentCaptor<BenefitBucketConsumption> captor =
                ArgumentCaptor.forClass(BenefitBucketConsumption.class);
        verify(consumptionRepository).save(captor.capture());
        assertEquals(LocalDate.of(2026, 3, 18), captor.getValue().getPeriodStart());
        assertEquals(LocalDate.of(2031, 3, 17), captor.getValue().getPeriodEnd());
    }

    // ── P1.6: a TIMES-limited bucket commits what the decision APPROVED,
    // never what was merely requested ──────────────────────────────────

    private BenefitLimitBucket timesLimitedBucket() {
        BenefitLimitBucket timesBucket = BenefitLimitBucket.builder()
                .id(71L).policy(policy).code("PHYSIO").nameAr("جلسات العلاج الطبيعي")
                .timesLimit(2).periodType(LimitPeriodType.ANNUAL).countingMethod(CountingMethod.EACH_UNIT)
                .consumptionBasis(ConsumptionBasis.COMPANY_SHARE).active(true).build();
        BenefitRuleBucket link = BenefitRuleBucket.builder().bucket(timesBucket).build();
        lenient().when(ruleBucketRepository.findByRuleIdOrderByConsumptionOrder(50L)).thenReturn(List.of(link));
        lenient().when(bucketRepository.findByIdForUpdate(71L)).thenReturn(Optional.of(timesBucket));
        lenient().when(consumptionRepository.sumCommittedTimes(any(), any(), any(), any(), any())).thenReturn(0);
        return timesBucket;
    }

    @Test
    @DisplayName("P1.6 — الدفتر يستهلك الكمية المعتمدة (2) لا المطلوبة (3) لوعاء محدود بعدد المرات")
    void commitClaimConsumesApprovedQuantityNotRequestedQuantity() {
        timesLimitedBucket();
        line.setQuantity(3);
        line.setApprovedQuantity(2);

        service.commitClaim(20L);

        ArgumentCaptor<BenefitBucketConsumption> captor = ArgumentCaptor.forClass(BenefitBucketConsumption.class);
        verify(consumptionRepository).save(captor.capture());
        assertEquals(2, captor.getValue().getTimesConsumed());
    }

    @Test
    @DisplayName("P1.6 — بلا approvedQuantity، الدفتر يفشل بوضوح ولا يستهلك الكمية المطلوبة كبديل صامت")
    void commitClaimFailsClosedWhenApprovedQuantityMissingForATimesLimitedBucket() {
        timesLimitedBucket();
        line.setQuantity(3);
        line.setApprovedQuantity(null);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.commitClaim(20L));

        assertTrue(error.getMessage().contains("TIMES_LEDGER_APPROVED_QUANTITY_MISSING"));
        verify(consumptionRepository, never()).save(any());
    }

    // ── P1.11.2: the canonical path -- a line carrying a live
    // UnifiedLimitDecision must be executed exactly as its
    // CanonicalConsumptionTarget says, with zero re-derivation ──────────

    private static final LocalDate PERIOD_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 12, 31);

    private ResolvedLimitDescriptor descriptorFor(Long bucketId) {
        return new ResolvedLimitDescriptor(ResolvedLimitDescriptor.bucketKey(bucketId), bucketId,
                ClaimLineLimitSnapshot.SourceType.POLICY_DEFAULT, BenefitScopeType.CATEGORY,
                BeneficiaryScopeType.MEMBER, 50L, 9L, "ANNUAL", PERIOD_START, PERIOD_END);
    }

    /** Wires the line with a REAL, resolver-produced decision for one AMOUNT bucket. */
    private void giveLineACanonicalAmountDecision(Long bucketId, BigDecimal configured, BigDecimal consumed) {
        BucketLimitSnapshot snapshot = new BucketLimitSnapshot(bucketId, 1L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, configured, BigDecimal.ZERO, BigDecimal.ZERO, configured,
                PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(1L, 50L, 10L, claim.getServiceDate(),
                EncounterType.OUTPATIENT, 0, 0, BigDecimal.ZERO, consumed, null,
                ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(snapshot));
        line.setUnifiedLimitDecision(decision);
        line.setResolvedLimitItems(List.of(new ResolvedLimitItem(snapshot, descriptorFor(bucketId))));
        line.setLimitConsumption(consumed);
    }

    @Test
    @DisplayName("CW1 — canonical only: what lands on the ledger row is exactly the target's own amount, ignoring companyShare/eligibleAmount entirely")
    void cw1_canonicalTargetIsExecutedLiterally() {
        when(consumptionRepository.sumCommittedAmount(any(), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        giveLineACanonicalAmountDecision(70L, new BigDecimal("1500.00"), new BigDecimal("777.00"));
        // Legacy-only fields the canonical path must NEVER consult:
        line.setCompanyShare(new BigDecimal("1.00"));
        line.setTotalPrice(new BigDecimal("1.00"));
        line.setLimitRefused(new BigDecimal("1.00"));

        service.commitClaim(20L);

        ArgumentCaptor<BenefitBucketConsumption> captor = ArgumentCaptor.forClass(BenefitBucketConsumption.class);
        verify(consumptionRepository).save(captor.capture());
        assertEquals(0, new BigDecimal("777.00").compareTo(captor.getValue().getApprovedAmount()));
        // The rule-bucket walk is never consulted on the canonical path.
        verify(ruleBucketRepository, never()).findByRuleIdOrderByConsumptionOrder(anyLong());
    }

    @Test
    @DisplayName("CW2 — no evaluated-but-unconsumed write: an empty canonical target list writes nothing at all")
    void cw2_emptyTargetsWriteNothing() {
        // A BLOCKED decision has consumptionTargets() == List.of() by construction.
        UnifiedLimitDecision blocked = UnifiedLimitDecision.blocked(50L, List.of("TEST_BLOCK"));
        line.setUnifiedLimitDecision(blocked);
        line.setResolvedLimitItems(List.of(new ResolvedLimitItem(
                new BucketLimitSnapshot(70L, 1L, LimitAxisType.AMOUNT, CountingMethod.EACH_LINE,
                        new BigDecimal("1500.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1500.00"),
                        PERIOD_START, PERIOD_END),
                descriptorFor(70L))));

        service.commitClaim(20L);

        verify(consumptionRepository, never()).save(any());
        verify(bucketRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("CW3 — POLICY_GENERAL: no bucket fetch, no synthetic bucket entity")
    void cw3_policyGeneralNeedsNoBucketLookup() {
        BucketLimitSnapshot general = new BucketLimitSnapshot(null, 1L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, new BigDecimal("1000000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1000000.00"), PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(1L, 50L, 10L, claim.getServiceDate(),
                EncounterType.OUTPATIENT, 0, 0, BigDecimal.ZERO, new BigDecimal("300.00"), null,
                ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(general));
        line.setUnifiedLimitDecision(decision);
        line.setResolvedLimitItems(List.of(new ResolvedLimitItem(general,
                new ResolvedLimitDescriptor(ResolvedLimitDescriptor.policyGeneralKey(1L), null,
                        ClaimLineLimitSnapshot.SourceType.POLICY_DEFAULT, BenefitScopeType.POLICY_GENERAL,
                        BeneficiaryScopeType.MEMBER, 50L, null, "ANNUAL", PERIOD_START, PERIOD_END))));
        line.setLimitConsumption(new BigDecimal("300.00"));

        service.commitClaim(20L);

        verify(bucketRepository, never()).findByIdForUpdate(anyLong());
        ArgumentCaptor<BenefitBucketConsumption> captor = ArgumentCaptor.forClass(BenefitBucketConsumption.class);
        verify(consumptionRepository).save(captor.capture());
        assertNull(captor.getValue().getBucket());
        assertEquals(BenefitBucketConsumption.LimitScope.POLICY_GENERAL, captor.getValue().getLimitScope());
    }

    @Test
    @DisplayName("CW4 — child + parent both consumed as separate rows, each carrying the SAME money the decision approved")
    void cw4_childAndParentBothWrittenAsGiven() {
        BenefitLimitBucket parent = BenefitLimitBucket.builder()
                .id(71L).policy(policy).code("PARENT").nameAr("أصل")
                .amountLimit(new BigDecimal("50000.00")).periodType(LimitPeriodType.ANNUAL)
                .countingMethod(CountingMethod.EACH_LINE).consumptionBasis(ConsumptionBasis.ELIGIBLE_AMOUNT)
                .active(true).build();
        lenient().when(bucketRepository.findByIdForUpdate(71L)).thenReturn(Optional.of(parent));

        BucketLimitSnapshot child = new BucketLimitSnapshot(70L, 1L, LimitAxisType.AMOUNT, CountingMethod.EACH_LINE,
                new BigDecimal("1500.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1500.00"),
                PERIOD_START, PERIOD_END);
        BucketLimitSnapshot parentSnapshot = new BucketLimitSnapshot(71L, 1L, LimitAxisType.AMOUNT,
                CountingMethod.EACH_LINE, new BigDecimal("50000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("50000.00"), PERIOD_START, PERIOD_END);
        UnifiedLimitInput input = new UnifiedLimitInput(1L, 50L, 10L, claim.getServiceDate(),
                EncounterType.OUTPATIENT, 0, 0, BigDecimal.ZERO, new BigDecimal("200.00"), null,
                ReservationEvaluationMode.NORMAL, null, null);
        UnifiedLimitDecision decision = UnifiedLimitResolver.resolve(input, List.of(child, parentSnapshot));
        line.setUnifiedLimitDecision(decision);
        line.setResolvedLimitItems(List.of(
                new ResolvedLimitItem(child, descriptorFor(70L)),
                new ResolvedLimitItem(parentSnapshot, descriptorFor(71L))));
        line.setLimitConsumption(new BigDecimal("200.00"));

        service.commitClaim(20L);

        ArgumentCaptor<BenefitBucketConsumption> captor = ArgumentCaptor.forClass(BenefitBucketConsumption.class);
        verify(consumptionRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(row ->
                assertEquals(0, new BigDecimal("200.00").compareTo(row.getApprovedAmount())));
        assertThat(captor.getAllValues()).extracting(row -> row.getBucket().getId())
                .containsExactlyInAnyOrder(70L, 71L);
    }

    @Test
    @DisplayName("P1.11.3 architectural guard — a normal commit (live decision present) never reaches legacyReconcileTargets' rule-bucket walk")
    void normalCommitNeverConsultsTheLegacyRuleBucketWalk() {
        // Exactly what every direct-entry or reviewed approval looks like
        // today: a live decision is present, so the canonical branch alone
        // must run. legacyReconcileTargets exists ONLY for
        // reconcileApprovedClaim's historical-claim repair, where the
        // decision is naturally absent -- never for a new approval.
        giveLineACanonicalAmountDecision(70L, new BigDecimal("1500.00"), new BigDecimal("100.00"));

        service.commitClaim(20L);

        verify(ruleBucketRepository, never()).findByRuleIdOrderByConsumptionOrder(anyLong());
    }
}

