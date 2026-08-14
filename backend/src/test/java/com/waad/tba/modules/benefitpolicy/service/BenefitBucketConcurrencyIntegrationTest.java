package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.entity.*;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.enums.*;
import com.waad.tba.modules.benefitpolicy.repository.*;
import com.waad.tba.modules.claim.dto.*;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.claim.service.ClaimService;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.*;
import com.waad.tba.modules.medicaltaxonomy.repository.*;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.entity.Provider.ProviderType;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.providercontract.entity.*;
import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import com.waad.tba.modules.providercontract.repository.*;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.modules.visit.entity.*;
import com.waad.tba.modules.visit.repository.VisitRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class BenefitBucketConcurrencyIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired ClaimService claimService;
    @Autowired BenefitBucketLedgerService ledgerService;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EmployerRepository employerRepository;
    @Autowired BenefitPolicyRepository policyRepository;
    @Autowired BenefitPolicyRuleRepository ruleRepository;
    @Autowired BenefitGroupRepository groupRepository;
    @Autowired BenefitLimitBucketRepository bucketRepository;
    @Autowired BenefitRuleBucketRepository ruleBucketRepository;
    @Autowired BenefitBucketConsumptionRepository consumptionRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired ProviderRepository providerRepository;
    @Autowired ProviderContractRepository contractRepository;
    @Autowired com.waad.tba.modules.providercontract.service.ProviderContractTermsService termsService;
    @Autowired ProviderContractPricingItemRepository pricingRepository;
    @Autowired MedicalCategoryRepository categoryRepository;
    @Autowired MedicalServiceRepository serviceRepository;
    @Autowired VisitRepository visitRepository;
    @Autowired ClaimRepository claimRepository;
    @Autowired UserRepository userRepository;
    @Autowired EffectiveLimitResolver effectiveLimitResolver;
    @Autowired LimitBalanceReader limitBalanceReader;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void ensureAuthenticatedUserExists() {
        userRepository.findByUsername("superadmin").orElseGet(() ->
                userRepository.save(User.builder()
                        .username("superadmin")
                        .password("test-password-not-used-for-authentication")
                        .fullName("Integration Test Super Admin")
                        .email("superadmin.integration@waad.test")
                        .userType("SUPER_ADMIN")
                        .active(true)
                        .build()));
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void concurrentClaimsCannotOverdrawAndReversalIsIdempotent() throws Exception {
        Fixture f = createFixture(new BigDecimal("70"), null, null);
        Long firstClaim = createClaim(f, createVisit(f, LocalDate.now()), LocalDate.now());
        Long secondClaim = createClaim(f, createVisit(f, LocalDate.now()), LocalDate.now());

        // Both previews were calculated before either ledger commit. Each wants 48
        // from the same 70-unit bucket, so at most one may commit.
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> commitAfterBarrier(firstClaim, ready, start));
            Future<Boolean> second = pool.submit(() -> commitAfterBarrier(secondClaim, ready, start));
            ready.await();
            start.countDown();

            List<Boolean> outcomes = List.of(first.get(), second.get());
            assertThat(outcomes).containsExactlyInAnyOrder(true, false);

            BigDecimal committed = consumptionRepository.sumCommittedAmount(
                    f.member().getId(), f.bucket().getId(),
                    LocalDate.of(LocalDate.now().getYear(), 1, 1),
                    LocalDate.of(LocalDate.now().getYear(), 12, 31), null);
            assertThat(committed).isEqualByComparingTo("60.00");

            Long acceptedClaim = outcomes.get(0) ? firstClaim : secondClaim;
            reverseInTransaction(acceptedClaim);
            reverseInTransaction(acceptedClaim);

            BigDecimal afterReversal = consumptionRepository.sumCommittedAmount(
                    f.member().getId(), f.bucket().getId(),
                    LocalDate.of(LocalDate.now().getYear(), 1, 1),
                    LocalDate.of(LocalDate.now().getYear(), 12, 31), null);
            assertThat(afterReversal).isZero();

            long reversalRows = consumptionRepository.findAll().stream()
                    // Since V174 a movement need not have a claim (a PREAUTH
                    // hold has none), and findAll() sees the whole table.
                    .filter(c -> c.getClaim() != null && c.getClaim().getId().equals(acceptedClaim))
                    .filter(c -> c.getReversalOf() != null)
                    .count();
            assertThat(reversalRows).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void concurrentClaimsCannotExceedTimesLimit() throws Exception {
        Fixture f = createFixture(null, 1, null);
        Long firstClaim = createClaim(f, createVisit(f, LocalDate.now()), LocalDate.now());
        Long secondClaim = createClaim(f, createVisit(f, LocalDate.now()), LocalDate.now());

        List<Boolean> outcomes = commitConcurrently(firstClaim, secondClaim);
        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        Integer committedTimes = consumptionRepository.sumCommittedTimes(
                f.member().getId(), f.bucket().getId(),
                LocalDate.of(LocalDate.now().getYear(), 1, 1),
                LocalDate.of(LocalDate.now().getYear(), 12, 31), null);
        assertThat(committedTimes).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void concurrentClaimsOnDifferentDatesCannotExceedDaysLimit() throws Exception {
        Fixture f = createFixture(null, null, 1);
        LocalDate firstDate = LocalDate.now().minusDays(1);
        LocalDate secondDate = LocalDate.now();
        Long firstClaim = createClaim(f, createVisit(f, firstDate), firstDate);
        Long secondClaim = createClaim(f, createVisit(f, secondDate), secondDate);

        List<Boolean> outcomes = commitConcurrently(firstClaim, secondClaim);
        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        Long committedDays = consumptionRepository.countCommittedServiceDays(
                f.member().getId(), f.bucket().getId(),
                LocalDate.of(LocalDate.now().getYear(), 1, 1),
                LocalDate.of(LocalDate.now().getYear(), 12, 31), null);
        assertThat(committedDays).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void twelveConcurrentClaimsRespectSharedAmountLimit() throws Exception {
        int workers = 12;
        Fixture f = createFixture(new BigDecimal("250"), null, null);
        List<Long> claimIds = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            claimIds.add(createClaim(f, createVisit(f, LocalDate.now()), LocalDate.now()));
        }

        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Future<Boolean>> futures = new ArrayList<>();
        long startedAt = System.nanoTime();
        try {
            for (Long claimId : claimIds) {
                futures.add(pool.submit(() -> commitAfterBarrier(claimId, ready, start)));
            }
            ready.await();
            start.countDown();

            int accepted = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    accepted++;
                }
            }
            long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

            assertThat(accepted).isEqualTo(4);
            BigDecimal committed = consumptionRepository.sumCommittedAmount(
                    f.member().getId(), f.bucket().getId(),
                    LocalDate.of(LocalDate.now().getYear(), 1, 1),
                    LocalDate.of(LocalDate.now().getYear(), 12, 31), null);
            assertThat(committed).isEqualByComparingTo("240.00");
            assertThat(committed).isLessThanOrEqualTo(new BigDecimal("250.00"));
            System.out.printf("LOAD_RESULT workers=%d accepted=%d rejected=%d elapsedMs=%d committed=%s limit=250.00%n",
                    workers, accepted, workers - accepted, elapsedMillis, committed);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void principalAndDependentHaveIndependentBalancesFromTheSamePolicyLimits() {
        Fixture principal = createFixture(new BigDecimal("1000.00"), null, null);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member dependentMember = memberRepository.save(Member.builder()
                .fullName("Dependent Member").barcode("DEP-BC-" + suffix)
                .nationalNumber("DEP-NAT-" + suffix).employer(principal.member().getEmployer())
                .benefitPolicy(principal.policy()).parent(principal.member())
                .relationship(Member.Relationship.SON).active(true).build());
        Fixture dependent = new Fixture(dependentMember, principal.provider(), principal.service(),
                principal.bucket(), principal.policy(), principal.rule());

        Long principalClaimId = createClaim(principal, createVisit(principal, LocalDate.now()), LocalDate.now());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var claim = claimRepository.findById(principalClaimId).orElseThrow();
            claim.setStatus(ClaimStatus.APPROVED);
            claimRepository.save(claim);
            ledgerService.commitClaim(principalClaimId);
        });

        // A hold is posted by a PRE-AUTHORIZATION, never by a claim: a claim
        // consumes, it does not reserve. V174 makes that an invariant of the
        // table, so this fixture must express the reservation the way the
        // approval service will.
        Long dependentPreauthId = jdbcTemplate.queryForObject(
                "INSERT INTO pre_authorizations (member_id, policy_id, status, request_date, created_at, "
                        + "updated_at) VALUES (?, ?, 'APPROVED', now(), now(), now()) RETURNING id",
                Long.class, dependent.member().getId(), principal.policy().getId());
        Long dependentPreauthLineId = jdbcTemplate.queryForObject(
                "INSERT INTO pre_authorization_lines (pre_authorization_id, requested_amount) "
                        + "VALUES (?, 60.00) RETURNING id",
                Long.class, dependentPreauthId);

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
            consumptionRepository.save(BenefitBucketConsumption.builder()
                    .policy(principal.policy())
                    .memberId(dependent.member().getId()).bucket(principal.bucket())
                    .periodStart(LocalDate.of(LocalDate.now().getYear(), 1, 1))
                    .periodEnd(LocalDate.of(LocalDate.now().getYear(), 12, 31))
                    .approvedAmount(new BigDecimal("60.00")).timesConsumed(1)
                    .status(BenefitBucketConsumption.Status.RESERVED)
                    .sourceType(BenefitBucketConsumption.SourceType.PREAUTH)
                    .preauthId(dependentPreauthId).preauthLineId(dependentPreauthLineId)
                    .calculationVersion(1)
                    .idempotencyKey("TEST:RESERVED:" + dependentPreauthId).build()));

        var principalLimits = effectiveLimitResolver.resolve(principal.policy().getId(), principal.rule().getId(),
                principal.member().getId(), LocalDate.now(), EncounterType.OUTPATIENT);
        var dependentLimits = effectiveLimitResolver.resolve(dependent.policy().getId(), dependent.rule().getId(),
                dependent.member().getId(), LocalDate.now(), EncounterType.OUTPATIENT);
        var principalBalances = limitBalanceReader.read(principal.member().getId(), principalLimits, null);
        var dependentBalances = limitBalanceReader.read(dependent.member().getId(), dependentLimits, null);

        assertThat(principalLimits).extracting(EffectiveLimitResolver.EffectiveLimit::effectiveLimit)
                .containsExactlyElementsOf(dependentLimits.stream()
                        .map(EffectiveLimitResolver.EffectiveLimit::effectiveLimit).toList());
        assertThat(balance(principalBalances, "BUCKET:" + principal.bucket().getId()).committed())
                .isEqualByComparingTo("60.00");
        assertThat(balance(dependentBalances, "BUCKET:" + principal.bucket().getId()).committed()).isZero();
        assertThat(balance(dependentBalances, "BUCKET:" + principal.bucket().getId()).reserved())
                .isEqualByComparingTo("60.00");
        // The reservation reduces what a NEW decision may consume, but not the
        // member's actual remaining balance -- a hold is not a consumption.
        assertThat(balance(dependentBalances, "BUCKET:" + principal.bucket().getId()).reservableAvailable())
                .isEqualByComparingTo("940.00");
        assertThat(balance(dependentBalances, "BUCKET:" + principal.bucket().getId()).actualRemaining())
                .as("nothing was committed, so the real remaining balance is untouched by the hold")
                .isEqualByComparingTo("1000.00");
        assertThat(balance(principalBalances, "POLICY_GENERAL:" + principal.policy().getId()).committed())
                .isEqualByComparingTo("60.00");
        assertThat(balance(dependentBalances, "POLICY_GENERAL:" + principal.policy().getId()).committed()).isZero();

        var excludingCurrent = limitBalanceReader.read(principal.member().getId(), principalLimits, principalClaimId);
        assertThat(balance(excludingCurrent, "BUCKET:" + principal.bucket().getId()).committed()).isZero();
        assertThat(balance(excludingCurrent, "POLICY_GENERAL:" + principal.policy().getId()).committed()).isZero();
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void liveClaimPathCoordinatesTwoLinesAgainstEverySharedBucket() {
        Fixture f = createFixture(new BigDecimal("70.00"), null, null);
        Visit visit = createVisit(f, LocalDate.now());

        ClaimViewDto created = claimService.createClaim(ClaimCreateDto.builder()
                .visitId(visit.getId()).serviceDate(LocalDate.now()).status(ClaimStatus.SUBMITTED)
                .encounterType(EncounterType.OUTPATIENT)
                .lines(List.of(
                        ClaimLineDto.builder().medicalServiceId(f.service().getId()).quantity(1).build(),
                        ClaimLineDto.builder().medicalServiceId(f.service().getId()).quantity(1).build()))
                .build());

        assertThat(created.getApprovedAmount()).isEqualByComparingTo("56.00");
        record Canonical(BigDecimal firstInside, BigDecimal secondInside,
                         BigDecimal secondExcess, BigDecimal firstPayment, BigDecimal secondPayment,
                         String firstMode, String secondMode) {}
        Canonical canonical = new TransactionTemplate(transactionManager).execute(status -> {
            var persisted = claimRepository.findById(created.getId()).orElseThrow();
            var first = persisted.getLines().get(0);
            var second = persisted.getLines().get(1);
            return new Canonical(first.getInsideLimit(), second.getInsideLimit(), second.getPatientLimitExcess(),
                    first.getInsurerFinalPayment(), second.getInsurerFinalPayment(),
                    first.getLimitMode(), second.getLimitMode());
        });
        assertThat(canonical).isNotNull();
        assertThat(canonical.firstInside()).isEqualByComparingTo("60.00");
        assertThat(canonical.secondInside()).isEqualByComparingTo("10.00");
        assertThat(canonical.secondExcess()).isEqualByComparingTo("50.00");
        assertThat(canonical.firstPayment()).isEqualByComparingTo("48.00");
        assertThat(canonical.secondPayment()).isEqualByComparingTo("8.00");
        assertThat(canonical.firstMode()).isEqualTo("LIMITED");
        assertThat(canonical.secondMode()).isEqualTo("LIMITED");
    }

    private LimitBalanceReader.LimitBalance balance(LimitBalanceReader.BalanceSet set, String semanticKey) {
        return set.limits().stream()
                .filter(value -> value.limit().definition().semanticKey().equals(semanticKey))
                .findFirst().orElseThrow();
    }

    private List<Boolean> commitConcurrently(Long firstClaim, Long secondClaim) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> commitAfterBarrier(firstClaim, ready, start));
            Future<Boolean> second = pool.submit(() -> commitAfterBarrier(secondClaim, ready, start));
            ready.await();
            start.countDown();
            return List.of(first.get(), second.get());
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean commitAfterBarrier(Long claimId, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            new TransactionTemplate(transactionManager).executeWithoutResult(s -> ledgerService.commitClaim(claimId));
            return true;
        } catch (Exception expectedLimitConflict) {
            return false;
        }
    }

    private void reverseInTransaction(Long claimId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> ledgerService.reverseClaim(claimId));
    }

    private Long createClaim(Fixture f, Visit visit, LocalDate serviceDate) {
        ClaimViewDto result = claimService.createClaim(ClaimCreateDto.builder()
                .visitId(visit.getId())
                .serviceDate(serviceDate)
                .status(ClaimStatus.SUBMITTED)
                .encounterType(EncounterType.OUTPATIENT)
                .lines(List.of(ClaimLineDto.builder()
                        .medicalServiceId(f.service().getId())
                        .quantity(1)
                        .build()))
                .build());
        assertThat(result.getApprovedAmount()).isEqualByComparingTo("48.00");
        return result.getId();
    }

    private Visit createVisit(Fixture f, LocalDate visitDate) {
        return visitRepository.save(Visit.builder()
                .member(f.member())
                .providerId(f.provider().getId())
                .visitDate(visitDate)
                .status(VisitStatus.REGISTERED)
                .build());
    }

    private Fixture createFixture(BigDecimal amountLimit, Integer timesLimit, Integer daysLimit) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Employer employer = employerRepository.save(Employer.builder()
                .name("Concurrency Employer " + suffix).code("CONC-" + suffix).active(true).build());
        BenefitPolicy policy = policyRepository.save(BenefitPolicy.builder()
                .name("Concurrency Policy " + suffix).policyCode("POL-" + suffix)
                .employer(employer).annualLimit(new BigDecimal("50000"))
                .defaultCoveragePercent(80).startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusYears(1)).status(BenefitPolicyStatus.ACTIVE)
                .active(true).build());
        Member member = memberRepository.save(Member.builder()
                .fullName("Concurrent Member").barcode("BC-" + suffix)
                .nationalNumber("NAT-" + suffix).employer(employer)
                .benefitPolicy(policy).active(true).build());
        Provider provider = providerRepository.save(Provider.builder()
                .name("Concurrency Provider " + suffix).providerType(ProviderType.HOSPITAL)
                .licenseNumber("LIC-" + suffix).allowAllEmployers(true).active(true).build());
        MedicalCategory category = categoryRepository.save(MedicalCategory.builder()
                .code("CAT-" + suffix).name("Concurrency Category").active(true).build());
        MedicalService service = serviceRepository.save(MedicalService.builder()
                .code("SRV-" + suffix).name("Concurrent Service")
                .categoryId(category.getId()).active(true).build());
        ProviderContract contract = contractRepository.save(ProviderContract.builder()
                .contractCode("CON-" + suffix).contractNumber("NUM-" + suffix)
                .provider(provider).startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusYears(1)).status(ContractStatus.ACTIVE)
                .active(true).build());
        // Mirrors production: every contract-creating path must also create its
        // effective terms row. The resolver fails closed, so a contract saved
        // without terms makes claim creation impossible for that provider.
        termsService.ensureEffectiveTerms(contract, "TEST");
        pricingRepository.save(ProviderContractPricingItem.builder()
                .contract(contract).serviceCode(service.getCode()).serviceName(service.getName())
                .medicalCategory(category).basePrice(new BigDecimal("60"))
                .contractPrice(new BigDecimal("60")).active(true).build());
        BenefitPolicyRule rule = ruleRepository.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy).medicalCategory(category)
                .encounterType(EncounterType.OUTPATIENT).coveragePercent(80)
                .active(true).deleted(false).build());
        BenefitGroup group = groupRepository.save(BenefitGroup.builder()
                .policy(policy).code("GRP-" + suffix).nameAr("وعاء التزامن")
                .contextType(EncounterType.OUTPATIENT).aggregationMode(AggregationMode.SHARED)
                .active(true).build());
        BenefitLimitBucket bucket = bucketRepository.save(BenefitLimitBucket.builder()
                .policy(policy).benefitGroup(group).code("BUC-" + suffix).nameAr("سقف مشترك")
                .contextType(EncounterType.OUTPATIENT).amountLimit(amountLimit)
                .timesLimit(timesLimit).daysLimit(daysLimit)
                .periodType(LimitPeriodType.ANNUAL).countingMethod(CountingMethod.EACH_LINE)
                .consumptionBasis(ConsumptionBasis.COMPANY_SHARE)
                .benefitScopeType(com.waad.tba.modules.benefitpolicy.enums.BenefitScopeType.GROUP)
                .shared(true).active(true).build());
        ruleBucketRepository.save(BenefitRuleBucket.builder().rule(rule).bucket(bucket).build());
        return new Fixture(member, provider, service, bucket, policy, rule);
    }

    private record Fixture(Member member, Provider provider, MedicalService service,
                           BenefitLimitBucket bucket, BenefitPolicy policy, BenefitPolicyRule rule) {}
}
