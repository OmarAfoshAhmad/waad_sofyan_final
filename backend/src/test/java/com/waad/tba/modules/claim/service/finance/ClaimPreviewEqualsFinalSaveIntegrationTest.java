package com.waad.tba.modules.claim.service.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.entity.BenefitGroup;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.entity.BenefitRuleBucket;
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
import com.waad.tba.modules.claim.dto.engine.BulkCoverageEngineRequest;
import com.waad.tba.modules.claim.dto.engine.ClaimLineInput;
import com.waad.tba.modules.claim.dto.engine.CoverageResult;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.claim.service.ClaimService;
import com.waad.tba.modules.claim.service.CoverageEngineService;
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
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.repository.VisitRepository;

/**
 * P1.6 Final — the decisive gate: {@code CoverageEngineService.calculateBulk}
 * is the SAME single source of truth for both the live UI preview
 * ({@code POST /api/v1/claims/calculate-bulk}, called here directly, not
 * persisting anything) and the entity mapping inside
 * {@code ClaimService.createClaim} (via {@code ClaimMapper}). Before P1.6,
 * Engine B re-resolved the limit independently after Save-A -- this test
 * proves that gap is closed: the SAME Physio decision Preview reports is
 * exactly what lands on the saved {@link ClaimLine}.
 *
 * The golden case: price=100, quantity=3 requested, only 2 sessions remain
 * on a TIMES-only bucket, coverage=75%.
 * <pre>
 *                  Preview   Saved
 * Approved Qty        2         2
 * Company (75% of 200)  150.00    150.00
 * Copay (25% of 200)     50.00     50.00
 * Non-covered (1 refused unit)  100.00   100.00
 * </pre>
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class ClaimPreviewEqualsFinalSaveIntegrationTest extends com.waad.tba.support.PostgresIntegrationTestBase {

    @Autowired private ClaimService claimService;
    @Autowired private CoverageEngineService coverageEngineService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ClaimRepository claimRepository;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

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

    private String suffix;
    private Member member;
    private Provider provider;
    private ProviderContract contract;
    private BenefitPolicy policy;
    private MedicalCategory category;
    private BenefitPolicyRule physioRule;
    private BenefitLimitBucket physioBucket;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        userRepository.findByUsername("admin").orElseGet(() -> userRepository.save(
                com.waad.tba.modules.rbac.entity.User.builder()
                        .username("admin").password("password").fullName("System Admin")
                        .email("admin@waad.ly").userType("SUPER_ADMIN").active(true).build()));

        Employer employer = employerRepository.save(Employer.builder()
                .name("Physio Co " + suffix).code("PHY-" + suffix).active(true).build());

        policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Physio Plan " + suffix).policyCode("PPOL-" + suffix).employer(employer)
                .annualLimit(new BigDecimal("1000000.00")).defaultCoveragePercent(100)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

        member = memberRepository.save(Member.builder()
                .fullName("Physio Member " + suffix).barcode("PB-" + suffix)
                .nationalNumber("PN-" + suffix).employer(employer).benefitPolicy(policy)
                .active(true).build());
        initializeTemporalAssignments(member);

        provider = providerRepository.save(Provider.builder()
                .name("Physio Clinic " + suffix).providerType(ProviderType.HOSPITAL)
                .licenseNumber("PLIC-" + suffix).allowAllEmployers(true).active(true).build());
        providerAccountRepository.save(ProviderAccount.builder()
                .providerId(provider.getId()).runningBalance(BigDecimal.ZERO)
                .totalApproved(BigDecimal.ZERO).totalPaid(BigDecimal.ZERO).build());

        category = medicalCategoryRepository.save(MedicalCategory.builder()
                .code("PCAT-" + suffix).name("Physiotherapy").active(true).build());

        // The Physio golden case: 75% coverage, exactly what makes
        // company/copay land on 150.00/50.00 for a 200.00 allowed amount.
        physioRule = benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy).medicalCategory(category)
                .encounterType(EncounterType.OUTPATIENT).coveragePercent(75)
                .active(true).deleted(false).build());

        BenefitGroup group = benefitGroupRepository.save(BenefitGroup.builder()
                .policy(policy).code("PG-" + suffix).nameAr("مجموعة العلاج الطبيعي")
                .contextType(EncounterType.OUTPATIENT).aggregationMode(AggregationMode.INDIVIDUAL)
                .active(true).build());

        // TIMES-only ceiling (no amountLimit at all -- exactly Physio's shape,
        // the one EffectiveLimitResolver/ApplicableLimitResolver silently
        // skipped, per P1.1's confirmed gap). Configured=2 directly represents
        // "only 2 sessions remain" without needing a separate pre-consumption
        // fixture step.
        physioBucket = bucketRepository.save(BenefitLimitBucket.builder()
                .policy(policy).benefitGroup(group)
                .code("PB-" + suffix).nameAr("جلسات العلاج الطبيعي")
                .timesLimit(2).periodType(LimitPeriodType.ANNUAL).countingMethod(CountingMethod.EACH_UNIT)
                .consumptionBasis(ConsumptionBasis.COMPANY_SHARE)
                .benefitScopeType(BenefitScopeType.SERVICE).contextType(EncounterType.OUTPATIENT)
                .active(true).build());
        ruleBucketRepository.save(BenefitRuleBucket.builder().rule(physioRule).bucket(physioBucket).build());

        contract = contractRepository.save(ProviderContract.builder()
                .contractCode("PCON-" + suffix).contractNumber("PCNT-" + suffix).provider(provider)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                .status(ContractStatus.ACTIVE).active(true).build());
        termsService.ensureEffectiveTerms(contract, "TEST");
    }

    private CoverageResult preview(int quantity, String unitPrice) {
        ClaimLineInput line = ClaimLineInput.builder()
                .lineId("PREVIEW-1").serviceId(1L).serviceCategoryId(category.getId())
                .quantity(quantity).enteredUnitPrice(new BigDecimal(unitPrice))
                .contractPrice(new BigDecimal(unitPrice)).build();
        BulkCoverageEngineRequest request = BulkCoverageEngineRequest.builder()
                .policyId(policy.getId()).memberId(member.getId())
                .serviceYear(LocalDate.now().getYear()).serviceDate(LocalDate.now())
                .encounterType(EncounterType.OUTPATIENT).lines(List.of(line)).build();
        return coverageEngineService.calculateBulk(request).get(0);
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    @DisplayName("P1.6 Final — the Physio gate: Preview and the saved ClaimLine agree exactly, for the first time")
    void previewEqualsFinalSaveForThePhysioGate() {
        // PREVIEW: the same call POST /api/v1/claims/calculate-bulk makes,
        // hitting the SAME real DB balances, before anything is saved.
        CoverageResult previewResult = preview(3, "100.00");
        assertThat(previewResult.getUsageDetails().getApprovedUnits()).isEqualTo(2);
        assertThat(previewResult.getCompanyShare()).isEqualByComparingTo("150.00");
        assertThat(previewResult.getPatientShare()).isEqualByComparingTo("50.00");
        assertThat(previewResult.getLimitRefused()).isEqualByComparingTo("100.00");

        // SAVE: the real entry point. Preview ran first and consumed nothing
        // (it is read-only), so the bucket is untouched -- still 2 remaining.
        Visit visit = visitRepository.save(Visit.builder()
                .member(member).providerId(provider.getId())
                .visitDate(LocalDate.now()).status(VisitStatus.REGISTERED).build());
        MedicalService service = medicalServiceRepository.save(MedicalService.builder()
                .code("PSRV-" + suffix).name("Physio Session").categoryId(category.getId())
                .cost(new BigDecimal("100.00")).active(true).build());
        pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract).serviceCode(service.getCode()).serviceName(service.getName())
                .medicalCategory(category).basePrice(new BigDecimal("100.00"))
                .contractPrice(new BigDecimal("100.00")).active(true).build());

        ClaimViewDto saved = claimService.createClaim(ClaimCreateDto.builder()
                .visitId(visit.getId()).serviceDate(LocalDate.now())
                .encounterType(EncounterType.OUTPATIENT)
                .lines(List.of(ClaimLineDto.builder().medicalServiceId(service.getId()).quantity(3).build()))
                .build());

        assertThat(saved.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        record PersistedLine(Integer approvedQuantity, BigDecimal companyShare,
                BigDecimal patientCoverageShare, BigDecimal limitRefused) {}
        PersistedLine persistedLine = new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .execute(status -> {
                    ClaimLine line = claimRepository.findById(saved.getId()).orElseThrow().getLines().get(0);
                    return new PersistedLine(line.getApprovedQuantity(), line.getCompanyShare(),
                            line.getPatientCoverageShare(), line.getLimitRefused());
                });

        // The decisive comparison: every figure Preview reported survives
        // onto the persisted entity unchanged.
        assertThat(persistedLine.approvedQuantity()).isEqualTo(2);
        assertThat(persistedLine.companyShare()).isEqualByComparingTo("150.00");
        assertThat(persistedLine.patientCoverageShare()).isEqualByComparingTo("50.00");
        assertThat(persistedLine.limitRefused()).isEqualByComparingTo("100.00");

        // claim_line_limit_snapshots has no TIMES columns at all (the P1.1
        // baseline gap ClaimLimitSnapshotFactory's own javadoc documents,
        // closed later by P1.11 -- Snapshot = Ledger) -- Physio's own TIMES
        // bucket therefore writes no row of its own. The one row that DOES
        // exist here is the policy's general AMOUNT ceiling (annualLimit),
        // which the same canonical decision also evaluated and consumed
        // (150.00 of the 1,000,000.00 annual ceiling) -- itself proof that
        // this row, too, traces to the SAME resolution, not a second one.
        String benefitScopeType = jdbc.queryForObject(
                "SELECT benefit_scope_type FROM claim_line_limit_snapshots WHERE claim_id = ?",
                String.class, saved.getId());
        assertThat(benefitScopeType).isEqualTo("POLICY_GENERAL");
    }
}
