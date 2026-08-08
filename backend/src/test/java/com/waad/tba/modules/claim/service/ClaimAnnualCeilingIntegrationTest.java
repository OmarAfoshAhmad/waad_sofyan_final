package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * finance-00: proves the general annual policy ceiling is enforced against
 * Claim.approvedAmount == Sigma ClaimLine.companyShare (the number
 * finance-00 step 4 guarantees is now correct), across MULTIPLE claims for
 * the same member -- not just within a single claim. Each test uses a fresh
 * member/policy pair so the two scenarios cannot interfere.
 *
 * Not @Transactional: BenefitBucketLedgerService's ceiling check
 * (BEFORE_COMMIT event) and the direct-entry approval path both need a real
 * commit boundary between claims to see each other's prior consumption.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class ClaimAnnualCeilingIntegrationTest extends PostgresIntegrationTestBase {

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

    private record Fixture(Member member, Provider provider, ProviderContract contract,
            MedicalCategory category, String suffix) {}

    private Fixture buildFixture(String suffix, BigDecimal annualLimit, int coveragePercent,
            BigDecimal discountPercent, boolean discountBeforeRejection) {
        userRepository.findByUsername("admin").orElseGet(() -> userRepository.save(
                com.waad.tba.modules.rbac.entity.User.builder()
                        .username("admin").password("password").fullName("System Admin")
                        .email("admin@waad.ly").userType("SUPER_ADMIN").active(true).build()));

        Employer employer = employerRepository.save(Employer.builder()
                .name("Ceiling Test Co " + suffix).code("EMP-" + suffix).active(true).build());

        BenefitPolicy policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Plan " + suffix).policyCode("POL-" + suffix).employer(employer)
                .annualLimit(annualLimit).defaultCoveragePercent(coveragePercent)
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
                .status(ContractStatus.ACTIVE).discountPercent(discountPercent)
                .discountBeforeRejection(discountBeforeRejection).active(true).build());
        contractTermRepository.save(ProviderContractTerm.builder()
                .contract(contract).effectiveFrom(contract.getStartDate())
                .discountPercent(discountPercent).discountBeforeRejection(discountBeforeRejection)
                .changeReason("Test initial terms").build());

        return new Fixture(member, provider, contract, category, suffix);
    }

    /**
     * Creates a dedicated service + pricing item priced at exactly grossAmount
     * and submits one claim for it. A distinct service per amount (rather than
     * one shared service with a per-line entered price) matches the pricing
     * resolution path every other finance-00 test already relies on:
     * ClaimMapper prefers the pricing item's contractPrice as the resolved
     * unit price once a matching pricingItemId/medicalServiceId is found, so
     * overriding ClaimLineDto.unitPrice alone does not reliably change the
     * effective total used by the coverage engine.
     */
    private ClaimViewDto submit(Fixture f, BigDecimal grossAmount) {
        String priceSuffix = UUID.randomUUID().toString().substring(0, 8);
        MedicalService service = medicalServiceRepository.save(MedicalService.builder()
                .code("SRV-" + f.suffix() + "-" + priceSuffix).name("Service " + priceSuffix)
                .categoryId(f.category().getId()).cost(grossAmount).active(true).build());
        pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(f.contract()).serviceCode(service.getCode()).serviceName(service.getName())
                .medicalCategory(f.category()).basePrice(grossAmount)
                .contractPrice(grossAmount).active(true).build());

        Visit visit = visitRepository.save(Visit.builder()
                .member(f.member()).providerId(f.provider().getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());
        return claimService.createClaim(ClaimCreateDto.builder()
                .visitId(visit.getId()).serviceDate(LocalDate.now()).encounterType(EncounterType.OUTPATIENT)
                .lines(List.of(ClaimLineDto.builder().medicalServiceId(service.getId()).quantity(1).build()))
                .build());
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void sequentialClaimsConsumeTheAnnualCeilingByCompanyShareAndAThirdOverTheLineIsRejected() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        // 100% coverage, no discount: companyShare == gross exactly, isolating
        // the ceiling arithmetic itself from any split logic.
        Fixture f = buildFixture(suffix, new BigDecimal("1000.00"), 100, BigDecimal.ZERO, false);

        ClaimViewDto first = submit(f, new BigDecimal("600.00"));
        assertThat(first.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(first.getApprovedAmount()).isEqualByComparingTo("600.00");

        ClaimViewDto second = submit(f, new BigDecimal("400.00"));
        assertThat(second.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(second.getApprovedAmount()).isEqualByComparingTo("400.00");

        // Cumulative is now exactly 1000.00 -- the ceiling. One more cent must be
        // refused. The system's real fail-closed behavior here is NOT a thrown
        // exception: BenefitBucketLimitService's synthetic annual-ceiling bucket
        // catches this during coverage calculation itself (it always tracks real
        // prior approvedAmount, so it correctly sees the 1000.00 already used),
        // refusing the line's entire amount and making the claim auto-REJECT --
        // the same "totalApproved > 0" gate that rejects any zero-payable claim.
        ClaimViewDto third = submit(f, new BigDecimal("0.01"));
        assertThat(third.getStatus()).isEqualTo(ClaimStatus.REJECTED);
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void annualCeilingIsConsumedByTheNetCompanyShareNotByTheGrossOrPreDiscountAmount() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        // gross=1000, coverage=80% -> patient=200, providerShare=800. 10%
        // discount BEFORE mode -> discount=80, companyShare=720.
        //
        // The ceiling is set to 800.00, NOT 720.00: BenefitBucketLimitService's
        // synthetic annual-ceiling bucket checks THIS claim's own basis at
        // coverage-calculation time using coveragePercent-scaled gross
        // (800 = 1000*80%) -- it is not discount-aware, because the contract
        // discount is only known/applied afterward in ClaimLineFinancialEngine.
        // Setting the ceiling below 800 would pre-emptively refuse part of THIS
        // claim's own gross before the discount ever runs. Setting it to exactly
        // 800 lets this claim pass through untouched (800 is not > 800), so the
        // discount is what determines the final companyShare, and it is THAT
        // number -- 720, not 800 -- that gets recorded as consumed against the
        // ceiling for every claim that follows.
        Fixture f = buildFixture(suffix, new BigDecimal("800.00"), 80, new BigDecimal("10.00"), true);

        ClaimViewDto claim = submit(f, new BigDecimal("1000.00"));
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getApprovedAmount()).isEqualByComparingTo("720.00");
        assertThat(claim.getPatientCoPay()).isEqualByComparingTo("200.00");
        assertThat(claim.getCompanyDiscountAmount()).isEqualByComparingTo("80.00");

        // Positive proof that the remaining headroom is exactly 800.00 - 720.00
        // = 80.00, not 0.00 (which wrong gross/pre-discount tracking would leave):
        // a second claim requesting precisely that much basis (gross=100 at 80%
        // coverage = 80 basis) must still be approved in full, with no
        // coverage-time refusal at all.
        ClaimViewDto second = submit(f, new BigDecimal("100.00"));
        assertThat(second.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(second.getRefusedAmount()).isEqualByComparingTo("0.00");
        // 100 gross * 80% coverage = 80 providerShare, minus 10% discount (8) = 72.
        assertThat(second.getApprovedAmount()).isEqualByComparingTo("72.00");

        // A third claim whose own basis (200 gross * 80% = 160) comfortably
        // exceeds what remains (800.00 - 720.00 - 72.00 = 8.00) must be refused
        // for at least the excess -- the annual-ceiling bucket partially fills
        // rather than outright blocking, so this asserts a real coverage-time
        // limitRefused was applied, not full rejection.
        ClaimViewDto third = submit(f, new BigDecimal("200.00"));
        assertThat(third.getRefusedAmount()).isGreaterThan(BigDecimal.ZERO);
    }
}
