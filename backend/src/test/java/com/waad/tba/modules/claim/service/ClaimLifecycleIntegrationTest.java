package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.claim.dto.ClaimApproveDto;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimSettleDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.entity.VisitStatus;
import com.waad.tba.modules.visit.repository.VisitRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ClaimLifecycleIntegrationTest {

        @Autowired
        private ClaimService claimService;

        @Autowired
        private ClaimReviewService claimReviewService;

        @Autowired
        private EmployerRepository employerRepository;

        @Autowired
        private BenefitPolicyRepository benefitPolicyRepository;

        @Autowired
        private com.waad.tba.modules.rbac.repository.UserRepository userRepository;

        @Autowired
        private MemberRepository memberRepository;

        @Autowired
        private ProviderRepository providerRepository;

        @Autowired
        private ProviderContractRepository contractRepository;

        @Autowired
        private ProviderContractPricingItemRepository pricingRepository;

        @Autowired
        private MedicalServiceRepository medicalServiceRepository;

        @Autowired
        private MedicalCategoryRepository medicalCategoryRepository;

        @Autowired
        private VisitRepository visitRepository;

        private Employer employer;
        private BenefitPolicy policy;
        private Member member;
        private Provider provider;
        private ProviderContract contract;
        private MedicalService service;
        private Visit visit;

        @BeforeEach
        void setupData() {
                // 0. User for auditing
                userRepository.save(com.waad.tba.modules.rbac.entity.User.builder()
                                .username("admin")
                                .password("password")
                                .fullName("System Admin")
                                .email("admin@waad.ly")
                                .userType("SUPER_ADMIN")
                                .active(true)
                                .build());

                // 1. Employer
                employer = employerRepository.save(Employer.builder()
                                .name("Test Company")
                                .code("EMP-TEST")
                                .active(true)
                                .build());

                // 2. Benefit Policy
                policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                                .name("Standard Plan")
                                .policyCode("POL-TEST")
                                .employer(employer)
                                .annualLimit(new BigDecimal("50000"))
                                .defaultCoveragePercent(80)
                                .startDate(LocalDate.now().minusMonths(1))
                                .endDate(LocalDate.now().plusYears(1))
                                .status(BenefitPolicyStatus.ACTIVE) // Assuming PolicyStatus is BenefitPolicyStatus
                                .active(true)
                                .build());

                // 3. Member
                member = memberRepository.save(Member.builder()
                                .fullName("John Doe")
                                .barcode("1234567890")
                                .nationalNumber("TEST-123")
                                .employer(employer)
                                .benefitPolicy(policy)
                                .active(true)
                                .build());

                // 4. Provider
                provider = providerRepository.save(Provider.builder()
                                .name("General Hospital")
                                .providerType(ProviderType.HOSPITAL)
                                .licenseNumber("LIC-TEST-456")
                                .allowAllEmployers(true)
                                .active(true)
                                .build());

                // 5. Medical Category
                var category = medicalCategoryRepository.save(MedicalCategory.builder()
                                .code("CAT-001")
                                .name("General Services")
                                .active(true)
                                .build());

                // 6. Medical Service
                service = medicalServiceRepository.save(MedicalService.builder()
                                .code("SRV-001")
                                .name("General Consultation")
                                .categoryId(category.getId())
                                .cost(new BigDecimal("150"))
                                .active(true)
                                .build());

                // 6. Contract
                contract = contractRepository.save(ProviderContract.builder()
                                .contractCode("CON-TEST")
                                .contractNumber("CNT-2026-001")
                                .provider(provider)
                                .startDate(LocalDate.now().minusMonths(1))
                                .endDate(LocalDate.now().plusMonths(11))
                                .status(ContractStatus.ACTIVE)
                                .active(true)
                                .build());

                // 7. Pricing Item
                pricingRepository.save(ProviderContractPricingItem.builder()
                                .contract(contract)
                                .serviceCode(service.getCode())
                                .serviceName(service.getName())
                                .basePrice(new BigDecimal("150"))
                                .contractPrice(new BigDecimal("120"))
                                .active(true)
                                .build());

                // 8. Visit
                visit = visitRepository.save(Visit.builder()
                                .member(member)
                                .providerId(provider.getId())
                                .visitDate(LocalDate.now())
                                .status(VisitStatus.REGISTERED)
                                .build());
        }

        @Test
        @WithMockUser(username = "admin", roles = { "ADMIN", "REVIEWER" })
        void fullClaimLifecycle_shouldSucceed() {
                // Step 1: Create Claim from Visit
                ClaimCreateDto createDto = ClaimCreateDto.builder()
                                .visitId(visit.getId())
                                .serviceDate(LocalDate.now())
                                .lines(List.of(ClaimLineDto.builder()
                                                .medicalServiceId(service.getId())
                                                .quantity(1)
                                                .build()))
                                .status(ClaimStatus.SUBMITTED)
                                .build();

                ClaimViewDto createdClaim = claimService.createClaim(createDto);
                assertThat(createdClaim).isNotNull();
                assertThat(createdClaim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
                assertThat(createdClaim.getRequestedAmount()).isEqualByComparingTo("120.00");

                // Step 2: Start Review
                ClaimViewDto underReview = claimReviewService.startReview(createdClaim.getId());
                assertThat(underReview.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);

                // Step 3: Request Approval (Phase 1)
                // Since we are in an integration test without a running async executor,
                // we'll wait or call the logic manually if needed.
                // But for Lifecycle verification, let's assume we can settle once Approved.

                ClaimApproveDto approveDto = ClaimApproveDto.builder()
                                .useSystemCalculation(true)
                                .notes("Looks good")
                                .build();

                claimReviewService.requestApproval(createdClaim.getId(), approveDto);

                // Note: In real environment, it goes to APPROVAL_IN_PROGRESS then APPROVED.
                // In local test context without async task executor enabled in @SpringBootTest
                // (usually it is),
                // we might need to check if it transition to APPROVED.

                // Step 4: Settle (Simulated after async completion or manual Status update for
                // test)
                // To make the test stable, we manually call the async logic synchronously if
                // possible,
                // OR we just verify the state transition was initiated.

                // For this lifecycle test, we want to see it reach SETTLED.
                // We'll call the internal async processing logic directly for the test
                // stability.
                claimReviewService.processApprovalAsync(createdClaim.getId(), approveDto);

                ClaimViewDto approvedClaim = claimService.getClaim(createdClaim.getId());
                assertThat(approvedClaim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
                assertThat(approvedClaim.getApprovedAmount()).isGreaterThan(BigDecimal.ZERO);

                // Step 5: Settle Payment
                ClaimSettleDto settleDto = ClaimSettleDto.builder()
                                .paymentReference("PAY-001")
                                .notes("Settled via Test")
                                .build();

                ClaimViewDto settledClaim = claimReviewService.settleClaim(createdClaim.getId(), settleDto);
                assertThat(settledClaim.getStatus()).isEqualTo(ClaimStatus.SETTLED);
                assertThat(settledClaim.getPaymentReference()).isEqualTo("PAY-001");
        }
}
