package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.benefitpolicy.repository.ClaimLineLimitSnapshotRepository;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.claim.repository.FinancialOutboxEventRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.providercontract.service.ProviderContractTermsService;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.repository.ProviderAccountRepository;
import com.waad.tba.modules.settlement.repository.AccountTransactionRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * End-to-end proof, against a real PostgreSQL instance, that the direct-entry claim
 * path (ClaimService.createClaim with no explicit status → DRAFT → finalizeSnapshot →
 * ClaimStateMachine) no longer double-counts the claim being validated against itself,
 * and never persists an APPROVED claim with a zero approved amount.
 *
 * Coverage is set to 100% and pricing items priced exactly at the amounts under test,
 * so requestedAmount == companyShare == approvedAmount with no patient co-pay noise.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@Transactional
class ClaimLimitDoubleCountingIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private ClaimService claimService;

    @Autowired
    private EmployerRepository employerRepository;

    @Autowired
    private BenefitPolicyRepository benefitPolicyRepository;

    @Autowired
    private BenefitPolicyRuleRepository benefitPolicyRuleRepository;

    @Autowired
    private com.waad.tba.modules.rbac.repository.UserRepository userRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ProviderContractRepository contractRepository;

    @Autowired
    private ProviderContractTermsService termsService;

    @Autowired
    private ProviderContractPricingItemRepository pricingRepository;

    @Autowired
    private MedicalServiceRepository medicalServiceRepository;

    @Autowired
    private MedicalCategoryRepository medicalCategoryRepository;

    @Autowired
    private VisitRepository visitRepository;

    @Autowired
    private ProviderAccountRepository providerAccountRepository;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimLineLimitSnapshotRepository claimLineLimitSnapshotRepository;

    @Autowired
    private FinancialOutboxEventRepository financialOutboxEventRepository;

    @Autowired
    private AccountTransactionRepository accountTransactionRepository;

    private String suffix;
    private Employer employer;
    private BenefitPolicy policy;
    private Member member;
    private Provider provider;
    private ProviderContract contract;
    private MedicalCategory category;

    @BeforeEach
    void setupData() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        userRepository.findByUsername("admin").orElseGet(() -> userRepository.save(
                com.waad.tba.modules.rbac.entity.User.builder()
                        .username("admin")
                        .password("password")
                        .fullName("System Admin")
                        .email("admin@waad.ly")
                        .userType("SUPER_ADMIN")
                        .active(true)
                        .build()));

        employer = employerRepository.save(Employer.builder()
                .name("Test Company " + suffix)
                .code("EMP-" + suffix)
                .active(true)
                .build());

        // perMemberLimit is the limit under test in these scenarios. Coverage is 100%
        // so companyShare == requestedAmount == approvedAmount, with zero patient
        // co-pay — this keeps the arithmetic exact and matches the report's numeric
        // scenario (limit 150, prior usage 60, new claim 90).
        policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Standard Plan " + suffix)
                .policyCode("POL-" + suffix)
                .employer(employer)
                // Annual limit is a required field on the entity, but deliberately set high
                // so it never binds — this scenario tests perMemberLimit in isolation.
                .annualLimit(new BigDecimal("100000.00"))
                .perMemberLimit(new BigDecimal("150.00"))
                .defaultCoveragePercent(100)
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE)
                .active(true)
                .build());

        member = memberRepository.save(Member.builder()
                .fullName("Limit Test Member " + suffix)
                .barcode("BC-" + suffix)
                .nationalNumber("NAT-" + suffix)
                .employer(employer)
                .benefitPolicy(policy)
                .active(true)
                .build());
        initializeTemporalAssignments(member);

        provider = providerRepository.save(Provider.builder()
                .name("Limit Test Hospital " + suffix)
                .providerType(ProviderType.HOSPITAL)
                .licenseNumber("LIC-" + suffix)
                .allowAllEmployers(true)
                .active(true)
                .build());

        providerAccountRepository.save(ProviderAccount.builder()
                .providerId(provider.getId())
                .runningBalance(BigDecimal.ZERO)
                .totalApproved(BigDecimal.ZERO)
                .totalPaid(BigDecimal.ZERO)
                .build());

        category = medicalCategoryRepository.save(MedicalCategory.builder()
                .code("CAT-" + suffix)
                .name("General Services")
                .active(true)
                .build());

        benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy)
                .medicalCategory(category)
                .encounterType(EncounterType.OUTPATIENT)
                .coveragePercent(100)
                .active(true)
                .deleted(false)
                .build());

        contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CON-" + suffix)
                .contractNumber("CNT-" + suffix)
                .provider(provider)
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusMonths(11))
                .status(ContractStatus.ACTIVE)
                .active(true)
                .build());
        // Mirrors production: every contract-creating path must also create its
        // effective terms row. The resolver fails closed, so a contract saved
        // without terms makes claim creation impossible for that provider.
        termsService.ensureEffectiveTerms(contract, "TEST");
    }

    /**
     * Creates a distinct service + pricing item priced at exactly `price`, and a
     * distinct visit for the member, so each claim in a scenario is fully independent
     * (no shared visit/pricing-item state between assertions).
     */
    private Visit newVisitWithPricedService(String label, BigDecimal price) {
        MedicalService service = medicalServiceRepository.save(MedicalService.builder()
                .code("SRV-" + suffix + "-" + label)
                .name("Service " + label)
                .categoryId(category.getId())
                .cost(price)
                .active(true)
                .build());

        pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract)
                .serviceCode(service.getCode())
                .serviceName(service.getName())
                .medicalCategory(category)
                .basePrice(price)
                .contractPrice(price)
                .active(true)
                .build());

        return visitRepository.save(Visit.builder()
                .member(member)
                .providerId(provider.getId())
                .visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED)
                .build());
    }

    private ClaimViewDto createDirectClaim(Visit visit, BigDecimal price, String label) {
        MedicalService service = medicalServiceRepository.findAll().stream()
                .filter(s -> s.getCode().equals("SRV-" + suffix + "-" + label))
                .findFirst()
                .orElseThrow();
        // No .status(...) on purpose: this is the direct-entry path under test — the
        // mapper defaults to DRAFT, then ClaimService decides APPROVED/REJECTED itself.
        return claimService.createClaim(ClaimCreateDto.builder()
                .visitId(visit.getId())
                .serviceDate(LocalDate.now())
                .encounterType(EncounterType.OUTPATIENT)
                .lines(List.of(ClaimLineDto.builder()
                        .medicalServiceId(service.getId())
                        .quantity(1)
                        .build()))
                .build());
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void directEntryClaim_atExactRemainingLimit_succeedsWithoutDoubleCounting() {
        // Prior real usage: 60, created via the same direct-entry path.
        Visit visit1 = newVisitWithPricedService("A", new BigDecimal("60.00"));
        ClaimViewDto first = createDirectClaim(visit1, new BigDecimal("60.00"), "A");
        assertThat(first.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(first.getApprovedAmount()).isEqualByComparingTo("60.00");

        // New claim: 90. Correct remaining before this claim = 150 - 60 = 90.
        // A double-counting bug would read "previously used" as if it already
        // included this 90 (or even summed 150+90), and wrongly reject it.
        Visit visit2 = newVisitWithPricedService("B", new BigDecimal("90.00"));
        ClaimViewDto second = createDirectClaim(visit2, new BigDecimal("90.00"), "B");

        assertThat(second.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(second.getApprovedAmount()).isEqualByComparingTo("90.00");

        BigDecimal totalApproved = claimRepository.sumApprovedAmountByMember(
                member.getId(),
                List.of(ClaimStatus.APPROVED, ClaimStatus.SETTLED, ClaimStatus.BATCHED),
                null);
        assertThat(totalApproved).isEqualByComparingTo("150.00");
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void directEntryClaim_oneOverRemainingLimit_isRejectedNotApproved() {
        Visit visit1 = newVisitWithPricedService("A", new BigDecimal("60.00"));
        createDirectClaim(visit1, new BigDecimal("60.00"), "A");

        // Remaining = 150 - 60 = 90. Requesting 91 must fail — not silently become
        // APPROVED with a truncated/zero amount, and not corrupt the ledger.
        Visit visit2 = newVisitWithPricedService("C", new BigDecimal("91.00"));

        assertThatThrownBy(() -> createDirectClaim(visit2, new BigDecimal("91.00"), "C"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("90");

        BigDecimal totalApproved = claimRepository.sumApprovedAmountByMember(
                member.getId(),
                List.of(ClaimStatus.APPROVED, ClaimStatus.SETTLED, ClaimStatus.BATCHED),
                null);
        assertThat(totalApproved).isEqualByComparingTo("60.00");
    }

    /**
     * APPROVED claims are no longer editable at all: the financial snapshot is
     * immutable once the claim is finalized, so contract/pricing changes can never
     * silently rewrite a claim that has already consumed benefit limits (and, for
     * SETTLED claims, already moved money). This test previously asserted that the
     * limit re-validation rejected an over-limit edit; that path is now closed one
     * level earlier — the edit itself is refused regardless of amount, which is a
     * strictly stronger guarantee and also closes the double-counting vector by
     * construction.
     */
    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void editingApprovedClaimLines_isRejectedOutright() {
        // Other real usage: 60 (a separate claim, untouched by the edit under test).
        Visit visitA = newVisitWithPricedService("A", new BigDecimal("60.00"));
        createDirectClaim(visitA, new BigDecimal("60.00"), "A");

        // Claim under test: approved at 50.
        Visit visitB = newVisitWithPricedService("B", new BigDecimal("50.00"));
        ClaimViewDto claimB = createDirectClaim(visitB, new BigDecimal("50.00"), "B");
        assertThat(claimB.getApprovedAmount()).isEqualByComparingTo("50.00");

        // Attempt to re-price claim B's own line to 100 (over the remaining limit).
        // The amount is now irrelevant: an APPROVED claim cannot be edited at all.
        MedicalService repricedService = medicalServiceRepository.save(MedicalService.builder()
                .code("SRV-" + suffix + "-B2")
                .name("Service B2")
                .categoryId(category.getId())
                .cost(new BigDecimal("100.00"))
                .active(true)
                .build());
        pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract)
                .serviceCode(repricedService.getCode())
                .serviceName(repricedService.getName())
                .medicalCategory(category)
                .basePrice(new BigDecimal("100.00"))
                .contractPrice(new BigDecimal("100.00"))
                .active(true)
                .build());

        com.waad.tba.modules.claim.dto.ClaimDataUpdateDto editDto = com.waad.tba.modules.claim.dto.ClaimDataUpdateDto.builder()
                .diagnosisCode("Z00.0")
                .lines(List.of(ClaimLineDto.builder()
                        .id(claimB.getLines().get(0).getId())
                        .medicalServiceId(repricedService.getId())
                        .quantity(1)
                        .build()))
                .build();

        // Commit setup first so the failing edit below runs in its OWN real
        // transaction (via updateClaimData's own @Transactional boundary) instead of
        // participating in this single shared test transaction. Otherwise a
        // mid-transaction Hibernate auto-flush (triggered by the validation query
        // itself) would be visible to a same-transaction re-read even though it is
        // genuinely rolled back at commit — a test-methodology artifact, not a real
        // production data-integrity gap (a separate transaction/request never sees
        // another transaction's uncommitted write).
        org.springframework.test.context.transaction.TestTransaction.flagForCommit();
        org.springframework.test.context.transaction.TestTransaction.end();

        assertThatThrownBy(() -> claimService.updateClaimData(claimB.getId(), editDto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED");

        // The rejected edit must not have persisted a partial/higher amount.
        ClaimViewDto reloaded = claimService.getClaim(claimB.getId());
        assertThat(reloaded.getApprovedAmount()).isEqualByComparingTo("50.00");
    }

    /**
     * Two concurrent direct-entry claims for the SAME member, each requesting 70
     * against a 100-remaining annual ceiling (150 total, 50 already used by a
     * committed prior claim). The member lock must serialize them: one consumes
     * 70 and the other is partially filled at 30, rather than both consuming 70
     * from the same stale balance and overdrawing the ceiling to 190.
     */
    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void concurrentDirectEntryClaims_serializeAndPartiallyFillTheRemainingAnnualLimit() throws Exception {
        // Exercise the canonical POLICY_GENERAL ceiling, not the legacy
        // perMemberLimit compatibility check.
        policy.setAnnualLimit(new BigDecimal("150.00"));
        policy.setPerMemberLimit(new BigDecimal("100000.00"));
        benefitPolicyRepository.saveAndFlush(policy);

        // Committed prior usage: 50. Remaining before the race = 150 - 50 = 100.
        Visit visitPrior = newVisitWithPricedService("PRIOR", new BigDecimal("50.00"));
        createDirectClaim(visitPrior, new BigDecimal("50.00"), "PRIOR");

        // Two racing claims at 70 each: only one can fit in the remaining 100.
        Visit visitX = newVisitWithPricedService("X", new BigDecimal("70.00"));
        Visit visitY = newVisitWithPricedService("Y", new BigDecimal("70.00"));

        // Commit setup so both racing calls below run in their own fresh, real
        // transactions (each createClaim() call is @Transactional on its own) instead
        // of participating in this single shared test transaction.
        org.springframework.test.context.transaction.TestTransaction.flagForCommit();
        org.springframework.test.context.transaction.TestTransaction.end();

        SecurityContext callerContext = SecurityContextHolder.getContext();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> attemptClaim(visitX, "X", callerContext, ready, start));
            Future<Boolean> second = pool.submit(() -> attemptClaim(visitY, "Y", callerContext, ready, start));
            ready.await();
            start.countDown();

            List<Boolean> outcomes = List.of(first.get(), second.get());
            assertThat(outcomes).containsExactly(true, true);
        } finally {
            pool.shutdownNow();
        }

        BigDecimal totalApproved = claimRepository.sumApprovedAmountByMember(
                member.getId(),
                List.of(ClaimStatus.APPROVED, ClaimStatus.SETTLED, ClaimStatus.BATCHED),
                null);
        // The first racer consumes 70 and the second is recalculated under the
        // member lock to consume only the remaining 30. Both requests complete,
        // but the ceiling itself is never overdrawn (50 + 70 + 30 = 150).
        assertThat(totalApproved).isEqualByComparingTo("150.00");

        // Every committed partial/full result must appear exactly once on every
        // financial surface.
        var persistedClaims = claimRepository.findByMemberId(member.getId());
        assertThat(persistedClaims).hasSize(3)
                .allSatisfy(claim -> assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED));
        assertThat(persistedClaims).extracting(c -> c.getApprovedAmount())
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(
                        new BigDecimal("50.00"), new BigDecimal("70.00"), new BigDecimal("30.00"));
        assertThat(persistedClaims.stream()
                .map(c -> c.getPatientCoPay() == null ? BigDecimal.ZERO : c.getPatientCoPay())
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("40.00");

        var account = providerAccountRepository.findByProviderId(provider.getId()).orElseThrow();
        assertThat(account.getTotalApproved()).isEqualByComparingTo("150.00");
        assertThat(account.getRunningBalance()).isEqualByComparingTo("150.00");
        assertThat(accountTransactionRepository.findClaimTransactionsByAccount(account.getId()))
                .hasSize(3)
                .extracting(com.waad.tba.modules.settlement.entity.AccountTransaction::getReferenceId)
                .containsExactlyInAnyOrderElementsOf(persistedClaims.stream().map(c -> c.getId()).toList());

        for (var claim : persistedClaims) {
            assertThat(claimLineLimitSnapshotRepository
                    .findByClaimIdOrderByClaimLineIdAscConsumptionOrderAsc(claim.getId()))
                    .hasSize(1);
            assertThat(financialOutboxEventRepository
                    .findByAggregateTypeAndAggregateIdAndEventType(
                            "CLAIM", claim.getId(), ClaimApprovalOutboxService.EVENT_TYPE))
                    .hasSize(1);
        }
    }

    private boolean attemptClaim(Visit visit, String label, SecurityContext callerContext,
            CountDownLatch ready, CountDownLatch start) {
        SecurityContextHolder.setContext(callerContext);
        ready.countDown();
        try {
            start.await();
            createDirectClaim(visit, new BigDecimal("70.00"), label);
            return true;
        } catch (Exception expectedLimitConflict) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
