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

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption;
import com.waad.tba.modules.benefitpolicy.entity.BenefitGroup;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
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
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
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
 * Proves, against real PostgreSQL, that the three-way interaction between
 * patient co-pay, a manual reviewer rejection, and the provider contract's
 * discount produces the exact net amount that is later credited to the
 * provider account (ProviderAccountService.creditForClaim reads
 * Claim.netProviderAmount verbatim -- see the 2026-05-01 double-deduction fix
 * documented there).
 *
 * The three components must combine in this order (ClaimMapper.
 * processEngineCalculations, lines ~346-392):
 *   1. patientShare is split off allowedGross FIRST (the member already paid
 *      it directly) -- providerShare = allowedGross - patientShare.
 *   2. The contract's discountBeforeRejection flag then decides whether the
 *      discount is taken on the full providerShare before the rejection is
 *      subtracted, or on the remainder after the rejection is subtracted.
 * These two orderings are NOT equivalent and must be tested explicitly:
 * BEFORE discounts a larger base (more discount, more surplus visible on
 * "companyDiscountAmount"; less lost to rejection); AFTER discounts the
 * post-rejection remainder (less discount).
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
// Deliberately NOT @Transactional: the ledger-divergence characterization test
// below relies on the real BEFORE_COMMIT BenefitBucketClaimEventListener, which
// never fires inside a Spring test transaction that is rolled back instead of
// committed. Isolation across tests instead comes from a random suffix on every
// entity name/code, matching BenefitBucketConcurrencyIntegrationTest's convention.
class ClaimCopayDiscountRejectionOrderingIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private ClaimService claimService;
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
     * Builds one fully independent claim fixture (employer/policy/member/
     * provider/contract/service/visit) with the given coverage percent and
     * contract discount timing, then submits a single line with the given
     * gross amount and manual reviewer rejection. Every test gets its own
     * isolated provider/employer so the scenarios cannot interfere.
     */
    private ClaimViewDto submitClaim(int coveragePercent, boolean discountBeforeRejection,
            BigDecimal grossAmount, BigDecimal manualRefusedAmount) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        userRepository.findByUsername("admin").orElseGet(() -> userRepository.save(
                com.waad.tba.modules.rbac.entity.User.builder()
                        .username("admin").password("password").fullName("System Admin")
                        .email("admin@waad.ly").userType("SUPER_ADMIN").active(true).build()));

        Employer employer = employerRepository.save(Employer.builder()
                .name("Ordering Test Co " + suffix).code("EMP-" + suffix).active(true).build());

        BenefitPolicy policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Plan " + suffix).policyCode("POL-" + suffix).employer(employer)
                .annualLimit(new BigDecimal("1000000.00")).defaultCoveragePercent(coveragePercent)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

        Member member = memberRepository.save(Member.builder()
                .fullName("Member " + suffix).barcode("BC-" + suffix).nationalNumber("NAT-" + suffix)
                .employer(employer).benefitPolicy(policy).active(true).build());

        Provider provider = providerRepository.save(Provider.builder()
                .name("Hospital " + suffix).providerType(ProviderType.HOSPITAL)
                .licenseNumber("LIC-" + suffix).allowAllEmployers(true).active(true).build());

        providerAccountRepository.save(ProviderAccount.builder()
                .providerId(provider.getId()).runningBalance(BigDecimal.ZERO)
                .totalApproved(BigDecimal.ZERO).totalPaid(BigDecimal.ZERO).build());

        MedicalCategory category = medicalCategoryRepository.save(MedicalCategory.builder()
                .code("CAT-" + suffix).name("General Services").active(true).build());

        benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy).medicalCategory(category).encounterType(EncounterType.OUTPATIENT)
                .coveragePercent(coveragePercent).active(true).deleted(false).build());

        ProviderContract contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CON-" + suffix).contractNumber("CNT-" + suffix).provider(provider)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                .status(ContractStatus.ACTIVE).discountPercent(new BigDecimal("10.00"))
                .discountBeforeRejection(discountBeforeRejection).active(true).build());
        contractTermRepository.save(ProviderContractTerm.builder()
                .contract(contract).effectiveFrom(contract.getStartDate())
                .discountPercent(new BigDecimal("10.00")).discountBeforeRejection(discountBeforeRejection)
                .changeReason("Test initial terms").build());

        MedicalService service = medicalServiceRepository.save(MedicalService.builder()
                .code("SRV-" + suffix).name("Service " + suffix).categoryId(category.getId())
                .cost(grossAmount).active(true).build());

        pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract).serviceCode(service.getCode()).serviceName(service.getName())
                .medicalCategory(category).basePrice(grossAmount).contractPrice(grossAmount)
                .active(true).build());

        Visit visit = visitRepository.save(Visit.builder()
                .member(member).providerId(provider.getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());

        return claimService.createClaim(ClaimCreateDto.builder()
                .visitId(visit.getId()).serviceDate(LocalDate.now()).encounterType(EncounterType.OUTPATIENT)
                .lines(List.of(ClaimLineDto.builder()
                        .medicalServiceId(service.getId()).quantity(1)
                        .manualRefusedAmount(manualRefusedAmount)
                        .build()))
                .build());
    }

    // ── Rejection + discount, no co-pay (isolates the ordering itself) ────────

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void beforeMode_discountsTheFullProviderShareThenSubtractsTheRejection() {
        // gross=500, coverage=100% -> providerShare=500. reject 100.
        // BEFORE: discount = 500*10% = 50; providerNet = 450; payable = 450-100 = 350.
        ClaimViewDto claim = submitClaim(100, true, new BigDecimal("500.00"), new BigDecimal("100.00"));

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getRequestedAmount()).isEqualByComparingTo("500.00");
        assertThat(claim.getPatientCoPay()).isEqualByComparingTo("0.00");
        assertThat(claim.getRefusedAmount()).isEqualByComparingTo("100.00");
        assertThat(claim.getCompanyDiscountAmount()).isEqualByComparingTo("50.00");
        assertThat(claim.getNetProviderAmount()).isEqualByComparingTo("350.00");
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void afterMode_subtractsTheRejectionFirstThenDiscountsOnlyTheRemainder() {
        // Same inputs as above, only discountBeforeRejection flips.
        // AFTER: afterRejection = 500-100 = 400; discount = 400*10% = 40; payable = 360.
        ClaimViewDto claim = submitClaim(100, false, new BigDecimal("500.00"), new BigDecimal("100.00"));

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getRequestedAmount()).isEqualByComparingTo("500.00");
        assertThat(claim.getPatientCoPay()).isEqualByComparingTo("0.00");
        assertThat(claim.getRefusedAmount()).isEqualByComparingTo("100.00");
        assertThat(claim.getCompanyDiscountAmount()).isEqualByComparingTo("40.00");
        assertThat(claim.getNetProviderAmount()).isEqualByComparingTo("360.00");
    }

    // ── Co-pay + rejection + discount together (the full real-world case) ─────

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void beforeMode_withCopay_deductsThePatientShareFirstThenOrdersDiscountBeforeRejection() {
        // gross=1000, coverage=80% -> patientShare=200, providerShare=800. reject 150.
        // BEFORE: discount = 800*10% = 80; providerNet = 720; payable = 720-150 = 570.
        ClaimViewDto claim = submitClaim(80, true, new BigDecimal("1000.00"), new BigDecimal("150.00"));

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getRequestedAmount()).isEqualByComparingTo("1000.00");
        assertThat(claim.getPatientCoPay()).isEqualByComparingTo("200.00");
        assertThat(claim.getRefusedAmount()).isEqualByComparingTo("150.00");
        assertThat(claim.getCompanyDiscountAmount()).isEqualByComparingTo("80.00");
        assertThat(claim.getNetProviderAmount()).isEqualByComparingTo("570.00");

        // The four components must reconstruct the gross exactly -- this is the
        // same identity the settlement screen relies on.
        BigDecimal reconstructed = claim.getPatientCoPay()
                .add(claim.getRefusedAmount())
                .add(claim.getCompanyDiscountAmount())
                .add(claim.getNetProviderAmount());
        assertThat(reconstructed).isEqualByComparingTo(claim.getRequestedAmount());
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void afterMode_withCopay_deductsThePatientShareFirstThenOrdersRejectionBeforeDiscount() {
        // Same inputs as above, only discountBeforeRejection flips.
        // AFTER: afterRejection = 800-150 = 650; discount = 650*10% = 65; payable = 585.
        ClaimViewDto claim = submitClaim(80, false, new BigDecimal("1000.00"), new BigDecimal("150.00"));

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getRequestedAmount()).isEqualByComparingTo("1000.00");
        assertThat(claim.getPatientCoPay()).isEqualByComparingTo("200.00");
        assertThat(claim.getRefusedAmount()).isEqualByComparingTo("150.00");
        assertThat(claim.getCompanyDiscountAmount()).isEqualByComparingTo("65.00");
        assertThat(claim.getNetProviderAmount()).isEqualByComparingTo("585.00");

        BigDecimal reconstructed = claim.getPatientCoPay()
                .add(claim.getRefusedAmount())
                .add(claim.getCompanyDiscountAmount())
                .add(claim.getNetProviderAmount());
        assertThat(reconstructed).isEqualByComparingTo(claim.getRequestedAmount());
    }

    // ── Full line rejection still applies the discount timing rule correctly ──

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void fullyRejectedLineLeavesNothingPayableAndIsAutoRejectedNotZeroApproved() {
        // gross=300, coverage=100%, the entire provider share is manually refused
        // (300, i.e. >= providerShare). Both orderings must floor payable at zero.
        // ClaimService.createClaim never lets a direct-entry claim become APPROVED
        // with a zero payable amount -- it auto-rejects instead (see the
        // "totalApproved > 0" gate in createClaim). This proves that gate still
        // holds once a contract discount is in the mix, not just for a raw
        // zero-coverage rule.
        ClaimViewDto claim = submitClaim(100, true, new BigDecimal("300.00"), new BigDecimal("300.00"));

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(claim.getNetProviderAmount()).isEqualByComparingTo("0.00");
    }

    // ── Closure proof: the ledger and the claim total now agree ──────────────

    /**
     * CLOSURE PROOF for the finance-00 defect (was a characterization test
     * before step 3/4; the divergence it documented is now fixed): proves
     * that {@code Claim.approvedAmount} (what the annual policy ceiling in
     * BenefitBucketLedgerService.validatePolicyAnnualLimit sums across a
     * member's claims) and {@code SUM(BenefitBucketConsumption.approvedAmount)}
     * for the SAME claim (what the benefit bucket ledger actually committed,
     * sourced from ClaimLine.companyShare) are now the SAME NUMBER for the
     * identical approved claim -- because ClaimFinancialSnapshotService no
     * longer recomputes anything; it only locks, guards, and validates the
     * ceiling against the number ClaimMapper already computed correctly.
     */
    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void claimApprovedAmountNowMatchesWhatTheBenefitBucketLedgerActuallyCommitted() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        userRepository.findByUsername("admin").orElseGet(() -> userRepository.save(
                com.waad.tba.modules.rbac.entity.User.builder()
                        .username("admin").password("password").fullName("System Admin")
                        .email("admin@waad.ly").userType("SUPER_ADMIN").active(true).build()));

        Employer employer = employerRepository.save(Employer.builder()
                .name("Ledger Divergence Co " + suffix).code("EMP-" + suffix).active(true).build());

        // coverage=100%: isolates the discount-timing divergence from the
        // separate patient-responsibility divergence already proven above.
        BenefitPolicy policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Plan " + suffix).policyCode("POL-" + suffix).employer(employer)
                .annualLimit(new BigDecimal("1000000.00")).defaultCoveragePercent(100)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

        Member member = memberRepository.save(Member.builder()
                .fullName("Member " + suffix).barcode("BC-" + suffix).nationalNumber("NAT-" + suffix)
                .employer(employer).benefitPolicy(policy).active(true).build());

        Provider provider = providerRepository.save(Provider.builder()
                .name("Hospital " + suffix).providerType(ProviderType.HOSPITAL)
                .licenseNumber("LIC-" + suffix).allowAllEmployers(true).active(true).build());

        providerAccountRepository.save(ProviderAccount.builder()
                .providerId(provider.getId()).runningBalance(BigDecimal.ZERO)
                .totalApproved(BigDecimal.ZERO).totalPaid(BigDecimal.ZERO).build());

        MedicalCategory category = medicalCategoryRepository.save(MedicalCategory.builder()
                .code("CAT-" + suffix).name("General Services").active(true).build());

        BenefitPolicyRule rule = benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy).medicalCategory(category).encounterType(EncounterType.OUTPATIENT)
                .coveragePercent(100).active(true).deleted(false).build());

        // A real bucket, linked to the rule, so the ledger actually commits a
        // consumption row -- an unlinked rule commits nothing, which would make
        // this test trivially (and uninterestingly) show 0 != approvedAmount.
        BenefitGroup group = benefitGroupRepository.save(BenefitGroup.builder()
                .policy(policy).code("GRP-" + suffix).nameAr("مجموعة الاختبار")
                .contextType(EncounterType.OUTPATIENT).aggregationMode(AggregationMode.SHARED)
                .active(true).build());
        BenefitLimitBucket bucket = benefitLimitBucketRepository.save(BenefitLimitBucket.builder()
                .policy(policy).benefitGroup(group).code("BUC-" + suffix).nameAr("سقف الاختبار")
                .contextType(EncounterType.OUTPATIENT).amountLimit(new BigDecimal("100000.00"))
                .periodType(LimitPeriodType.ANNUAL).countingMethod(CountingMethod.EACH_LINE)
                .consumptionBasis(ConsumptionBasis.COMPANY_SHARE)
                .benefitScopeType(com.waad.tba.modules.benefitpolicy.enums.BenefitScopeType.GROUP)
                .shared(false).active(true).build());
        benefitRuleBucketRepository.save(BenefitRuleBucket.builder().rule(rule).bucket(bucket).build());

        // 10% contract discount, BEFORE mode, plus a manual partial rejection.
        // With zero rejection, BEFORE and AFTER collapse to the same number and
        // this test would pass by accident even with the defect present -- the
        // rejection is what makes finalizeSnapshot's ordering-blind formula
        // diverge from ClaimMapper's ordering-aware one.
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
                .cost(new BigDecimal("500.00")).active(true).build());

        pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract).serviceCode(service.getCode()).serviceName(service.getName())
                .medicalCategory(category).basePrice(new BigDecimal("500.00"))
                .contractPrice(new BigDecimal("500.00")).active(true).build());

        Visit visit = visitRepository.save(Visit.builder()
                .member(member).providerId(provider.getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());

        // gross=500, coverage=100% -> providerShare=500. reject 100. BEFORE mode
        // (ClaimMapper, correct): discount=500*10%=50, providerNet=450,
        // rejectedAmount=min(450,100)=100, finalPayable=350.
        // finalizeSnapshot (wrong, always "after"): refused=100, accepted=400,
        // patient=0, companyBeforeDiscount=400, discount=40, payable=360.
        ClaimViewDto claim = claimService.createClaim(ClaimCreateDto.builder()
                .visitId(visit.getId()).serviceDate(LocalDate.now()).encounterType(EncounterType.OUTPATIENT)
                .lines(List.of(ClaimLineDto.builder().medicalServiceId(service.getId()).quantity(1)
                        .manualRefusedAmount(new BigDecimal("100.00")).build()))
                .build());
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);

        // What ClaimMapper actually computed and stored per line (correct,
        // discount-timing-aware, BEFORE mode with the 100 rejection): 350.00.
        var txTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        BigDecimal correctNetFromLines = txTemplate.execute(status -> claimRepository.findById(claim.getId())
                .orElseThrow()
                .getLines().stream()
                .map(l -> l.getCompanyShare() == null ? BigDecimal.ZERO : l.getCompanyShare())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertThat(correctNetFromLines).isEqualByComparingTo("350.00");

        // What the benefit bucket ledger actually committed for this claim --
        // sourced from that same, correct ClaimLine.companyShare.
        BigDecimal committedInLedger = benefitBucketConsumptionRepository
                .findByClaimIdAndStatus(claim.getId(), BenefitBucketConsumption.Status.COMMITTED)
                .stream()
                .map(BenefitBucketConsumption::getApprovedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(committedInLedger).isEqualByComparingTo("350.00");

        // What is ACTUALLY persisted on Claim.approvedAmount -- the number the
        // annual policy ceiling sums across all of this member's claims. Today
        // this is finalizeSnapshot's own recomputation, not ClaimMapper's.
        BigDecimal actualClaimApprovedAmount = claim.getApprovedAmount();

        // THE CLOSURE: the ledger and the claim total (used for the general
        // annual ceiling) now agree for the identical approved claim.
        assertThat(actualClaimApprovedAmount)
                .as("Claim.approvedAmount must equal what the benefit ledger actually "
                        + "committed for this claim")
                .isEqualByComparingTo(committedInLedger)
                .isEqualByComparingTo("350.00");
    }
}
