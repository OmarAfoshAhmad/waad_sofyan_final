package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.claim.service.ClaimLegacyReconciliationService.ReconciliationReport;
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
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Proves the admin-triggered legacy repair (case A: zero-approved APPROVED claim)
 * against a simulated pre-fix row: a normal claim is created through the API, then a
 * raw SQL update forces it back into the "legacy bug" shape (approved_amount = 0,
 * company_share = 0 on its line) — the exact state the old code could produce before
 * this fix — and the reconciliation service must move it out of APPROVED without
 * touching claims that are genuinely fine.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@Transactional
class ClaimLegacyReconciliationServiceIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private ClaimService claimService;

    @Autowired
    private ClaimLegacyReconciliationService reconciliationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

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
    private ProviderContractPricingItemRepository pricingRepository;

    @Autowired
    private MedicalServiceRepository medicalServiceRepository;

    @Autowired
    private MedicalCategoryRepository medicalCategoryRepository;

    @Autowired
    private VisitRepository visitRepository;

    @Autowired
    private ProviderAccountRepository providerAccountRepository;

    @Autowired
    private ClaimRepository claimRepository;

    private String suffix;
    private Member member;
    private Provider provider;
    private ProviderContract contract;
    private MedicalCategory category;

    @BeforeEach
    void setupData() {
        suffix = UUID.randomUUID().toString().substring(0, 8);

        userRepository.findByUsername("admin").orElseGet(() -> userRepository.save(
                com.waad.tba.modules.rbac.entity.User.builder()
                        .username("admin").password("password").fullName("System Admin")
                        .email("admin@waad.ly").userType("SUPER_ADMIN").active(true).build()));

        Employer employer = employerRepository.save(Employer.builder()
                .name("Repair Test Company " + suffix).code("EMP-" + suffix).active(true).build());

        BenefitPolicy policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Repair Test Plan " + suffix).policyCode("POL-" + suffix)
                .employer(employer).annualLimit(new BigDecimal("100000.00"))
                .defaultCoveragePercent(100)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

        member = memberRepository.save(Member.builder()
                .fullName("Repair Test Member " + suffix).barcode("BC-" + suffix)
                .nationalNumber("NAT-" + suffix).employer(employer).benefitPolicy(policy).active(true).build());

        provider = providerRepository.save(Provider.builder()
                .name("Repair Test Hospital " + suffix).providerType(ProviderType.HOSPITAL)
                .licenseNumber("LIC-" + suffix).allowAllEmployers(true).active(true).build());

        providerAccountRepository.save(ProviderAccount.builder()
                .providerId(provider.getId()).runningBalance(BigDecimal.ZERO)
                .totalApproved(BigDecimal.ZERO).totalPaid(BigDecimal.ZERO).build());

        category = medicalCategoryRepository.save(MedicalCategory.builder()
                .code("CAT-" + suffix).name("General Services").active(true).build());

        benefitPolicyRuleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy).medicalCategory(category)
                .encounterType(EncounterType.OUTPATIENT).coveragePercent(100)
                .active(true).deleted(false).build());

        contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CON-" + suffix).contractNumber("CNT-" + suffix).provider(provider)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                .status(ContractStatus.ACTIVE).active(true).build());
        // Mirrors production: every contract-creating path must also create its
        // effective terms row. The resolver fails closed, so a contract saved
        // without terms makes claim creation impossible for that provider.
        termsService.ensureEffectiveTerms(contract, "TEST");
    }

    private ClaimViewDto createApprovedClaim(BigDecimal price, String label) {
        MedicalService service = medicalServiceRepository.save(MedicalService.builder()
                .code("SRV-" + suffix + "-" + label).name("Service " + label)
                .categoryId(category.getId()).cost(price).active(true).build());
        pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract).serviceCode(service.getCode()).serviceName(service.getName())
                .medicalCategory(category).basePrice(price).contractPrice(price).active(true).build());
        Visit visit = visitRepository.save(Visit.builder()
                .member(member).providerId(provider.getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());
        return claimService.createClaim(ClaimCreateDto.builder()
                .visitId(visit.getId()).serviceDate(LocalDate.now()).encounterType(EncounterType.OUTPATIENT)
                .lines(List.of(ClaimLineDto.builder().medicalServiceId(service.getId()).quantity(1).build()))
                .build());
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void reconciliation_fixesSimulatedLegacyZeroApprovedClaim_andLeavesHealthyClaimsUntouched() {
        // A perfectly healthy, currently-created claim — must NOT be touched by the repair.
        ClaimViewDto healthy = createApprovedClaim(new BigDecimal("40.00"), "HEALTHY");
        assertThat(healthy.getStatus()).isEqualTo(ClaimStatus.APPROVED);

        // A second claim, created normally (so it has valid lines/state), then forced
        // back into the pre-fix "legacy bug" shape via raw SQL: APPROVED status with
        // approved_amount = 0 and company_share = 0 on its only line. No COMMITTED
        // ledger row exists for it (this policy has no benefit buckets configured),
        // matching exactly the legacy pattern case A targets.
        ClaimViewDto legacy = createApprovedClaim(new BigDecimal("40.00"), "LEGACY");
        // Flush first so any Hibernate-cached state from createApprovedClaim() is
        // written out, then clear the persistence context so the raw JDBC update
        // below isn't later overwritten by an auto-flush of a stale in-memory entity,
        // and so the reconciliation service reads truly fresh rows.
        entityManager.flush();
        entityManager.clear();
        // Keep the claim's financial identity balanced (requestedAmount = patientCoPay
        // + refusedAmount + companyDiscountAmount + netPayableAmount is enforced by
        // Claim.validateFinancialIdentity()): the realistic legacy shape for "no
        // qualifying amount" is everything refused, nothing approved.
        jdbcTemplate.update(
                "UPDATE claims SET approved_amount = 0, net_provider_amount = 0, refused_amount = requested_amount WHERE id = ?",
                legacy.getId());
        jdbcTemplate.update("UPDATE claim_lines SET company_share = 0, refused_amount = requested_total WHERE claim_id = ?",
                legacy.getId());
        entityManager.clear();

        ReconciliationReport report = reconciliationService.reconcileLegacyClaims();

        assertThat(report.getZeroApprovedFixed()).isGreaterThanOrEqualTo(1);
        // Not asserting failed.isEmpty() globally: this Testcontainers instance is
        // shared across the whole test run, so other integration tests may leave
        // unrelated legacy-shaped rows behind — only this test's own claim matters here.
        assertThat(report.getFailed()).noneMatch(f -> f.claimId().equals(legacy.getId()));

        ClaimViewDto reloadedLegacy = claimService.getClaim(legacy.getId());
        assertThat(reloadedLegacy.getStatus()).isEqualTo(ClaimStatus.NEEDS_CORRECTION);

        ClaimViewDto reloadedHealthy = claimService.getClaim(healthy.getId());
        assertThat(reloadedHealthy.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(reloadedHealthy.getApprovedAmount()).isEqualByComparingTo("40.00");
    }
}
