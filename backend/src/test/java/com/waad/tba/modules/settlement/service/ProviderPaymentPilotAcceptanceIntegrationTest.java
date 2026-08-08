package com.waad.tba.modules.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.settlement.dto.CreateProviderPaymentRequest;
import com.waad.tba.modules.settlement.dto.CreateProviderPaymentRequest.AllocationInput;
import com.waad.tba.modules.settlement.dto.PaymentAllocationSuggestionDto;
import com.waad.tba.modules.settlement.dto.ProviderReconciliationDto.Finding;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.entity.ProviderPayment;
import com.waad.tba.modules.settlement.repository.AccountTransactionRepository;
import com.waad.tba.modules.settlement.repository.ProviderAccountRepository;
import com.waad.tba.modules.settlement.repository.ProviderPaymentRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Phase 10 — dedicated pilot acceptance matrix for the new provider-payment
 * model, run against real PostgreSQL with real claim/employer/member fixtures
 * (not the DRAFT-with-preset-allocation shortcuts used by earlier phase
 * tests). Each scenario below maps to one line of the acceptance matrix
 * agreed before this phase started: full/partial/over payments, multi-
 * employer/period allocation, post/reverse/idempotent replay, stale-version
 * rejection, historical-drift closure, and reconciliation staying consistent
 * after every mutating step.
 *
 * Historical-drift closure and the reversal-reopens-allocations mechanics
 * already have dedicated, exhaustive coverage in
 * {@link ProviderAccountReconciliationIntegrationTest} and
 * {@code ProviderPaymentPostingServiceIntegrationTest} (Phases 6-7) — this
 * class does not repeat that depth, only confirms each behavior holds as
 * part of one continuous pilot-style lifecycle, which is what those earlier,
 * narrower tests could not demonstrate.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class ProviderPaymentPilotAcceptanceIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired ProviderPaymentAllocationSuggestionService suggestionService;
    @Autowired ProviderPaymentDraftService draftService;
    @Autowired ProviderPaymentPostingService postingService;
    @Autowired ProviderPaymentReversalService reversalService;
    @Autowired ProviderAccountReconciliationService reconciliation;
    @Autowired ProviderAccountAdjustmentService adjustmentService;
    @Autowired ProviderPaymentRepository payments;
    @Autowired ProviderAccountRepository accounts;
    @Autowired AccountTransactionRepository transactions;
    @Autowired ProviderRepository providers;
    @Autowired EmployerRepository employers;
    @Autowired MemberRepository members;
    @Autowired VisitRepository visits;
    @Autowired ClaimRepository claims;
    @Autowired BenefitPolicyRepository benefitPolicies;

    private String suffix;
    private Long providerId;
    private Long employerA;
    private Long employerB;
    private ProviderAccount account;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        providerId = providers.save(Provider.builder().name("Pilot Hospital " + suffix)
                .providerType(ProviderType.HOSPITAL).licenseNumber("PILOT-" + suffix)
                .allowAllEmployers(true).active(true).build()).getId();
        employerA = newEmployer("A");
        employerB = newEmployer("B");

        // Outstanding: A/Jan=100, A/Feb=200, B/Jan=300 -> total 600.
        addClaim(employerA, LocalDate.of(2026, 1, 10), "100.00");
        addClaim(employerA, LocalDate.of(2026, 2, 5), "200.00");
        addClaim(employerB, LocalDate.of(2026, 1, 20), "300.00");

        account = accounts.save(ProviderAccount.builder().providerId(providerId)
                .runningBalance(new BigDecimal("600.00"))
                .totalApproved(new BigDecimal("600.00")).totalPaid(BigDecimal.ZERO).build());
    }

    // ── 1. دفعة كاملة ────────────────────────────────────────────────────────

    @Test
    void fullPaymentFullyAllocatesAndReconciliationIsMatchedAfterPosting() {
        ProviderPayment posted = suggestAndPost("600.00", LocalDate.of(2026, 7, 1));

        assertThat(posted.getUnallocatedAmount()).isEqualByComparingTo("0.00");
        var report = reconciliation.reconcile(providerId);
        assertThat(report.getFindings()).containsExactly(Finding.MATCHED);
        assertThat(report.getAccountTotalPaid()).isEqualByComparingTo("600.00");
    }

    // ── 2. دفعة جزئية عبر عدة جهات وفترات ────────────────────────────────────

    @Test
    void partialPaymentSplitsOldestPeriodsFirstAndLeavesTheRestOutstanding() {
        // 350 covers A/Jan(100) + B/Jan(300)? No: FIFO orders by date, both Jan
        // periods are oldest — suggestion covers them before A/Feb. 350 covers
        // A/Jan(100) fully and partially covers B/Jan(300) worth 250 (same-period
        // proportional split is not needed since it's a single bucket).
        ProviderPayment posted = suggestAndPost("350.00", LocalDate.of(2026, 7, 1));

        assertThat(posted.getAllocatedAmount()).isEqualByComparingTo("350.00");
        assertThat(posted.getUnallocatedAmount()).isEqualByComparingTo("0.00");
        assertThat(posted.getAllocations()).hasSizeGreaterThanOrEqualTo(2);

        var report = reconciliation.reconcile(providerId);
        assertThat(report.getFindings()).containsExactly(Finding.MATCHED);
        assertThat(report.getAccountTotalPaid()).isEqualByComparingTo("350.00");
        // A/Feb (200) and the remainder of B/Jan are still legitimately outstanding
        // — nothing here claims them as paid.
        assertThat(report.getAccountRunningBalance()).isEqualByComparingTo("250.00");
    }

    // ── 3. دفعة زائدة ────────────────────────────────────────────────────────

    @Test
    void overPaymentLeavesAnExplicitUnallocatedRemainderAndSurfacesACreditBalance() {
        ProviderPayment posted = suggestAndPost("900.00", LocalDate.of(2026, 7, 1));

        // Total real outstanding is 600; FIFO cannot invent 300 more to allocate.
        assertThat(posted.getAllocatedAmount()).isEqualByComparingTo("600.00");
        assertThat(posted.getUnallocatedAmount()).isEqualByComparingTo("300.00");

        var report = reconciliation.reconcile(providerId);
        assertThat(report.getAccountRunningBalance()).isEqualByComparingTo("-300.00");
        assertThat(report.getCreditBalance()).isEqualByComparingTo("300.00");
        assertThat(report.getFindings()).contains(Finding.PROVIDER_CREDIT_BALANCE, Finding.UNDER_ALLOCATED);
    }

    // ── 4. ترحيل، عكس، إعادة كلتيهما، ومطابقة بعد كل خطوة ────────────────────

    @Test
    void postReverseAndBothReplaysAreIdempotentWithReconciliationConsistentThroughout() {
        ProviderPayment draft = suggestAndCreateDraft("300.00", LocalDate.of(2026, 7, 1));
        assertThat(reconciliation.reconcile(providerId).getFindings())
                .containsExactly(Finding.UNPOSTED_PAYMENT);

        ProviderAccount before = accounts.findById(account.getId()).orElseThrow();
        var postResult = postingService.post(draft.getId(), draft.getIdempotencyKey(),
                draft.getVersion(), before.getVersion(), "accountant", 1L);
        assertThat(postResult.isIdempotentReplay()).isFalse();
        assertThat(reconciliation.reconcile(providerId).getFindings()).containsExactly(Finding.MATCHED);

        // Idempotent replay of post: same idempotency key, same result, no new ledger entry.
        long ledgerCountAfterPost = transactions.count();
        var replayPost = postingService.post(draft.getId(), draft.getIdempotencyKey(),
                postResult.getPaymentVersion(), postResult.getAccountVersion(), "accountant", 1L);
        assertThat(replayPost.isIdempotentReplay()).isTrue();
        assertThat(transactions.count()).isEqualTo(ledgerCountAfterPost);

        ProviderAccount afterPost = accounts.findById(account.getId()).orElseThrow();
        var reverseResult = reversalService.reverse(draft.getId(), "اختبار قبول تجريبي",
                replayPost.getPaymentVersion(), afterPost.getVersion(), "supervisor", 2L);
        assertThat(reverseResult.isIdempotentReplay()).isFalse();
        assertThat(reconciliation.reconcile(providerId).getFindings()).containsExactly(Finding.MATCHED);

        // Idempotent replay of reverse: no second compensating credit.
        long ledgerCountAfterReverse = transactions.count();
        ProviderAccount afterReverse = accounts.findById(account.getId()).orElseThrow();
        var replayReverse = reversalService.reverse(draft.getId(), "سبب مختلف يُتجاهل",
                reverseResult.getPaymentVersion(), afterReverse.getVersion(), "supervisor", 2L);
        assertThat(replayReverse.isIdempotentReplay()).isTrue();
        assertThat(transactions.count()).isEqualTo(ledgerCountAfterReverse);

        // Final state: fully reverted, nothing outstanding was ever claimed as paid.
        var finalReport = reconciliation.reconcile(providerId);
        assertThat(finalReport.getFindings()).containsExactly(Finding.MATCHED);
        assertThat(finalReport.getAccountTotalPaid()).isEqualByComparingTo("0.00");
    }

    // ── 5. تزامن وتغيّر النسخ ────────────────────────────────────────────────

    @Test
    void staleAccountOrPaymentVersionIsRejectedOnPostAndOnReverse() {
        ProviderPayment draft = suggestAndCreateDraft("100.00", LocalDate.of(2026, 7, 1));
        ProviderAccount current = accounts.findById(account.getId()).orElseThrow();

        assertThatThrownBy(() -> postingService.post(draft.getId(), draft.getIdempotencyKey(),
                draft.getVersion(), current.getVersion() + 99, "accountant", 1L))
                .hasMessageContaining("تغيّر");

        assertThatThrownBy(() -> postingService.post(draft.getId(), draft.getIdempotencyKey(),
                draft.getVersion() + 99, current.getVersion(), "accountant", 1L))
                .hasMessageContaining("تغيّر");

        var posted = postingService.post(draft.getId(), draft.getIdempotencyKey(),
                draft.getVersion(), current.getVersion(), "accountant", 1L);
        ProviderAccount afterPost = accounts.findById(account.getId()).orElseThrow();

        assertThatThrownBy(() -> reversalService.reverse(draft.getId(), "سبب",
                posted.getPaymentVersion() + 99, afterPost.getVersion(), "supervisor", 2L))
                .isInstanceOf(RuntimeException.class);
    }

    // ── 6. تسوية انحراف تاريخي ثم استمرار المطابقة ───────────────────────────

    @Test
    void historicalDriftAdjustmentClosesTheGapAndFuturePaymentsStayConsistent() {
        ProviderPayment posted = suggestAndPost("100.00", LocalDate.of(2026, 7, 1));
        // Simulate a pre-existing, unexplained drift on top of the legitimate payment.
        accounts.findById(account.getId()).ifPresent(a -> {
            a.setTotalPaid(a.getTotalPaid().add(new BigDecimal("50.00")));
            a.setRunningBalance(a.getRunningBalance().subtract(new BigDecimal("50.00")));
            accounts.saveAndFlush(a);
        });

        var drifted = reconciliation.reconcile(providerId);
        assertThat(drifted.getFindings()).contains(Finding.BALANCE_DRIFT);

        ProviderAccount current = accounts.findById(account.getId()).orElseThrow();
        adjustmentService.alignPaidTotalWithLedger(providerId, "إغلاق انحراف تجريبي",
                current.getVersion(), "supervisor", 2L);

        var afterAdjustment = reconciliation.reconcile(providerId);
        assertThat(afterAdjustment.getFindings()).containsExactly(Finding.MATCHED);
        assertThat(afterAdjustment.getAccountTotalPaid()).isEqualByComparingTo(posted.getAmount());
    }

    // ── 9. تطابق نهائي شامل ──────────────────────────────────────────────────

    @Test
    void finalStateMatchesAcrossLedgerAccountDocumentsAndAllocationsAfterAMixedLifecycle() {
        ProviderPayment kept = suggestAndPost("100.00", LocalDate.of(2026, 3, 1));
        ProviderPayment reversedLater = suggestAndPost("200.00", LocalDate.of(2026, 4, 1));

        ProviderAccount current = accounts.findById(account.getId()).orElseThrow();
        reversalService.reverse(reversedLater.getId(), "إلغاء دفعة ثانية",
                reversedLater.getVersion(), current.getVersion(), "supervisor", 2L);

        var report = reconciliation.reconcile(providerId);
        assertThat(report.getFindings()).containsExactly(Finding.MATCHED);
        // Only the kept, non-reversed payment should count toward paid/ledger.
        assertThat(report.getAccountTotalPaid()).isEqualByComparingTo(kept.getAmount());
        assertThat(report.getLedgerNet()).isEqualByComparingTo(report.getAccountTotalPaid());
        assertThat(report.getDocumentsTotal()).isEqualByComparingTo(report.getAccountTotalPaid());
        assertThat(report.getAllocatedTotal()).isEqualByComparingTo(report.getAccountTotalPaid());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ProviderPayment suggestAndCreateDraft(String amount, LocalDate asOfDate) {
        PaymentAllocationSuggestionDto suggestion =
                suggestionService.suggest(providerId, new BigDecimal(amount), asOfDate);

        CreateProviderPaymentRequest request = new CreateProviderPaymentRequest();
        request.setProviderId(providerId);
        request.setAmount(new BigDecimal(amount));
        request.setPaymentDate(asOfDate);
        request.setPaymentMethod(com.waad.tba.modules.settlement.entity.PaymentMethod.BANK_TRANSFER);
        request.setAllocations(suggestion.getAllocations().stream().map(a -> {
            AllocationInput input = new AllocationInput();
            input.setEmployerId(a.getEmployerId());
            input.setTargetYear(a.getTargetYear());
            input.setTargetMonth(a.getTargetMonth());
            input.setAmount(a.getSuggestedAmount());
            input.setOutstandingAtAllocation(a.getOutstandingAtAllocation());
            input.setAllocationMethod(a.getAllocationMethod());
            return input;
        }).toList());

        return draftService.createDraft(request, "accountant");
    }

    private ProviderPayment suggestAndPost(String amount, LocalDate asOfDate) {
        ProviderPayment draft = suggestAndCreateDraft(amount, asOfDate);
        ProviderAccount current = accounts.findById(account.getId()).orElseThrow();
        postingService.post(draft.getId(), draft.getIdempotencyKey(),
                draft.getVersion(), current.getVersion(), "accountant", 1L);
        return payments.findByIdWithAllocations(draft.getId()).orElseThrow();
    }

    private Long newEmployer(String marker) {
        Employer employer = employers.save(Employer.builder().name("Pilot Employer " + marker + " " + suffix)
                .code("PILOT-" + marker + "-" + suffix).active(true).build());
        BenefitPolicy policy = benefitPolicies.save(BenefitPolicy.builder()
                .name("Pilot Policy " + marker + " " + suffix)
                .policyCode("PILOT-POL-" + marker + "-" + suffix)
                .employer(employer).annualLimit(new BigDecimal("100000.00"))
                .defaultCoveragePercent(100).startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31)).status(BenefitPolicyStatus.ACTIVE)
                .active(true).build());
        employerPolicies.put(employer.getId(), policy);
        return employer.getId();
    }

    private final java.util.Map<Long, BenefitPolicy> employerPolicies = new java.util.HashMap<>();

    private void addClaim(Long employerId, LocalDate date, String amount) {
        Employer employer = employers.findById(employerId).orElseThrow();
        Member member = members.save(Member.builder().fullName("Pilot Member " + UUID.randomUUID())
                .barcode("BC-" + UUID.randomUUID()).employer(employer)
                .benefitPolicy(employerPolicies.get(employerId)).active(true).build());
        Visit visit = visits.save(Visit.builder().member(member).employer(employer).providerId(providerId)
                .visitDate(date).status(VisitStatus.REGISTERED).build());
        BigDecimal value = new BigDecimal(amount);
        Claim claim = Claim.builder().claimNumber("PILOT-CLM-" + UUID.randomUUID())
                .member(member).visit(visit).providerId(providerId).serviceDate(date)
                .status(ClaimStatus.APPROVED)
                .requestedAmount(value).approvedAmount(value).netProviderAmount(value)
                .patientCoPay(BigDecimal.ZERO).refusedAmount(BigDecimal.ZERO)
                .companyDiscountAmount(BigDecimal.ZERO).active(true).build();
        ClaimLine line = ClaimLine.builder().claim(claim).serviceCode("PILOT-SVC")
                .serviceName("Pilot Service").quantity(1).unitPrice(value).totalPrice(value)
                .requestedTotal(value).approvedAmount(value).companyShare(value)
                .patientShare(BigDecimal.ZERO).build();
        claim.setLines(List.of(line));
        claims.saveAndFlush(claim);
    }
}
