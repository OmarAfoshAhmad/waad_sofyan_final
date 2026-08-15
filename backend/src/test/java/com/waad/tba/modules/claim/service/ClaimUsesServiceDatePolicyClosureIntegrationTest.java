package com.waad.tba.modules.claim.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.claim.dto.ClaimCreateDto;
import com.waad.tba.modules.claim.dto.ClaimLineDto;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalService;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalServiceRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.entity.PolicyAssignmentSource;
import com.waad.tba.modules.member.entity.EmployerAssignmentSource;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.service.MemberPolicyResolver;
import com.waad.tba.modules.member.service.MemberEmployerResolver;
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
 * CLOSURE PROOF for the dated financial conversion: one real claim cycle
 * proving all five layers agreed on the policy in force ON THE SERVICE DATE,
 * rather than five separate mock-based tests that could each be individually
 * right while the composition is wrong.
 *
 * The fixture is built to make an accidental pass impossible:
 *   - Policy A: 80% coverage, annual limit 1,000. Effective in the PAST.
 *   - Policy B: 50% coverage, annual limit 10,000. Effective NOW.
 *   - The member's current pointer AND current open assignment are B.
 *   - The service date falls inside A and outside B.
 * A gross of 1,000 therefore yields 800 under A and 500 under B -- so the
 * amount alone distinguishes them, and the assertions additionally pin the
 * policy identity and the ASSIGNMENT identity (two different assignment
 * periods can share one logical policy, which policyId alone cannot
 * distinguish -- see V172).
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class ClaimUsesServiceDatePolicyClosureIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private ClaimService claimService;
    @Autowired private MemberPolicyResolver policyResolver;
    @Autowired private MemberEmployerResolver employerResolver;
    @Autowired private MemberRepository memberRepository;
    @Autowired private EmployerRepository employerRepository;
    @Autowired private BenefitPolicyRepository policyRepository;
    @Autowired private BenefitPolicyRuleRepository ruleRepository;
    @Autowired private MedicalCategoryRepository categoryRepository;
    @Autowired private MedicalServiceRepository serviceRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private ProviderContractRepository contractRepository;
    @Autowired private ProviderContractTermRepository contractTermRepository;
    @Autowired private ProviderContractPricingItemRepository pricingRepository;
    @Autowired private ProviderAccountRepository providerAccountRepository;
    @Autowired private VisitRepository visitRepository;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    // Policy A -- the one in force on the service date.
    private static final BigDecimal A_LIMIT = new BigDecimal("1000.00");
    private static final int A_COVERAGE = 80;
    // Policy B -- the one the member points at today. Deliberately different
    // on BOTH axes so neither the percentage nor the ceiling can coincide.
    private static final BigDecimal B_LIMIT = new BigDecimal("10000.00");
    private static final int B_COVERAGE = 50;

    private static final BigDecimal GROSS = new BigDecimal("1000.00");
    private static final BigDecimal EXPECTED_UNDER_A = new BigDecimal("800.00");
    private static final BigDecimal EXPECTED_UNDER_B = new BigDecimal("500.00");

    private record Fixture(Member member, Employer employerA, Employer employerB,
            BenefitPolicy policyA, BenefitPolicy policyB,
            Long assignmentA, Long assignmentB, Provider provider, ProviderContract contract,
            MedicalCategory category, String suffix) {}

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * A ends before B begins, leaving a deliberate one-month GAP in policy
     * coverage while assignment A still spans it -- so a date in that window
     * is "assignment says A, but A had already expired", which must fail
     * closed rather than silently fall through to B.
     */
    private Fixture build(String s) {
        userRepository.findByUsername("admin").orElseGet(() -> userRepository.save(
                com.waad.tba.modules.rbac.entity.User.builder()
                        .username("admin").password("password").fullName("System Admin")
                        .email("admin@waad.ly").userType("SUPER_ADMIN").active(true).build()));

        Employer employerA = employerRepository.save(Employer.builder()
                .name("Closure Co A " + s).code("CL-A-" + s).active(true).build());
        Employer employerB = employerRepository.save(Employer.builder()
                .name("Closure Co B " + s).code("CL-B-" + s).active(true).build());

        BenefitPolicy policyA = policyRepository.save(BenefitPolicy.builder()
                .name("Policy A " + s).policyCode("POL-A-" + s).employer(employerA)
                .annualLimit(A_LIMIT).defaultCoveragePercent(A_COVERAGE)
                .startDate(LocalDate.now().minusYears(2)).endDate(LocalDate.now().minusMonths(7))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());
        BenefitPolicy policyB = policyRepository.save(BenefitPolicy.builder()
                .name("Policy B " + s).policyCode("POL-B-" + s).employer(employerB)
                .annualLimit(B_LIMIT).defaultCoveragePercent(B_COVERAGE)
                .startDate(LocalDate.now().minusMonths(6)).endDate(LocalDate.now().plusYears(1))
                .status(BenefitPolicyStatus.ACTIVE).active(true).build());

        MedicalCategory category = categoryRepository.save(MedicalCategory.builder()
                .code("CAT-" + s).name("General").active(true).build());
        ruleRepository.save(BenefitPolicyRule.builder().benefitPolicy(policyA)
                .medicalCategory(category).encounterType(EncounterType.OUTPATIENT)
                .coveragePercent(A_COVERAGE).active(true).deleted(false).build());
        ruleRepository.save(BenefitPolicyRule.builder().benefitPolicy(policyB)
                .medicalCategory(category).encounterType(EncounterType.OUTPATIENT)
                .coveragePercent(B_COVERAGE).active(true).deleted(false).build());

        Member member = memberRepository.save(Member.builder()
                .fullName("Closure Member " + s).employer(employerA).benefitPolicy(policyA)
                .cardNumber("CL" + s).barcode("CL" + s)
                .status(Member.MemberStatus.ACTIVE).active(true).build());

        employerResolver.assignEmployer(member, employerA, LocalDate.now().minusYears(2),
                "initial employer", EmployerAssignmentSource.MANUAL, 1L);

        // Historical assignment to A, then the CURRENT assignment to B. This
        // also leaves members.benefit_policy_id pointing at B.
        Long assignmentA = policyResolver.assignPolicy(member, policyA, LocalDate.now().minusYears(2),
                "initial coverage", PolicyAssignmentSource.MANUAL, 1L).getId();
        employerResolver.assignEmployer(member, employerB, LocalDate.now().minusMonths(6),
                "moved to the new employer", EmployerAssignmentSource.MANUAL, 1L);
        Long assignmentB = policyResolver.assignPolicy(member, policyB, LocalDate.now().minusMonths(6),
                "moved to the new policy", PolicyAssignmentSource.MANUAL, 1L).getId();
        member = memberRepository.saveAndFlush(member);
        assertThat(member.getBenefitPolicy().getId())
                .as("the fixture must leave the CURRENT pointer on B, so reading it would be wrong")
                .isEqualTo(policyB.getId());
        assertThat(member.getEmployer().getId())
                .as("the fixture must leave the CURRENT employer pointer on B")
                .isEqualTo(employerB.getId());

        Provider provider = providerRepository.save(Provider.builder()
                .name("Hospital " + s).providerType(ProviderType.HOSPITAL)
                .licenseNumber("LIC-" + s).allowAllEmployers(true).active(true).build());
        providerAccountRepository.save(ProviderAccount.builder()
                .providerId(provider.getId()).runningBalance(BigDecimal.ZERO)
                .totalApproved(BigDecimal.ZERO).totalPaid(BigDecimal.ZERO).build());

        ProviderContract contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CON-" + s).contractNumber("CNT-" + s).provider(provider)
                .startDate(LocalDate.now().minusYears(3)).endDate(LocalDate.now().plusYears(1))
                .status(ContractStatus.ACTIVE).discountPercent(BigDecimal.ZERO)
                .discountBeforeRejection(false).active(true).build());
        contractTermRepository.save(ProviderContractTerm.builder()
                .contract(contract).effectiveFrom(contract.getStartDate())
                .discountPercent(BigDecimal.ZERO).discountBeforeRejection(false)
                .changeReason("closure test terms").build());

        return new Fixture(member, employerA, employerB, policyA, policyB, assignmentA, assignmentB,
                provider, contract, category, s);
    }

    private ClaimViewDto submit(Fixture f, LocalDate serviceDate) {
        String priceSuffix = UUID.randomUUID().toString().substring(0, 8);
        MedicalService service = serviceRepository.save(MedicalService.builder()
                .code("SRV-" + f.suffix() + "-" + priceSuffix).name("Service " + priceSuffix)
                .categoryId(f.category().getId()).cost(GROSS).active(true).build());
        pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(f.contract()).serviceCode(service.getCode()).serviceName(service.getName())
                .medicalCategory(f.category()).basePrice(GROSS)
                .contractPrice(GROSS).active(true).build());

        Visit visit = visitRepository.save(Visit.builder()
                .member(f.member()).providerId(f.provider().getId()).visitDate(serviceDate)
                .status(VisitStatus.REGISTERED).build());
        return claimService.createClaim(ClaimCreateDto.builder()
                .visitId(visit.getId()).serviceDate(serviceDate).encounterType(EncounterType.OUTPATIENT)
                .lines(List.of(ClaimLineDto.builder().medicalServiceId(service.getId()).quantity(1).build()))
                .build());
    }

    /** Reads committed state in a genuinely separate transaction. */
    private <T> T inFreshTransaction(java.util.function.Supplier<T> work) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(status -> work.get());
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void everyLayerUsesThePolicyInForceOnTheServiceDateNotTheCurrentPointer() {
        Fixture f = build(suffix());
        LocalDate serviceDate = LocalDate.now().minusYears(1); // inside A, outside B

        ClaimViewDto claim = submit(f, serviceDate);
        Long claimId = claim.getId();

        // 1. The money itself was computed with A's coverage percentage.
        assertThat(claim.getApprovedAmount())
                .as("800 under A's 80%%, not 500 under B's 50%%")
                .isEqualByComparingTo(EXPECTED_UNDER_A);

        // Everything below is read back AFTER the claim transaction committed,
        // in a new transaction, so nothing is served from a stale first-level
        // cache.
        inFreshTransaction(() -> {
            // 2. The limit snapshot records policy A, its assignment period,
            //    and A's ceiling -- not B's.
            List<Map<String, Object>> snapshots = jdbc.queryForList(
                    "SELECT policy_id, member_policy_assignment_id, effective_limit "
                            + "FROM claim_line_limit_snapshots WHERE claim_id = ?", claimId);
            assertThat(snapshots).as("the claim must have produced limit snapshots").isNotEmpty();
            for (Map<String, Object> row : snapshots) {
                assertThat(((Number) row.get("policy_id")).longValue())
                        .as("snapshot must record policy A").isEqualTo(f.policyA().getId());
                assertThat(row.get("member_policy_assignment_id"))
                        .as("snapshot must record WHICH assignment period, not only the policy")
                        .isNotNull();
                assertThat(((Number) row.get("member_policy_assignment_id")).longValue())
                        .isEqualTo(f.assignmentA());
                assertThat(new BigDecimal(row.get("effective_limit").toString()))
                        .as("the ceiling read was A's 1,000, not B's 10,000")
                        .isEqualByComparingTo(A_LIMIT);
            }

            // 3. Nothing anywhere references policy B.
            Long snapshotsOnB = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM claim_line_limit_snapshots WHERE claim_id = ? AND policy_id = ?",
                    Long.class, claimId, f.policyB().getId());
            assertThat(snapshotsOnB).as("no snapshot may reference the current-pointer policy B").isZero();

            Long consumptionsOnB = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM benefit_bucket_consumptions c "
                            + "JOIN benefit_limit_buckets b ON b.id = c.bucket_id "
                            + "WHERE c.claim_id = ? AND b.policy_id = ?",
                    Long.class, claimId, f.policyB().getId());
            assertThat(consumptionsOnB).as("no ledger consumption may land in a policy B bucket").isZero();

            // 4. The provider account received the A-derived amount.
            BigDecimal totalApproved = jdbc.queryForObject(
                    "SELECT total_approved FROM provider_accounts WHERE provider_id = ?",
                    BigDecimal.class, f.provider().getId());
            assertThat(totalApproved)
                    .as("the provider is credited the amount A produced")
                    .isEqualByComparingTo(EXPECTED_UNDER_A);

            // 5. Re-reading the claim itself gives the same figure.
            BigDecimal persistedApproved = jdbc.queryForObject(
                    "SELECT approved_amount FROM claims WHERE id = ?", BigDecimal.class, claimId);
            assertThat(persistedApproved).isEqualByComparingTo(EXPECTED_UNDER_A);
            Long batchEmployer = jdbc.queryForObject(
                    "SELECT b.employer_id FROM claims c JOIN claim_batches b ON b.id = c.claim_batch_id "
                            + "WHERE c.id = ?", Long.class, claimId);
            assertThat(batchEmployer)
                    .as("the historical claim must be grouped under historical employer A")
                    .isEqualTo(f.employerA().getId());
            return null;
        });
    }

    /**
     * The mirror case -- proves the test is not simply forcing A. A service
     * date inside B's period must use B, with B's percentage and B's ceiling.
     */
    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void aServiceDateInsideTheCurrentPolicyPeriodUsesThatPolicy() {
        Fixture f = build(suffix());
        LocalDate serviceDate = LocalDate.now().minusMonths(1); // inside B

        ClaimViewDto claim = submit(f, serviceDate);

        assertThat(claim.getApprovedAmount())
                .as("500 under B's 50%%")
                .isEqualByComparingTo(EXPECTED_UNDER_B);

        inFreshTransaction(() -> {
            List<Map<String, Object>> snapshots = jdbc.queryForList(
                    "SELECT policy_id, member_policy_assignment_id FROM claim_line_limit_snapshots "
                            + "WHERE claim_id = ?", claim.getId());
            assertThat(snapshots).isNotEmpty();
            for (Map<String, Object> row : snapshots) {
                assertThat(((Number) row.get("policy_id")).longValue()).isEqualTo(f.policyB().getId());
                assertThat(((Number) row.get("member_policy_assignment_id")).longValue())
                        .isEqualTo(f.assignmentB());
            }
            Long batchEmployer = jdbc.queryForObject(
                    "SELECT b.employer_id FROM claims c JOIN claim_batches b ON b.id = c.claim_batch_id "
                            + "WHERE c.id = ?", Long.class, claim.getId());
            assertThat(batchEmployer)
                    .as("the current claim must be grouped under current employer B")
                    .isEqualTo(f.employerB().getId());
            return null;
        });
    }

    /**
     * A date in the gap -- assignment A still spans it, but policy A had
     * already expired and B had not started. Must fail closed and write
     * nothing: no claim, no snapshot, no consumption, no provider credit.
     */
    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void aServiceDateInTheGapBetweenPoliciesFailsClosedAndWritesNothing() {
        Fixture f = build(suffix());
        LocalDate gapDate = LocalDate.now().minusMonths(6).minusDays(15);

        long claimsBefore = countClaimsForMember(f.member().getId());

        assertThatThrownBy(() -> submit(f, gapDate))
                .as("no policy was in force on that date -- the claim must not be created")
                .isInstanceOf(Exception.class);

        inFreshTransaction(() -> {
            assertThat(countClaimsForMember(f.member().getId()))
                    .as("no claim row may survive").isEqualTo(claimsBefore);
            BigDecimal totalApproved = jdbc.queryForObject(
                    "SELECT total_approved FROM provider_accounts WHERE provider_id = ?",
                    BigDecimal.class, f.provider().getId());
            assertThat(totalApproved).as("the provider must not be credited").isEqualByComparingTo("0.00");
            return null;
        });
    }

    /**
     * Proves the clock fallbacks were genuinely removed: with no service date
     * the operation stops before writing anything, instead of quietly using
     * today (which would have resolved to B).
     */
    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void aMissingServiceDateFailsBeforeAnyWrite() {
        Fixture f = build(suffix());
        long claimsBefore = countClaimsForMember(f.member().getId());

        assertThatThrownBy(() -> submit(f, null))
                .as("a missing service date must never be replaced by today")
                .isInstanceOf(Exception.class);

        inFreshTransaction(() -> {
            assertThat(countClaimsForMember(f.member().getId())).isEqualTo(claimsBefore);
            return null;
        });
    }

    private long countClaimsForMember(Long memberId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM claims WHERE member_id = ?", Long.class, memberId);
        return count != null ? count : 0L;
    }
}
