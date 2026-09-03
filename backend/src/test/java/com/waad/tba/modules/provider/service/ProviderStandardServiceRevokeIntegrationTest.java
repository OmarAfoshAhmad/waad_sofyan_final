package com.waad.tba.modules.provider.service;

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
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.service.ClaimService;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.enums.PricingMode;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.provider.dto.ProvisionStandardServicesRequestDto;
import com.waad.tba.modules.provider.dto.ProvisionStandardServicesRequestDto.Scope;
import com.waad.tba.modules.provider.dto.RevokeStandardServicesSummaryDto;
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
 * The claim-history check behind bulk revoke (ClaimRepository
 * .findProviderServiceCodePairsWithClaimHistory) is a JPQL join + DISTINCT
 * into an interface projection -- exactly the kind of query that compiles
 * fine but can silently return nothing, or throw, against a real Hibernate
 * session. This exercises it against real Postgres, through the same
 * revoke() path the controller calls.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@Transactional
class ProviderStandardServiceRevokeIntegrationTest extends PostgresIntegrationTestBase {

        @Autowired
        private ProviderStandardServiceProvisioner provisioner;
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
        private ProviderServiceRepository providerServiceRepository;
        @Autowired
        private ProviderContractRepository contractRepository;
        @Autowired
        private ProviderContractTermsService termsService;
        @Autowired
        private MedicalServiceRepository medicalServiceRepository;
        @Autowired
        private MedicalCategoryRepository medicalCategoryRepository;
        @Autowired
        private VisitRepository visitRepository;

        private Provider provider;
        private MedicalService manualService;

        @BeforeEach
        void setupData() {
                String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                userRepository.findByUsername("admin").orElseGet(() -> userRepository.save(
                                com.waad.tba.modules.rbac.entity.User.builder()
                                                .username("admin").password("password").fullName("System Admin")
                                                .email("admin@waad.ly").userType("SUPER_ADMIN").active(true).build()));

                Employer employer = employerRepository.save(Employer.builder()
                                .name("Test Company " + suffix).code("EMP-" + suffix).active(true).build());
                BenefitPolicy policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                                .name("Standard Plan " + suffix).policyCode("POL-" + suffix).employer(employer)
                                .annualLimit(new BigDecimal("50000")).defaultCoveragePercent(80)
                                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                                .status(BenefitPolicyStatus.ACTIVE).active(true).build());
                Member member = memberRepository.save(Member.builder()
                                .fullName("Jane Revoke").barcode("BC-" + suffix).nationalNumber("NAT-" + suffix)
                                .employer(employer).benefitPolicy(policy).active(true).build());
                initializeTemporalAssignments(member);

                provider = providerRepository.save(Provider.builder()
                                .name("Pharmacy Revoke " + suffix).providerType(ProviderType.PHARMACY)
                                .licenseNumber("LIC-" + suffix).allowAllEmployers(true).active(true).build());

                MedicalCategory category = medicalCategoryRepository.save(MedicalCategory.builder()
                                .code("CAT-" + suffix).name("Drugs " + suffix).active(true).build());
                benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                                .benefitPolicy(policy).medicalCategory(category)
                                .encounterType(EncounterType.OUTPATIENT)
                                .coveragePercent(80).active(true).deleted(false).build());

                manualService = medicalServiceRepository.save(MedicalService.builder()
                                .code("SYS-REVOKE-" + suffix).name("Test Revoke Service")
                                .categoryId(category.getId()).pricingMode(PricingMode.MANUAL_AMOUNT)
                                .active(true).build());

                providerServiceRepository.save(ProviderService.builder()
                                .providerId(provider.getId()).serviceCode(manualService.getCode()).active(true).build());

                ProviderContract contract = contractRepository.save(ProviderContract.builder()
                                .contractCode("CON-" + suffix).contractNumber("CNT-" + suffix).provider(provider)
                                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                                .status(ContractStatus.ACTIVE).active(true).build());
                termsService.ensureEffectiveTerms(contract, "TEST");

                Visit visit = visitRepository.save(Visit.builder()
                                .member(member).providerId(provider.getId()).visitDate(LocalDate.now())
                                .status(VisitStatus.REGISTERED).build());
                this.visit = visit;
        }

        private Visit visit;

        private ProvisionStandardServicesRequestDto selectedProviderRequest() {
                return ProvisionStandardServicesRequestDto.builder()
                                .serviceCodes(List.of(manualService.getCode()))
                                .scope(Scope.SELECTED_PROVIDERS)
                                .providerIds(List.of(provider.getId()))
                                .build();
        }

        @Test
        @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
        void revokesFreelyWhenNoClaimWasEverEnteredForThatServiceAndProvider() {
                RevokeStandardServicesSummaryDto summary = provisioner.revoke(selectedProviderRequest());

                assertThat(summary.getAssignmentsToRevoke()).isEqualTo(1);
                assertThat(summary.getAssignmentsBlockedByClaimHistory()).isZero();
                assertThat(providerServiceRepository
                                .findByProviderIdAndServiceCode(provider.getId(), manualService.getCode())
                                .orElseThrow().getActive()).isFalse();
        }

        @Test
        @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
        void refusesToRevokeOnceAClaimLineExistsForThatExactProviderAndService() {
                ClaimCreateDto dto = ClaimCreateDto.builder()
                                .visitId(visit.getId())
                                .serviceDate(LocalDate.now())
                                .encounterType(EncounterType.OUTPATIENT)
                                .lines(List.of(ClaimLineDto.builder()
                                                .medicalServiceId(manualService.getId())
                                                .manualAmount(new BigDecimal("50.00"))
                                                .build()))
                                .status(ClaimStatus.SUBMITTED)
                                .build();
                claimService.createClaim(dto);

                RevokeStandardServicesSummaryDto summary = provisioner.revoke(selectedProviderRequest());

                assertThat(summary.getAssignmentsToRevoke()).isZero();
                assertThat(summary.getAssignmentsBlockedByClaimHistory()).isEqualTo(1);
                assertThat(summary.getBlockedAssignments().get(0).getProviderId()).isEqualTo(provider.getId());
                assertThat(summary.getBlockedAssignments().get(0).getServiceCode()).isEqualTo(manualService.getCode());
                assertThat(providerServiceRepository
                                .findByProviderIdAndServiceCode(provider.getId(), manualService.getCode())
                                .orElseThrow().getActive())
                                .as("must not have been touched")
                                .isTrue();
        }
}
