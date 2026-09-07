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
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.enums.PricingMode;
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

@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class ClaimMultiLineContextAndLimitIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private ClaimService claimService;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private BenefitPolicyRepository policyRepository;
    @Autowired private BenefitPolicyRuleRepository ruleRepository;
    @Autowired private BenefitGroupRepository groupRepository;
    @Autowired private BenefitLimitBucketRepository bucketRepository;
    @Autowired private BenefitRuleBucketRepository ruleBucketRepository;
    @Autowired private BenefitBucketConsumptionRepository consumptionRepository;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository userRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ProviderContractRepository contractRepository;
    @Autowired private ProviderContractTermRepository contractTermRepository;
    @Autowired private ProviderContractPricingItemRepository pricingRepository;
    @Autowired private MedicalServiceRepository serviceRepository;
    @Autowired private MedicalCategoryRepository categoryRepository;
    @Autowired private VisitRepository visitRepository;
    @Autowired private ProviderAccountRepository providerAccountRepository;

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void inpatientClaimCanHoldMixedCategoryLinesAndAppliesLimitsWithoutCrossLineReuse() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Fixture f = fixture(suffix, new BigDecimal("800.00"));

        MedicalCategory diagnostics = category("CAT-DIAG-" + suffix, "تحاليل وأشعة");
        MedicalCategory imaging = category("CAT-IMG-" + suffix, "رنين ومقطعية");
        MedicalCategory outpatientOnly = category("CAT-OPD-" + suffix, "خدمة مصنفة عيادات خارجية");
        MedicalCategory inpatientGeneral = category("CAT-COV-INPATIENT", "إيواء عام " + suffix);

        rule(f.policy(), diagnostics, EncounterType.INPATIENT, 75, bucket(f.policy(), "DIAG-" + suffix,
                EncounterType.INPATIENT, new BigDecimal("3000.00"), null));
        rule(f.policy(), imaging, EncounterType.INPATIENT, 75, bucket(f.policy(), "IMG-" + suffix,
                EncounterType.INPATIENT, new BigDecimal("500.00"), null));
        rule(f.policy(), outpatientOnly, EncounterType.OUTPATIENT, 75, bucket(f.policy(), "OPD-" + suffix,
                EncounterType.OUTPATIENT, new BigDecimal("1000.00"), null));
        rule(f.policy(), inpatientGeneral, EncounterType.INPATIENT, 100, bucket(f.policy(), "INP-" + suffix,
                EncounterType.INPATIENT, new BigDecimal("1000.00"), null));

        MedicalService lab = pricedService(f.contract(), diagnostics, "LAB-" + suffix, "فاتورة تحاليل طبية", "200.00");
        MedicalService ct = pricedService(f.contract(), imaging, "CT-" + suffix, "CT-001 صورة مقطعية", "800.00");
        MedicalService sugar = manualService(outpatientOnly, "SUGAR-" + suffix, "تحليل سكر مصنف عيادات خارجية");

        ClaimViewDto claim = submit(f, EncounterType.INPATIENT, List.of(
                ClaimLineDto.builder().medicalServiceId(lab.getId()).quantity(1).build(),
                ClaimLineDto.builder().medicalServiceId(ct.getId()).quantity(1).build(),
                ClaimLineDto.builder().medicalServiceId(sugar.getId()).manualAmount(new BigDecimal("100.00")).quantity(1).build()));

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getRequestedAmount()).isEqualByComparingTo("1100.00");
        assertThat(claim.getApprovedAmount()).isEqualByComparingTo("625.00");
        assertThat(claim.getPatientCoPay()).isEqualByComparingTo("175.00");
        assertThat(claim.getRefusedAmount()).isEqualByComparingTo("300.00");

        assertThat(claim.getLines()).hasSize(3);
        assertThat(claim.getLines().get(0).getCompanyShare()).isEqualByComparingTo("150.00");
        assertThat(claim.getLines().get(0).getPatientShare()).isEqualByComparingTo("50.00");

        assertThat(claim.getLines().get(1).getApprovedAmount()).isEqualByComparingTo("375.00");
        assertThat(claim.getLines().get(1).getCompanyShare()).isEqualByComparingTo("375.00");
        assertThat(claim.getLines().get(1).getPatientShare()).isEqualByComparingTo("125.00");
        assertThat(claim.getLines().get(1).getRefusedAmount()).isEqualByComparingTo("300.00");

        assertThat(claim.getLines().get(2).getCompanyShare()).isEqualByComparingTo("100.00");
        assertThat(claim.getLines().get(2).getPatientShare()).isZero();
        assertThat(claim.getLines().get(2).getServiceCategoryId()).isEqualTo(outpatientOnly.getId());

        var committed = consumptionRepository.findByClaimIdAndStatus(
                claim.getId(), com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption.Status.COMMITTED);
        assertThat(committed).filteredOn(c -> c.getLimitScope()
                == com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption.LimitScope.POLICY_GENERAL)
                .extracting(com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption::getApprovedAmount)
                .containsExactlyInAnyOrder(new BigDecimal("200.00"), new BigDecimal("500.00"), new BigDecimal("100.00"));
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void oneMultiLineClaimCannotOverdrawTheGeneralAnnualCeiling() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Fixture f = fixture(suffix, new BigDecimal("700.00"));
        MedicalCategory diagnostics = category("CAT-GEN-DIAG-" + suffix, "تحاليل عامة");
        MedicalCategory imaging = category("CAT-GEN-IMG-" + suffix, "أشعة عامة");

        rule(f.policy(), diagnostics, EncounterType.OUTPATIENT, 100, bucket(f.policy(), "GEN-DIAG-" + suffix,
                EncounterType.OUTPATIENT, new BigDecimal("1000.00"), null));
        rule(f.policy(), imaging, EncounterType.OUTPATIENT, 100, bucket(f.policy(), "GEN-IMG-" + suffix,
                EncounterType.OUTPATIENT, new BigDecimal("1000.00"), null));

        MedicalService first = pricedService(f.contract(), diagnostics, "A-" + suffix, "بند أول", "400.00");
        MedicalService second = pricedService(f.contract(), imaging, "B-" + suffix, "بند ثان", "400.00");

        ClaimViewDto claim = submit(f, EncounterType.OUTPATIENT, List.of(
                ClaimLineDto.builder().medicalServiceId(first.getId()).quantity(1).build(),
                ClaimLineDto.builder().medicalServiceId(second.getId()).quantity(1).build()));

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getRequestedAmount()).isEqualByComparingTo("800.00");
        assertThat(claim.getApprovedAmount()).isEqualByComparingTo("700.00");
        assertThat(claim.getPatientCoPay()).isZero();
        assertThat(claim.getRefusedAmount()).isEqualByComparingTo("100.00");
        assertThat(claim.getLines().get(0).getCompanyShare()).isEqualByComparingTo("400.00");
        assertThat(claim.getLines().get(1).getApprovedAmount()).isEqualByComparingTo("300.00");
        assertThat(claim.getLines().get(1).getRefusedAmount()).isEqualByComparingTo("100.00");
    }

    private record Fixture(Member member, Provider provider, ProviderContract contract, BenefitPolicy policy) {}

    private Fixture fixture(String suffix, BigDecimal annualLimit) {
        userRepository.findByUsername("admin").orElseGet(() -> userRepository.save(
                com.waad.tba.modules.rbac.entity.User.builder()
                        .username("admin").password("password").fullName("System Admin")
                        .email("admin@waad.ly").userType("SUPER_ADMIN").active(true).build()));

        Employer employer = employerRepository.save(Employer.builder()
                .name("Multi Claim Co " + suffix).code("EMP-MULTI-" + suffix).active(true).build());
        BenefitPolicy policy = policyRepository.save(BenefitPolicy.builder()
                .name("Multi Policy " + suffix).policyCode("POL-MULTI-" + suffix).employer(employer)
                .annualLimit(annualLimit).defaultCoveragePercent(100)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());
        Member member = memberRepository.save(Member.builder()
                .fullName("Multi Member " + suffix).barcode("BC-MULTI-" + suffix).nationalNumber("NAT-MULTI-" + suffix)
                .employer(employer).benefitPolicy(policy).active(true).build());
        initializeTemporalAssignments(member);

        Provider provider = providerRepository.save(Provider.builder()
                .name("Multi Hospital " + suffix).providerType(ProviderType.HOSPITAL)
                .licenseNumber("LIC-MULTI-" + suffix).allowAllEmployers(true).active(true).build());
        providerAccountRepository.save(ProviderAccount.builder()
                .providerId(provider.getId()).runningBalance(BigDecimal.ZERO)
                .totalApproved(BigDecimal.ZERO).totalPaid(BigDecimal.ZERO).build());

        ProviderContract contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CON-MULTI-" + suffix).contractNumber("CNT-MULTI-" + suffix).provider(provider)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                .status(ContractStatus.ACTIVE).discountPercent(BigDecimal.ZERO)
                .discountBeforeRejection(false).active(true).build());
        contractTermRepository.save(ProviderContractTerm.builder()
                .contract(contract).effectiveFrom(contract.getStartDate())
                .discountPercent(BigDecimal.ZERO).discountBeforeRejection(false)
                .changeReason("Multi-line test terms").build());
        return new Fixture(member, provider, contract, policy);
    }

    private MedicalCategory category(String code, String name) {
        return categoryRepository.findActiveByCode(code)
                .orElseGet(() -> categoryRepository.save(MedicalCategory.builder()
                        .code(code).name(name).active(true).build()));
    }

    private BenefitPolicyRule rule(BenefitPolicy policy, MedicalCategory category, EncounterType context,
            int coveragePercent, BenefitLimitBucket bucket) {
        BenefitPolicyRule rule = ruleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy).medicalCategory(category)
                .encounterType(context).claimContextCode(context.name())
                .coveragePercent(coveragePercent).active(true).deleted(false).build());
        ruleBucketRepository.save(BenefitRuleBucket.builder().rule(rule).bucket(bucket).build());
        return rule;
    }

    private BenefitLimitBucket bucket(BenefitPolicy policy, String code, EncounterType context,
            BigDecimal amountLimit, Integer timesLimit) {
        BenefitGroup group = groupRepository.save(BenefitGroup.builder()
                .policy(policy).code("GRP-" + code).nameAr("مجموعة " + code)
                .contextType(context).aggregationMode(AggregationMode.SHARED).active(true).build());
        return bucketRepository.save(BenefitLimitBucket.builder()
                .policy(policy).benefitGroup(group).code("BUC-" + code).nameAr("سقف " + code)
                .contextType(context).amountLimit(amountLimit).timesLimit(timesLimit)
                .periodType(LimitPeriodType.ANNUAL).countingMethod(CountingMethod.EACH_LINE)
                .consumptionBasis(ConsumptionBasis.ELIGIBLE_AMOUNT)
                .benefitScopeType(BenefitScopeType.GROUP)
                .shared(false).active(true).build());
    }

    private MedicalService pricedService(ProviderContract contract, MedicalCategory category,
            String code, String name, String amount) {
        MedicalService service = serviceRepository.save(MedicalService.builder()
                .code(code).name(name).categoryId(category.getId()).cost(new BigDecimal(amount))
                .pricingMode(PricingMode.CONTRACT_PRICE).active(true).build());
        pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract).serviceCode(service.getCode()).serviceName(service.getName())
                .medicalCategory(category).basePrice(new BigDecimal(amount))
                .contractPrice(new BigDecimal(amount)).active(true).build());
        return service;
    }

    private MedicalService manualService(MedicalCategory category, String code, String name) {
        return serviceRepository.save(MedicalService.builder()
                .code(code).name(name).categoryId(category.getId()).cost(BigDecimal.ZERO)
                .pricingMode(PricingMode.MANUAL_AMOUNT).defaultClaimContextCode(EncounterType.OUTPATIENT.name())
                .active(true).build());
    }

    private ClaimViewDto submit(Fixture f, EncounterType context, List<ClaimLineDto> lines) {
        Visit visit = visitRepository.save(Visit.builder()
                .member(f.member()).providerId(f.provider().getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());
        return claimService.createClaim(ClaimCreateDto.builder()
                .visitId(visit.getId()).serviceDate(LocalDate.now())
                .encounterType(context).claimContextCode(context.name())
                .lines(lines).build());
    }
}
