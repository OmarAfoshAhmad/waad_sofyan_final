package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.entity.BenefitGroup;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.entity.BenefitRuleBucket;
import com.waad.tba.modules.benefitpolicy.enums.AggregationMode;
import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.benefitpolicy.enums.LimitPeriodType;
import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitGroupRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitRuleBucketRepository;
import com.waad.tba.modules.claim.dto.ClaimApproveDto;
import com.waad.tba.modules.claim.dto.ClaimApproveDto.LineDecision;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimLineReviewDecision;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
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
import com.waad.tba.modules.providercontract.entity.ProviderContractTerm;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractTermRepository;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.repository.ProviderAccountRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * finance-00: end-to-end proof of the REVIEWED approval path (DRAFT via
 * SUBMITTED -> UNDER_REVIEW -> APPROVAL_IN_PROGRESS -> APPROVED), which until
 * now was only proven by reading ClaimReviewService.java:397 (it calls the
 * same ClaimFinancialSnapshotService.finalizeSnapshot as the direct-entry
 * path) -- never exercised by an integration test. This closes that gap.
 *
 * requestApproval() only performs the synchronous phase-1 transition and
 * publishes ClaimApprovalRequestedEvent; the real financial work happens in
 * processApproval(), which runs via an @Async AFTER_COMMIT listener --
 * meaning it only fires once this test's accumulated transaction (fixture +
 * createClaim + requestApproval) actually commits. This class is
 * @Transactional so the fixture setup shares one transaction with the
 * request; TestTransaction.flagForCommit()/.end() forces that transaction to
 * commit for real partway through the test, and awaitClaimStatus() then
 * polls for the async listener's result -- the same convention already
 * established by ClaimLifecycleIntegrationTest.commitRequestAndAwaitApproval.
 * (An earlier version of this test called processApproval() directly in
 * addition to requestApproval(), racing the real async listener onto the
 * same SERIALIZABLE claim lock and failing with a genuine
 * "could not serialize access due to concurrent update" from PostgreSQL --
 * proof the two must not run concurrently.)
 *
 * Because the commit is real, BenefitBucketClaimEventListener and
 * ClaimApprovalEventListener (both plain @TransactionalEventListener
 * (AFTER_COMMIT), no @Async) also fire for real once processApproval's own
 * REQUIRES_NEW transaction commits -- exercising the real ledger-commit and
 * provider-credit paths, not a stub.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@Transactional
class ClaimReviewApprovalIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private ClaimService claimService;
    @Autowired private ClaimReviewService claimReviewService;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private BenefitPolicyRepository benefitPolicyRepository;
    @Autowired private BenefitPolicyRuleRepository benefitPolicyRuleRepository;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository userRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ProviderContractRepository contractRepository;
    @Autowired private ProviderContractTermRepository contractTermRepository;
    @Autowired private ProviderContractPricingItemRepository pricingRepository;
    @Autowired private MedicalServiceRepository medicalServiceRepository;
    @Autowired private MedicalCategoryRepository medicalCategoryRepository;
    @Autowired private VisitRepository visitRepository;
    @Autowired private ProviderAccountRepository providerAccountRepository;
    @Autowired private BenefitGroupRepository benefitGroupRepository;
    @Autowired private BenefitLimitBucketRepository benefitLimitBucketRepository;
    @Autowired private BenefitRuleBucketRepository benefitRuleBucketRepository;
    @Autowired private BenefitBucketConsumptionRepository benefitBucketConsumptionRepository;
    @Autowired private ClaimRepository claimRepository;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    /**
     * Polls until the async approval listener has moved the claim into one of
     * the given terminal statuses, then returns it fully loaded (lines eagerly
     * fetched, matching commitRequestAndAwaitApproval's convention elsewhere
     * in the claim test suite).
     */
    private com.waad.tba.modules.claim.entity.Claim awaitClaimStatus(Long claimId, ClaimStatus... terminal) {
        java.util.Set<ClaimStatus> terminalSet = java.util.Set.of(terminal);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        ClaimStatus status;
        do {
            status = claimRepository.findById(claimId).orElseThrow().getStatus();
            if (terminalSet.contains(status)) {
                break;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while awaiting claim status", e);
            }
        } while (System.nanoTime() < deadline);

        var txTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        return txTemplate.execute(tx -> claimRepository.findByIdForFinancialUpdate(claimId).orElseThrow());
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void fullReviewCycleProducesTheSameNumberEverywhereAndCommitsTheLedgerExactlyOnce() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        userRepository.findByUsername("admin").orElseGet(() -> userRepository.save(
                com.waad.tba.modules.rbac.entity.User.builder()
                        .username("admin").password("password").fullName("System Admin")
                        .email("admin@waad.ly").userType("SUPER_ADMIN").active(true).build()));

        Employer employer = employerRepository.save(Employer.builder()
                .name("Review Path Co " + suffix).code("EMP-" + suffix).active(true).build());

        // 80% coverage -> patient co-pay is part of the proof too, not just the discount.
        BenefitPolicy policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Plan " + suffix).policyCode("POL-" + suffix).employer(employer)
                .annualLimit(new BigDecimal("1000000.00")).defaultCoveragePercent(80)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

        Member member = memberRepository.save(Member.builder()
                .fullName("Member " + suffix).barcode("BC-" + suffix).nationalNumber("NAT-" + suffix)
                .employer(employer).benefitPolicy(policy).active(true).build());

        Provider provider = providerRepository.save(Provider.builder()
                .name("Hospital " + suffix).providerType(ProviderType.HOSPITAL)
                .licenseNumber("LIC-" + suffix).allowAllEmployers(true).active(true).build());

        ProviderAccount account = providerAccountRepository.save(ProviderAccount.builder()
                .providerId(provider.getId()).runningBalance(BigDecimal.ZERO)
                .totalApproved(BigDecimal.ZERO).totalPaid(BigDecimal.ZERO).build());

        MedicalCategory category = medicalCategoryRepository.save(MedicalCategory.builder()
                .code("CAT-" + suffix).name("General Services").active(true).build());

        BenefitPolicyRule rule = benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy).medicalCategory(category).encounterType(EncounterType.OUTPATIENT)
                .coveragePercent(80).active(true).deleted(false).build());

        // A real bucket linked to the rule, so the ledger actually commits --
        // proves the review path also drives the bucket ledger, not just the
        // direct-entry path already covered elsewhere.
        BenefitGroup group = benefitGroupRepository.save(BenefitGroup.builder()
                .policy(policy).code("GRP-" + suffix).nameAr("مجموعة مسار المراجعة")
                .contextType(EncounterType.OUTPATIENT).aggregationMode(AggregationMode.SHARED)
                .active(true).build());
        BenefitLimitBucket bucket = benefitLimitBucketRepository.save(BenefitLimitBucket.builder()
                .policy(policy).benefitGroup(group).code("BUC-" + suffix).nameAr("سقف مسار المراجعة")
                .contextType(EncounterType.OUTPATIENT).amountLimit(new BigDecimal("100000.00"))
                .periodType(LimitPeriodType.ANNUAL).countingMethod(CountingMethod.EACH_LINE)
                .consumptionBasis(ConsumptionBasis.COMPANY_SHARE)
                .benefitScopeType(com.waad.tba.modules.benefitpolicy.enums.BenefitScopeType.GROUP)
                .shared(false).active(true).build());
        benefitRuleBucketRepository.save(BenefitRuleBucket.builder().rule(rule).bucket(bucket).build());

        // 10% contract discount, BEFORE mode -- the same ordering rule that was
        // proven correct for direct entry must also hold through review.
        ProviderContract contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CON-" + suffix).contractNumber("CNT-" + suffix).provider(provider)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                .status(ContractStatus.ACTIVE).discountPercent(new BigDecimal("10.00"))
                .discountBeforeRejection(true).active(true).build());
        contractTermRepository.save(ProviderContractTerm.builder()
                .contract(contract).effectiveFrom(contract.getStartDate())
                .discountPercent(new BigDecimal("10.00")).discountBeforeRejection(true)
                .changeReason("Test initial terms").build());

        MedicalService service = medicalServiceRepository.save(MedicalService.builder()
                .code("SRV-" + suffix).name("Service " + suffix).categoryId(category.getId())
                .cost(new BigDecimal("1000.00")).active(true).build());

        pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract).serviceCode(service.getCode()).serviceName(service.getName())
                .medicalCategory(category).basePrice(new BigDecimal("1000.00"))
                .contractPrice(new BigDecimal("1000.00")).active(true).build());

        Visit visit = visitRepository.save(Visit.builder()
                .member(member).providerId(provider.getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());

        // gross=1000, coverage=80% -> patient=200, providerShare=800. No rejection.
        // BEFORE mode with 10% discount: discount=80, company=720.
        ClaimViewDto created = claimService.createClaim(ClaimCreateDto.builder()
                .visitId(visit.getId()).serviceDate(LocalDate.now()).encounterType(EncounterType.OUTPATIENT)
                .status(ClaimStatus.SUBMITTED)
                .lines(List.of(ClaimLineDto.builder().medicalServiceId(service.getId()).quantity(1).build()))
                .build());
        assertThat(created.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        Long claimId = created.getId();
        Long lineId = created.getLines().get(0).getId();

        // Phase 1: reviewer decision + synchronous transition to APPROVAL_IN_PROGRESS.
        ClaimApproveDto approveDto = ClaimApproveDto.builder()
                .lineDecisions(List.of(LineDecision.builder()
                        .lineId(lineId).decision(ClaimLineReviewDecision.APPROVE).build()))
                .notes("Reviewed and approved in test")
                .build();
        ClaimViewDto afterRequest = claimReviewService.requestApproval(claimId, approveDto);
        assertThat(afterRequest.getStatus()).isEqualTo(ClaimStatus.APPROVAL_IN_PROGRESS);

        // Phase 2 (processApproval) is @Async + @TransactionalEventListener(AFTER_COMMIT):
        // it only fires once this test's own accumulated transaction (fixture
        // setup + createClaim + requestApproval) actually commits. Calling
        // processApproval directly here as well -- instead of committing and
        // waiting -- caused two concurrent transactions to fight over the same
        // SERIALIZABLE claim lock (proven by a real PSQLException during this
        // test's development); committing and polling is the only correct way
        // to observe the real async path deterministically.
        TestTransaction.flagForCommit();
        TestTransaction.end();

        var approved = awaitClaimStatus(claimId, ClaimStatus.APPROVED, ClaimStatus.REJECTED);
        assertThat(approved.getStatus()).isEqualTo(ClaimStatus.APPROVED);

        // 1) The claim total equals the sum of its own lines' company share --
        // GUARD 2 already enforces this at the approval gate; this re-asserts
        // it as an end-to-end fact, not just "no exception was thrown".
        BigDecimal sumCompanyShare = approved.getLines().stream()
                .map(l -> l.getCompanyShare() == null ? BigDecimal.ZERO : l.getCompanyShare())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(approved.getApprovedAmount()).isEqualByComparingTo(sumCompanyShare);
        assertThat(approved.getApprovedAmount()).isEqualByComparingTo("720.00");
        assertThat(approved.getPatientCoPay()).isEqualByComparingTo("200.00");

        // 2) The approval gate did not recompute: the BEFORE-mode discount
        // (80, not 65) is exactly what ClaimLineFinancialEngine produces.
        assertThat(approved.getRequestedAmount().subtract(approved.getApprovedAmount())
                .subtract(approved.getPatientCoPay()).subtract(approved.getRefusedAmount()))
                .isEqualByComparingTo("80.00");

        // 3) The bucket ledger committed exactly once, for the correct amount.
        List<BenefitBucketConsumption> committed = benefitBucketConsumptionRepository
                .findByClaimIdAndStatus(claimId, BenefitBucketConsumption.Status.COMMITTED);
        assertThat(committed).hasSize(1);
        assertThat(committed.get(0).getApprovedAmount()).isEqualByComparingTo("720.00");

        // 4) The provider account was credited the identical amount.
        ProviderAccount refreshedAccount = providerAccountRepository.findById(account.getId()).orElseThrow();
        assertThat(refreshedAccount.getTotalApproved()).isEqualByComparingTo("720.00");
        assertThat(refreshedAccount.getRunningBalance()).isEqualByComparingTo("720.00");
    }
}
