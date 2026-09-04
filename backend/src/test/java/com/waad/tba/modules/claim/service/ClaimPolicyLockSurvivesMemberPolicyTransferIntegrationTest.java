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
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyService;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.enums.PricingMode;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.PolicyAssignmentSource;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.service.MemberPolicyResolver;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.entity.ProviderService;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.provider.repository.ProviderServiceRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.providercontract.service.ProviderContractTermsService;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * V217/step 9: a claim's historical policy attribution
 * ({@code Claim.policyId}, read by {@link ClaimRepository#countByPolicyId})
 * must survive the member being transferred to a different policy after the
 * claim exists. Before V217, {@code countByPolicyId} read
 * {@code member.benefitPolicy.id} -- the member's CURRENT pointer -- so a
 * transfer would silently move an already-adjudicated claim's vote from the
 * policy it actually happened under to whichever policy the member is on
 * today, letting the original policy's rules look editable again
 * ({@link BenefitPolicyService#canPolicyBeEdited}) even though a real claim
 * still depends on them.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@Transactional
class ClaimPolicyLockSurvivesMemberPolicyTransferIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private ClaimService claimService;
    @Autowired private ClaimRepository claimRepository;
    @Autowired private BenefitPolicyService benefitPolicyService;
    @Autowired private BenefitPolicyRepository benefitPolicyRepository;
    @Autowired private BenefitPolicyRuleRepository benefitPolicyRuleRepository;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository userRepository;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private MemberPolicyResolver memberPolicyResolver;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ProviderContractRepository contractRepository;
    @Autowired private ProviderContractTermsService termsService;
    @Autowired private MedicalServiceRepository medicalServiceRepository;
    @Autowired private MedicalCategoryRepository medicalCategoryRepository;
    @Autowired private ProviderServiceRepository providerServiceRepository;
    @Autowired private VisitRepository visitRepository;

    private BenefitPolicy policyA;
    private BenefitPolicy policyB;
    private Member member;
    private Provider provider;
    private MedicalService manualService;
    private Visit visit;

    @BeforeEach
    void setupData() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        userRepository.findByUsername("admin").orElseGet(() -> userRepository.save(
                com.waad.tba.modules.rbac.entity.User.builder()
                        .username("admin").password("password").fullName("System Admin")
                        .email("admin@waad.ly").userType("SUPER_ADMIN").active(true).build()));

        Employer employer = employerRepository.save(Employer.builder()
                .name("Test Company " + suffix).code("EMP-" + suffix).active(true).build());

        policyA = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Policy A " + suffix).policyCode("POLA-" + suffix).employer(employer)
                .annualLimit(new BigDecimal("50000")).defaultCoveragePercent(80)
                .startDate(LocalDate.now().minusMonths(2)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());
        policyB = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Policy B " + suffix).policyCode("POLB-" + suffix).employer(employer)
                .annualLimit(new BigDecimal("50000")).defaultCoveragePercent(80)
                .startDate(LocalDate.now().minusMonths(2)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

        member = memberRepository.save(Member.builder()
                .fullName("Transferred Member").barcode("BC-" + suffix).nationalNumber("NAT-" + suffix)
                .employer(employer).benefitPolicy(policyA).active(true).build());
        initializeTemporalAssignments(member);

        provider = providerRepository.save(Provider.builder()
                .name("City Pharmacy " + suffix).providerType(ProviderType.PHARMACY)
                .licenseNumber("LIC-" + suffix).allowAllEmployers(true).active(true).build());

        MedicalCategory category = medicalCategoryRepository.save(MedicalCategory.builder()
                .code("CAT-" + suffix).name("Drugs " + suffix).active(true).build());

        for (BenefitPolicy policy : List.of(policyA, policyB)) {
            benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                    .benefitPolicy(policy).medicalCategory(category)
                    .encounterType(EncounterType.OUTPATIENT)
                    .coveragePercent(80).active(true).deleted(false).build());
        }

        manualService = medicalServiceRepository.save(MedicalService.builder()
                .code("SYS-TEST-DRUG-" + suffix.toUpperCase()).name("Test Drug Invoice")
                .categoryId(category.getId()).pricingMode(PricingMode.MANUAL_AMOUNT)
                .active(true).build());

        ProviderContract contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CON-" + suffix).contractNumber("CNT-" + suffix).provider(provider)
                .startDate(LocalDate.now().minusMonths(2)).endDate(LocalDate.now().plusMonths(11))
                .status(ContractStatus.ACTIVE).active(true).build());
        termsService.ensureEffectiveTerms(contract, "TEST");

        providerServiceRepository.save(ProviderService.builder()
                .providerId(provider.getId()).serviceCode(manualService.getCode())
                .active(true).build());

        visit = visitRepository.save(Visit.builder()
                .member(member).providerId(provider.getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void transferringTheMemberAfterTheClaimDoesNotMoveItsPolicyLockVote() {
        ClaimCreateDto dto = ClaimCreateDto.builder()
                .visitId(visit.getId())
                .serviceDate(LocalDate.now())
                .encounterType(EncounterType.OUTPATIENT)
                .lines(List.of(ClaimLineDto.builder()
                        .medicalServiceId(manualService.getId())
                        .manualAmount(new BigDecimal("120.00"))
                        .quantity(1)
                        .build()))
                .status(ClaimStatus.SUBMITTED)
                .build();

        ClaimViewDto created = claimService.createClaim(dto);
        assertThat(claimRepository.findById(created.getId()).orElseThrow().getPolicyId())
                .as("the claim's historical snapshot names the policy in force when it was created")
                .isEqualTo(policyA.getId());

        assertThat(claimRepository.countByPolicyId(policyA.getId())).isEqualTo(1L);
        assertThat(claimRepository.countByPolicyId(policyB.getId())).isZero();
        assertThat(benefitPolicyService.canPolicyBeEdited(policyA.getId()))
                .as("policy A is locked -- a real claim depends on its rules").isFalse();
        assertThat(benefitPolicyService.canPolicyBeEdited(policyB.getId()))
                .as("policy B has never been used").isTrue();

        // The transfer: the member moves to policy B effective today, exactly
        // the scenario that used to make the old claim invisible to policy
        // A's lock query.
        memberPolicyResolver.assignPolicy(member, policyB, LocalDate.now(),
                "integration test transfer", PolicyAssignmentSource.MANUAL, 1L);

        assertThat(claimRepository.findById(created.getId()).orElseThrow().getPolicyId())
                .as("the already-created claim's historical snapshot is untouched by the transfer")
                .isEqualTo(policyA.getId());
        assertThat(claimRepository.countByPolicyId(policyA.getId()))
                .as("policy A must still show the claim that happened under it")
                .isEqualTo(1L);
        assertThat(claimRepository.countByPolicyId(policyB.getId()))
                .as("policy B must not inherit a claim that never happened under it")
                .isZero();
        assertThat(benefitPolicyService.canPolicyBeEdited(policyA.getId()))
                .as("policy A must stay locked after the transfer").isFalse();
        assertThat(benefitPolicyService.canPolicyBeEdited(policyB.getId()))
                .as("policy B must not become falsely locked by a claim that isn't its own").isTrue();
    }
}
