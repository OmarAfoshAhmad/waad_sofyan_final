package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.entity.BenefitRuleBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitGroup;
import com.waad.tba.modules.benefitpolicy.enums.AggregationMode;
import com.waad.tba.modules.benefitpolicy.enums.BenefitScopeType;
import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.benefitpolicy.enums.LimitPeriodType;
import com.waad.tba.modules.benefitpolicy.repository.BenefitGroupRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitRuleBucketRepository;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.service.ClaimService;
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
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.providercontract.service.ProviderContractTermsService;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.repository.ProviderAccountRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.repository.VisitRepository;

/**
 * The claim side of the counting split, pinned by the LEDGER ROWS it writes.
 *
 * TimesLimitEvaluator was extracted from this path so approvals and claims
 * share one definition of "how many occurrences". An extraction that changes
 * behaviour is a rewrite wearing the wrong name, and asserting only that a
 * request succeeded would not notice -- so every case below checks the amount
 * AND the count actually recorded, with row counts and idempotency keys.
 *
 * Claims are created through ClaimService.createClaim, the real entry point:
 * adjudication, then finalizeSnapshot (which writes the canonical
 * claim_line_limit_snapshots), then the bucket ledger. Hand-inserting the
 * canonical snapshot would let the test pass on a fixture production cannot
 * produce -- proving something that does not exist.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class ClaimCountingLimitCharacterizationTest extends com.waad.tba.support.PostgresIntegrationTestBase {

    @Autowired private ClaimService claimService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Autowired private EmployerRepository employerRepository;
    @Autowired private BenefitPolicyRepository benefitPolicyRepository;
    @Autowired private BenefitPolicyRuleRepository benefitPolicyRuleRepository;
    @Autowired private BenefitGroupRepository benefitGroupRepository;
    @Autowired private BenefitLimitBucketRepository bucketRepository;
    @Autowired private BenefitRuleBucketRepository ruleBucketRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ProviderAccountRepository providerAccountRepository;
    @Autowired private ProviderContractRepository contractRepository;
    @Autowired private ProviderContractTermsService termsService;
    @Autowired private ProviderContractPricingItemRepository pricingRepository;
    @Autowired private MedicalCategoryRepository medicalCategoryRepository;
    @Autowired private MedicalServiceRepository medicalServiceRepository;
    @Autowired private VisitRepository visitRepository;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository userRepository;
    @Autowired private com.waad.tba.modules.benefitpolicy.service.BenefitBucketLedgerService ledgerService;

    private String suffix;
    private Member member;
    private Provider provider;
    private ProviderContract contract;
    private MedicalCategory category;
    private BenefitPolicy policy;
    private BenefitPolicyRule rule;

    @BeforeEach
    void setupData() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        userRepository.findByUsername("admin").orElseGet(() -> userRepository.save(
                com.waad.tba.modules.rbac.entity.User.builder()
                        .username("admin").password("password").fullName("System Admin")
                        .email("admin@waad.ly").userType("SUPER_ADMIN").active(true).build()));

        Employer employer = employerRepository.save(Employer.builder()
                .name("Counting Co " + suffix).code("CNT-" + suffix).active(true).build());

        policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Counting Plan " + suffix).policyCode("CPOL-" + suffix).employer(employer)
                .annualLimit(new BigDecimal("1000000.00")).defaultCoveragePercent(100)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

        member = memberRepository.save(Member.builder()
                .fullName("Counting Member " + suffix).barcode("CB-" + suffix)
                .nationalNumber("CN-" + suffix).employer(employer).benefitPolicy(policy)
                .active(true).build());
        initializeTemporalAssignments(member);

        provider = providerRepository.save(Provider.builder()
                .name("Counting Hospital " + suffix).providerType(ProviderType.HOSPITAL)
                .licenseNumber("CLIC-" + suffix).allowAllEmployers(true).active(true).build());
        providerAccountRepository.save(ProviderAccount.builder()
                .providerId(provider.getId()).runningBalance(BigDecimal.ZERO)
                .totalApproved(BigDecimal.ZERO).totalPaid(BigDecimal.ZERO).build());

        category = medicalCategoryRepository.save(MedicalCategory.builder()
                .code("CCAT-" + suffix).name("Counting Services").active(true).build());

        rule = benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy).medicalCategory(category)
                .encounterType(EncounterType.OUTPATIENT).coveragePercent(100)
                .active(true).deleted(false).build());

        contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CCON-" + suffix).contractNumber("CCNT-" + suffix).provider(provider)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                .status(ContractStatus.ACTIVE).active(true).build());
        termsService.ensureEffectiveTerms(contract, "TEST");
    }

    /** A bucket linked to the rule. Amount and/or occurrence ceiling, and an optional parent. */
    private BenefitLimitBucket bucket(String label, String amountLimit, Integer timesLimit,
            CountingMethod method, BenefitLimitBucket parent, BenefitScopeType scope, boolean linkToRule) {
        BenefitGroup group = benefitGroupRepository.save(BenefitGroup.builder()
                .policy(policy).code("CG-" + suffix + "-" + label).nameAr("مجموعة " + label)
                .contextType(EncounterType.OUTPATIENT).aggregationMode(AggregationMode.INDIVIDUAL)
                .active(true).build());

        BenefitLimitBucket saved = bucketRepository.save(BenefitLimitBucket.builder()
                .policy(policy).benefitGroup(group).parentBucket(parent)
                .code("CB-" + suffix + "-" + label).nameAr("وعاء " + label)
                .amountLimit(amountLimit == null ? null : new BigDecimal(amountLimit))
                .timesLimit(timesLimit)
                .periodType(LimitPeriodType.ANNUAL).countingMethod(method)
                .consumptionBasis(ConsumptionBasis.COMPANY_SHARE)
                .benefitScopeType(scope).contextType(EncounterType.OUTPATIENT)
                .active(true).build());

        if (linkToRule) {
            ruleBucketRepository.save(BenefitRuleBucket.builder().rule(rule).bucket(saved).build());
        }
        return saved;
    }

    /** Creates the claim through the production entry point and requires it to be approved. */
    private ClaimViewDto approveClaim(int lineCount, int quantityEach, String unitPrice) {
        Visit visit = visitRepository.save(Visit.builder()
                .member(member).providerId(provider.getId())
                .visitDate(LocalDate.now()).status(VisitStatus.REGISTERED).build());

        List<ClaimLineDto> lines = new java.util.ArrayList<>();
        for (int i = 0; i < lineCount; i++) {
            MedicalService service = medicalServiceRepository.save(MedicalService.builder()
                    .code("CSRV-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 6))
                    .name("Counting Service " + i).categoryId(category.getId())
                    .cost(new BigDecimal(unitPrice)).active(true).build());
            pricingRepository.save(ProviderContractPricingItem.builder()
                    .contract(contract).serviceCode(service.getCode()).serviceName(service.getName())
                    .medicalCategory(category).basePrice(new BigDecimal(unitPrice))
                    .contractPrice(new BigDecimal(unitPrice)).active(true).build());

            lines.add(ClaimLineDto.builder()
                    .medicalServiceId(service.getId()).quantity(quantityEach).build());
        }

        ClaimViewDto claim = claimService.createClaim(ClaimCreateDto.builder()
                .visitId(visit.getId()).serviceDate(LocalDate.now())
                .encounterType(EncounterType.OUTPATIENT).lines(lines).build());

        // The canonical snapshot must exist before the ledger is even consulted.
        // If finalizeSnapshot did not run, this is where the test stops -- not
        // at commitClaim with a hand-made row standing in for it.
        assertThat(claim.getStatus()).as("the production path must approve this claim")
                .isEqualTo(ClaimStatus.APPROVED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM claim_line_limit_snapshots WHERE claim_id = ?",
                Long.class, claim.getId()))
                .as("finalizeSnapshot must have written the canonical snapshot").isPositive();
        return claim;
    }

    private BigDecimal amountOn(Long claimId, Long bucketId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(approved_amount), 0) "
                + "FROM benefit_bucket_consumptions WHERE claim_id = ? AND bucket_id = ? "
                + "AND status = 'COMMITTED'", BigDecimal.class, claimId, bucketId);
    }

    private int timesOn(Long claimId, Long bucketId) {
        Integer times = jdbc.queryForObject("SELECT COALESCE(SUM(times_consumed), 0) "
                + "FROM benefit_bucket_consumptions WHERE claim_id = ? AND bucket_id = ? "
                + "AND status = 'COMMITTED'", Integer.class, claimId, bucketId);
        return times == null ? 0 : times;
    }

    private long rowsOn(Long claimId, Long bucketId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM benefit_bucket_consumptions "
                + "WHERE claim_id = ? AND bucket_id = ?", Long.class, claimId, bucketId);
    }

    // ── the three bucket shapes ─────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", roles = {"SUPER_ADMIN"})
    void aCountOnlyBucketRecordsItsOccurrences() {
        BenefitLimitBucket counting = bucket("count", null, 5, CountingMethod.EACH_LINE,
                null, BenefitScopeType.CATEGORY, true);

        ClaimViewDto claim = approveClaim(1, 1, "100.00");

        assertThat(timesOn(claim.getId(), counting.getId())).isEqualTo(1);
        assertThat(rowsOn(claim.getId(), counting.getId())).isEqualTo(1L);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"SUPER_ADMIN"})
    void aMonetaryChildAndACountingParentEachRecordTheirOwnDimension() {
        BenefitLimitBucket parent = bucket("parent", null, 5, CountingMethod.EACH_LINE,
                null, BenefitScopeType.GROUP, false);
        BenefitLimitBucket child = bucket("child", "1000000", null, CountingMethod.EACH_LINE,
                parent, BenefitScopeType.CATEGORY, true);

        ClaimViewDto claim = approveClaim(1, 1, "100.00");

        // Two ceilings, two rows, nothing summed between them and nothing
        // duplicated: the child took the money, the parent took the visit.
        assertThat(amountOn(claim.getId(), child.getId())).isEqualByComparingTo("100.00");
        assertThat(timesOn(claim.getId(), parent.getId())).isEqualTo(1);
        assertThat(rowsOn(claim.getId(), parent.getId()))
                .as("a parent reached once is recorded once").isEqualTo(1L);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"SUPER_ADMIN"})
    void aMixedBucketCarriesBothFiguresOnOneRow() {
        BenefitLimitBucket mixed = bucket("mixed", "1000000", 9, CountingMethod.EACH_UNIT,
                null, BenefitScopeType.CATEGORY, true);

        ClaimViewDto claim = approveClaim(1, 3, "100.00");

        assertThat(amountOn(claim.getId(), mixed.getId())).isEqualByComparingTo("300.00");
        assertThat(timesOn(claim.getId(), mixed.getId())).isEqualTo(3);
        assertThat(rowsOn(claim.getId(), mixed.getId()))
                .as("one bucket, one row, two dimensions").isEqualTo(1L);
    }

    // ── the three counting methods ──────────────────────────────────────

    @ParameterizedTest(name = "{0} over {1} lines of {2} units holds {3}")
    @CsvSource({
            "PER_VISIT, 3, 1, 1",   // one encounter is one visit, however many lines
            "EACH_LINE, 3, 1, 3",   // one per line
            "EACH_UNIT, 2, 4, 8"    // the quantities, summed
    })
    @WithMockUser(username = "admin", roles = {"SUPER_ADMIN"})
    void countingMethodsRecordTheOccurrencesTheyDefine(String method, int lines, int quantity,
            int expectedTimes) {
        BenefitLimitBucket counting = bucket("m" + method, "1000000", 50,
                CountingMethod.valueOf(method), null, BenefitScopeType.CATEGORY, true);

        ClaimViewDto claim = approveClaim(lines, quantity, "100.00");

        assertThat(timesOn(claim.getId(), counting.getId())).isEqualTo(expectedTimes);
    }

    // ── reversal returns both dimensions ────────────────────────────────

    @Test
    @WithMockUser(username = "admin", roles = {"SUPER_ADMIN"})
    void reversalReturnsBothDimensionsToZeroWithoutEditingTheOriginal() {
        BenefitLimitBucket mixed = bucket("rev", "1000000", 9, CountingMethod.EACH_LINE,
                null, BenefitScopeType.CATEGORY, true);
        ClaimViewDto claim = approveClaim(2, 1, "100.00");

        BigDecimal committedBefore = amountOn(claim.getId(), mixed.getId());
        long rowsBefore = rowsOn(claim.getId(), mixed.getId());
        assertThat(committedBefore).isEqualByComparingTo("200.00");
        assertThat(timesOn(claim.getId(), mixed.getId())).isEqualTo(2);

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> ledgerService.reverseClaim(claim.getId()));

        // The originals stay COMMITTED forever; the compensating movements
        // carry the release. What returns to zero is the NET, not the rows.
        assertThat(amountOn(claim.getId(), mixed.getId()))
                .as("originals untouched").isEqualByComparingTo(committedBefore);
        assertThat(rowsOn(claim.getId(), mixed.getId()))
                .as("compensating rows are added, not substituted").isGreaterThan(rowsBefore);

        BigDecimal netAmount = jdbc.queryForObject(
                "SELECT COALESCE(SUM(c.approved_amount - COALESCE(r.released, 0)), 0) "
                        + "FROM benefit_bucket_consumptions c LEFT JOIN ("
                        + "  SELECT reversal_of_id, SUM(approved_amount) AS released "
                        + "  FROM benefit_bucket_consumptions WHERE status='REVERSED' "
                        + "  GROUP BY reversal_of_id) r ON r.reversal_of_id = c.id "
                        + "WHERE c.claim_id = ? AND c.status = 'COMMITTED'",
                BigDecimal.class, claim.getId());
        assertThat(netAmount).as("net money returns to zero").isEqualByComparingTo("0");

        Integer releasedTimes = jdbc.queryForObject(
                "SELECT COALESCE(SUM(times_consumed), 0) FROM benefit_bucket_consumptions "
                        + "WHERE claim_id = ? AND status = 'REVERSED'", Integer.class, claim.getId());
        assertThat(releasedTimes).as("and so do the occurrences").isEqualTo(2);

        // Every compensating movement carries its own key, so a repeated
        // reversal cannot post a second one.
        Long distinctKeys = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT idempotency_key) FROM benefit_bucket_consumptions "
                        + "WHERE claim_id = ?", Long.class, claim.getId());
        Long allRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM benefit_bucket_consumptions WHERE claim_id = ?",
                Long.class, claim.getId());
        assertThat(distinctKeys).as("no key is reused across movements").isEqualTo(allRows);
    }
}
