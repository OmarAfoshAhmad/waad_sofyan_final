package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.repository.ClaimRepository;
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
 * A pharmacy/optics-style invoice has no fixed contract price list -- the
 * clerk enters the invoice total directly. This is the standard-service
 * "manual amount" path added alongside CONTRACT_PRICE: MedicalService.pricingMode
 * = MANUAL_AMOUNT skips the contract pricing lookup entirely in ClaimMapper,
 * same branch that already carries the legacy GEN-MEDICATION/GEN-MEDICAL-SERVICE
 * free-text codes.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@Transactional
class ClaimManualAmountLineIntegrationTest extends PostgresIntegrationTestBase {

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
        private MedicalServiceRepository medicalServiceRepository;
        @Autowired
        private MedicalCategoryRepository medicalCategoryRepository;
        @Autowired
        private ProviderServiceRepository providerServiceRepository;
        @Autowired
        private VisitRepository visitRepository;
        @Autowired
        private ClaimRepository claimRepository;

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

                BenefitPolicy policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                                .name("Standard Plan " + suffix).policyCode("POL-" + suffix).employer(employer)
                                .annualLimit(new BigDecimal("50000")).defaultCoveragePercent(80)
                                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

                Member member = memberRepository.save(Member.builder()
                                .fullName("Jane Doe").barcode("BC-" + suffix).nationalNumber("NAT-" + suffix)
                                .employer(employer).benefitPolicy(policy).active(true).build());
                initializeTemporalAssignments(member);

                provider = providerRepository.save(Provider.builder()
                                .name("City Pharmacy " + suffix).providerType(ProviderType.PHARMACY)
                                .licenseNumber("LIC-" + suffix).allowAllEmployers(true).active(true).build());

                MedicalCategory category = medicalCategoryRepository.save(MedicalCategory.builder()
                                .code("CAT-" + suffix).name("Drugs " + suffix).active(true).build());

                benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                                .benefitPolicy(policy).medicalCategory(category)
                                .encounterType(EncounterType.OUTPATIENT)
                                .coveragePercent(80).active(true).deleted(false).build());

                manualService = medicalServiceRepository.save(MedicalService.builder()
                                .code("SYS-TEST-DRUG-" + suffix.toUpperCase()).name("Test Drug Invoice")
                                .categoryId(category.getId()).pricingMode(PricingMode.MANUAL_AMOUNT)
                                .active(true).build());

                ProviderContract contract = contractRepository.save(ProviderContract.builder()
                                .contractCode("CON-" + suffix).contractNumber("CNT-" + suffix).provider(provider)
                                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                                .status(ContractStatus.ACTIVE).active(true).build());
                termsService.ensureEffectiveTerms(contract, "TEST");

                visit = visitRepository.save(Visit.builder()
                                .member(member).providerId(provider.getId()).visitDate(LocalDate.now())
                                .status(VisitStatus.REGISTERED).build());
        }

        private void assignServiceToProvider() {
                providerServiceRepository.save(ProviderService.builder()
                                .providerId(provider.getId()).serviceCode(manualService.getCode())
                                .active(true).build());
        }

        private ClaimCreateDto.ClaimCreateDtoBuilder baseClaim(ClaimLineDto line) {
                return ClaimCreateDto.builder()
                                .visitId(visit.getId())
                                .serviceDate(LocalDate.now())
                                .encounterType(EncounterType.OUTPATIENT)
                                .lines(List.of(line))
                                .status(ClaimStatus.SUBMITTED);
        }

        @Test
        @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
        void entersTheInvoiceAmountDirectlyWithNoContractPriceLookup() {
                assignServiceToProvider();

                ClaimCreateDto dto = baseClaim(ClaimLineDto.builder()
                                .medicalServiceId(manualService.getId())
                                .manualAmount(new BigDecimal("850.00"))
                                .quantity(5) // client-sent quantity must be ignored -- forced to 1
                                .build()).build();

                ClaimViewDto created = claimService.createClaim(dto);

                assertThat(created.getRequestedAmount()).isEqualByComparingTo("850.00");
                ClaimLine savedLine = claimRepository.findById(created.getId()).orElseThrow()
                                .getLines().get(0);
                assertThat(savedLine.getQuantity()).isEqualTo(1);
                assertThat(savedLine.getUnitPrice()).isEqualByComparingTo("850.00");
                assertThat(savedLine.getAmountSource()).isEqualTo("MANUAL_AMOUNT");
                assertThat(savedLine.getPricingItemId()).isNull();
        }

        @Test
        @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
        void rejectsAPricingItemIdOnAManualAmountService() {
                assignServiceToProvider();

                ClaimCreateDto dto = baseClaim(ClaimLineDto.builder()
                                .medicalServiceId(manualService.getId())
                                .pricingItemId(999L)
                                .manualAmount(new BigDecimal("100.00"))
                                .build()).build();

                assertThatThrownBy(() -> claimService.createClaim(dto))
                                .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
        void rejectsAZeroOrMissingManualAmount() {
                assignServiceToProvider();

                ClaimCreateDto dto = baseClaim(ClaimLineDto.builder()
                                .medicalServiceId(manualService.getId())
                                .build()).build();

                assertThatThrownBy(() -> claimService.createClaim(dto))
                                .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
        void rejectsAManualServiceNotAssignedToTheClaimingProvider() {
                // deliberately not calling assignServiceToProvider()

                ClaimCreateDto dto = baseClaim(ClaimLineDto.builder()
                                .medicalServiceId(manualService.getId())
                                .manualAmount(new BigDecimal("100.00"))
                                .build()).build();

                assertThatThrownBy(() -> claimService.createClaim(dto))
                                .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
        void rejectsAManualAmountSentForAContractPricedService() {
                MedicalCategory category = medicalCategoryRepository.findAll().stream()
                                .filter(c -> c.getId().equals(manualService.getCategoryId())).findFirst().orElseThrow();
                MedicalService contractPricedService = medicalServiceRepository.save(MedicalService.builder()
                                .code("SRV-CONTRACT-" + UUID.randomUUID().toString().substring(0, 6))
                                .name("Contract priced service").categoryId(category.getId())
                                .pricingMode(PricingMode.CONTRACT_PRICE).active(true).build());

                ClaimCreateDto dto = baseClaim(ClaimLineDto.builder()
                                .medicalServiceId(contractPricedService.getId())
                                .manualAmount(new BigDecimal("100.00"))
                                .build()).build();

                assertThatThrownBy(() -> claimService.createClaim(dto))
                                .isInstanceOf(BusinessRuleException.class);
        }
}
