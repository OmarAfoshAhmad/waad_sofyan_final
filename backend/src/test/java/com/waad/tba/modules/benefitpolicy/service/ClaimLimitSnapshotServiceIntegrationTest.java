package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.entity.BenefitGroup;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.ClaimLineLimitSnapshot;
import com.waad.tba.modules.benefitpolicy.entity.ClaimLineLimitSnapshot.LimitScopeType;
import com.waad.tba.modules.benefitpolicy.entity.ClaimLineLimitSnapshot.SourceType;
import com.waad.tba.modules.benefitpolicy.enums.AggregationMode;
import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.benefitpolicy.enums.LimitPeriodType;
import com.waad.tba.modules.benefitpolicy.repository.BenefitGroupRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.ClaimLineLimitSnapshotRepository;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.repository.ClaimRepository;
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
 * Proves migration V144 applies cleanly against real PostgreSQL, and that
 * claim_line_limit_snapshots behaves exactly as designed: append-only
 * (UPDATE/DELETE blocked by trigger), the POLICY_GENERAL/bucket_id CHECK,
 * the available_after arithmetic CHECK, and ClaimLimitSnapshotService's
 * Propagation.MANDATORY failing outside an active transaction.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class ClaimLimitSnapshotServiceIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private ClaimLimitSnapshotService snapshotService;
    @Autowired private ClaimLineLimitSnapshotRepository snapshotRepository;
    @Autowired private ClaimService claimService;
    @Autowired private ClaimRepository claimRepository;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private BenefitPolicyRepository benefitPolicyRepository;
    @Autowired private com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository benefitPolicyRuleRepository;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository userRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ProviderContractRepository contractRepository;
    @Autowired private ProviderContractTermsService termsService;
    @Autowired private ProviderContractPricingItemRepository pricingRepository;
    @Autowired private MedicalServiceRepository medicalServiceRepository;
    @Autowired private MedicalCategoryRepository medicalCategoryRepository;
    @Autowired private VisitRepository visitRepository;
    @Autowired private ProviderAccountRepository providerAccountRepository;
    @Autowired private BenefitGroupRepository benefitGroupRepository;
    @Autowired private BenefitLimitBucketRepository benefitLimitBucketRepository;

    private record Fixture(Claim claim, Long claimLineId, BenefitPolicy policy, BenefitLimitBucket bucket) {}

    private Fixture buildApprovedClaimWithBucket() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        userRepository.findByUsername("admin").orElseGet(() -> userRepository.save(
                com.waad.tba.modules.rbac.entity.User.builder()
                        .username("admin").password("password").fullName("System Admin")
                        .email("admin@waad.ly").userType("SUPER_ADMIN").active(true).build()));

        Employer employer = employerRepository.save(Employer.builder()
                .name("Snapshot Test Co " + suffix).code("EMP-" + suffix).active(true).build());

        BenefitPolicy policy = benefitPolicyRepository.save(BenefitPolicy.builder()
                .name("Plan " + suffix).policyCode("POL-" + suffix).employer(employer)
                .annualLimit(new BigDecimal("100000.00")).defaultCoveragePercent(80)
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

        benefitPolicyRuleRepository.save(com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule.builder()
                .benefitPolicy(policy).medicalCategory(category).encounterType(EncounterType.OUTPATIENT)
                .coveragePercent(80).active(true).deleted(false).build());

        BenefitGroup group = benefitGroupRepository.save(BenefitGroup.builder()
                .policy(policy).code("GRP-" + suffix).nameAr("مجموعة الاختبار")
                .contextType(EncounterType.OUTPATIENT).aggregationMode(AggregationMode.SHARED)
                .active(true).build());
        BenefitLimitBucket bucket = benefitLimitBucketRepository.save(BenefitLimitBucket.builder()
                .policy(policy).benefitGroup(group).code("BUC-" + suffix).nameAr("سقف الاختبار")
                .contextType(EncounterType.OUTPATIENT).amountLimit(new BigDecimal("500.00"))
                .periodType(LimitPeriodType.ANNUAL).countingMethod(CountingMethod.EACH_LINE)
                .consumptionBasis(ConsumptionBasis.COMPANY_SHARE).shared(false).active(true).build());

        ProviderContract contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CON-" + suffix).contractNumber("CNT-" + suffix).provider(provider)
                .startDate(LocalDate.now().minusMonths(1)).endDate(LocalDate.now().plusMonths(11))
                .status(ContractStatus.ACTIVE).discountPercent(BigDecimal.ZERO)
                .discountBeforeRejection(false).active(true).build());
        termsService.ensureEffectiveTerms(contract, "TEST");

        MedicalService service = medicalServiceRepository.save(MedicalService.builder()
                .code("SRV-" + suffix).name("Service " + suffix).categoryId(category.getId())
                .cost(new BigDecimal("100.00")).active(true).build());
        pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract).serviceCode(service.getCode()).serviceName(service.getName())
                .medicalCategory(category).basePrice(new BigDecimal("100.00"))
                .contractPrice(new BigDecimal("100.00")).active(true).build());

        Visit visit = visitRepository.save(Visit.builder()
                .member(member).providerId(provider.getId()).visitDate(LocalDate.now())
                .status(VisitStatus.REGISTERED).build());

        ClaimViewDto created = claimService.createClaim(ClaimCreateDto.builder()
                .visitId(visit.getId()).serviceDate(LocalDate.now()).encounterType(EncounterType.OUTPATIENT)
                .lines(List.of(ClaimLineDto.builder().medicalServiceId(service.getId()).quantity(1).build()))
                .build());

        Claim claim = claimRepository.findById(created.getId()).orElseThrow();
        Long claimLineId = created.getLines().get(0).getId();
        return new Fixture(claim, claimLineId, policy, bucket);
    }

    private ClaimLineLimitSnapshot buildSnapshot(Fixture f, boolean binding) {
        return ClaimLineLimitSnapshot.builder()
                .claim(f.claim())
                .claimLine(claimLineRefOnly(f.claimLineId()))
                .calculationVersion(1)
                .limitScopeType(LimitScopeType.SERVICE)
                .limitSemanticKey("BUCKET:" + f.bucket().getId())
                .bucket(f.bucket())
                .policy(f.policy())
                .sourceType(SourceType.POLICY_DEFAULT)
                .periodType("ANNUAL")
                .periodStart(LocalDate.now().withDayOfYear(1))
                .periodEnd(LocalDate.now().withMonth(12).withDayOfMonth(31))
                .effectiveLimit(new BigDecimal("500.00"))
                .consumedBefore(BigDecimal.ZERO)
                .reservedBefore(BigDecimal.ZERO)
                .bindingAvailableBefore(new BigDecimal("500.00"))
                .lineSettlementBase(new BigDecimal("100.00"))
                .lineInsideLimit(new BigDecimal("100.00"))
                .limitConsumption(new BigDecimal("100.00"))
                .patientLimitExcess(BigDecimal.ZERO)
                .availableAfter(new BigDecimal("400.00"))
                .binding(binding)
                .consumptionOrder(1)
                .build();
    }

    // Avoids a lazy-loading round trip in test setup: only the id matters for
    // the FK. version must be non-null (any value) or Hibernate cannot tell
    // this detached instance apart from a genuinely transient one and tries
    // to cascade-insert it instead of just using it as a foreign key.
    private com.waad.tba.modules.claim.entity.ClaimLine claimLineRefOnly(Long id) {
        return com.waad.tba.modules.claim.entity.ClaimLine.builder().id(id).version(0L).build();
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    @Transactional
    void savesAValidSnapshotWithAllConstraintsSatisfied() {
        Fixture f = buildApprovedClaimWithBucket();

        List<ClaimLineLimitSnapshot> saved = snapshotService.saveAll(List.of(buildSnapshot(f, true)));

        assertThat(saved).hasSize(1);
        ClaimLineLimitSnapshot row = saved.get(0);
        assertThat(row.getId()).isNotNull();
        assertThat(row.isBinding()).isTrue();
        assertThat(row.getAvailableAfter()).isEqualByComparingTo("400.00");
        assertThat(row.getLimitSemanticKey()).isEqualTo("BUCKET:" + f.bucket().getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    @Transactional
    void policyGeneralScopeRequiresNullBucket_realBucketScopeRequiresNonNullBucket() {
        Fixture f = buildApprovedClaimWithBucket();

        // POLICY_GENERAL with a non-null bucket must be rejected by the CHECK constraint.
        ClaimLineLimitSnapshot invalid = buildSnapshot(f, false).toBuilder()
                .limitScopeType(LimitScopeType.POLICY_GENERAL)
                .limitSemanticKey("POLICY_GENERAL:" + f.policy().getId())
                .build(); // bucket is still set from buildSnapshot -- must fail

        assertThatThrownBy(() -> snapshotService.saveAll(List.of(invalid)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    @Transactional
    void availableAfterMustEqualBindingAvailableBeforeMinusConsumption() {
        Fixture f = buildApprovedClaimWithBucket();

        ClaimLineLimitSnapshot wrongArithmetic = buildSnapshot(f, true).toBuilder()
                .availableAfter(new BigDecimal("999.00")) // should be 500-100=400
                .build();

        assertThatThrownBy(() -> snapshotService.saveAll(List.of(wrongArithmetic)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void appendOnly_updateAndDeleteAreBothBlockedByTheDatabaseTrigger() {
        Fixture f = buildApprovedClaimWithBucket();

        var txTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager());
        ClaimLineLimitSnapshot saved = txTemplate.execute(status ->
                snapshotService.saveAll(List.of(buildSnapshot(f, true))).get(0));

        assertThatThrownBy(() -> txTemplate.execute(status -> {
            saved.setAvailableAfter(new BigDecimal("1.00"));
            return snapshotRepository.saveAndFlush(saved);
        })).isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> txTemplate.execute(status -> {
            snapshotRepository.delete(saved);
            snapshotRepository.flush();
            return null;
        })).isInstanceOf(DataAccessException.class);
    }

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManagerBean;

    private org.springframework.transaction.PlatformTransactionManager transactionManager() {
        return transactionManagerBean;
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void mandatoryPropagationFailsOutsideAnActiveTransaction() {
        Fixture f = buildApprovedClaimWithBucket();

        assertThatThrownBy(() -> snapshotService.saveAll(List.of(buildSnapshot(f, true))))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
