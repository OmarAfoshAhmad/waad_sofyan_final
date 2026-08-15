package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.dto.FinancialSummaryDto;
import com.waad.tba.modules.claim.entity.ClaimStatus;
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
import com.waad.tba.modules.providercontract.repository.ProviderContractTermRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContractTerm;
import com.waad.tba.modules.settlement.entity.ProviderAccount;
import com.waad.tba.modules.settlement.repository.ProviderAccountRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Proves, end to end against real PostgreSQL, that GET /claims/financial-summary
 * reports the SAME per-claim contract discount (Claim.companyDiscountAmount) that
 * the settlement screen now reads directly -- not a client-invented percentage,
 * and not silently dropped from the aggregate the way totalCompanyDiscountAmount
 * was missing from FinancialSummaryDto before this fix.
 *
 * Coverage is 100% and there is no rejection, so the arithmetic is exact:
 * requestedAmount(200) -> companyDiscountAmount(20, at 10%) -> netProviderAmount(180).
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@Transactional
class ClaimFinancialSummaryCompanyDiscountIntegrationTest extends PostgresIntegrationTestBase {

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
    private ProviderContractTermRepository contractTermRepository;
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

    private String suffix;
    private Employer employer;

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
                .name("Discount Summary Test Co " + suffix)
                .code("EMP-" + suffix)
                .active(true)
                .build());

        BenefitPolicy policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Plan " + suffix)
                .policyCode("POL-" + suffix)
                .employer(employer)
                .annualLimit(new BigDecimal("100000.00"))
                .defaultCoveragePercent(100)
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE)
                .active(true)
                .build());

        Member member = memberRepository.save(Member.builder()
                .fullName("Member " + suffix)
                .barcode("BC-" + suffix)
                .nationalNumber("NAT-" + suffix)
                .employer(employer)
                .benefitPolicy(policy)
                .active(true)
                .build());
        initializeTemporalAssignments(member);

        Provider provider = providerRepository.save(Provider.builder()
                .name("Hospital " + suffix)
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

        MedicalCategory category = medicalCategoryRepository.save(MedicalCategory.builder()
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

        // 10% contract discount, applied after rejection (irrelevant here: no rejection).
        ProviderContract contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CON-" + suffix)
                .contractNumber("CNT-" + suffix)
                .provider(provider)
                .startDate(LocalDate.now().minusMonths(1))
                .endDate(LocalDate.now().plusMonths(11))
                .status(ContractStatus.ACTIVE)
                .discountPercent(new BigDecimal("10.00"))
                .discountBeforeRejection(false)
                .active(true)
                .build());
        contractTermRepository.save(ProviderContractTerm.builder()
                .contract(contract)
                .effectiveFrom(contract.getStartDate())
                .discountPercent(new BigDecimal("10.00"))
                .discountBeforeRejection(false)
                .changeReason("Test initial terms")
                .build());

        MedicalService service = medicalServiceRepository.save(MedicalService.builder()
                .code("SRV-" + suffix)
                .name("Service " + suffix)
                .categoryId(category.getId())
                .cost(new BigDecimal("200.00"))
                .active(true)
                .build());

        pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract)
                .serviceCode(service.getCode())
                .serviceName(service.getName())
                .medicalCategory(category)
                .basePrice(new BigDecimal("200.00"))
                .contractPrice(new BigDecimal("200.00"))
                .active(true)
                .build());

        Visit visit = visitRepository.save(Visit.builder()
                .member(member)
                .providerId(provider.getId())
                .visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED)
                .build());

        ClaimViewDto claim = claimService.createClaim(ClaimCreateDto.builder()
                .visitId(visit.getId())
                .serviceDate(LocalDate.now())
                .encounterType(EncounterType.OUTPATIENT)
                .lines(List.of(ClaimLineDto.builder()
                        .medicalServiceId(service.getId())
                        .quantity(1)
                        .build()))
                .build());
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getRequestedAmount()).isEqualByComparingTo("200.00");
        assertThat(claim.getCompanyDiscountAmount()).isEqualByComparingTo("20.00");
        assertThat(claim.getNetProviderAmount()).isEqualByComparingTo("180.00");
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void financialSummaryReportsThePersistedCompanyDiscountTotal() {
        FinancialSummaryDto summary = claimService.getFinancialSummary(employer.getId(), null, null, null, null);

        assertThat(summary.getTotalClaimsAmount()).isEqualByComparingTo("200.00");
        assertThat(summary.getTotalCompanyDiscountAmount()).isEqualByComparingTo("20.00");
        assertThat(summary.getTotalApprovedAmount()).isEqualByComparingTo("180.00");

        // Pre-existing characteristic, NOT something this fix changes: totalPaidAmount
        // is SUM(netProviderAmount), and Claim.netProviderAmount always equals
        // Claim.approvedAmount (see ClaimFinancialIdentityTest / ClaimFinancialSnapshotServiceTest)
        // -- so today totalPaidAmount and totalApprovedAmount are always identical. The
        // name "totalPaidAmount" suggests actual disbursement, which this is not; that
        // mislabeling is a separate, pre-existing issue outside this fix's scope and is
        // recorded here only so a future change to either field trips this assertion.
        assertThat(summary.getTotalPaidAmount()).isEqualByComparingTo(summary.getTotalApprovedAmount());

        // What the settlement screen derives as "إجمالي المستحق" (payable before the
        // company/facility split) = companyDiscount + facilityShare. With no patient
        // co-pay and no rejection in this scenario, that equals the gross claim amount.
        assertThat(summary.getTotalCompanyDiscountAmount().add(summary.getTotalPaidAmount()))
                .isEqualByComparingTo(summary.getTotalClaimsAmount());
    }
}
