package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicyRule;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitPolicyRuleRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.medicaltaxonomy.repository.MedicalCategoryRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * P-08: two overlapping DRAFT policies for the same employer race to
 * activate() at the same instant.
 *
 * activate() is read-check-write against the same fact from both threads:
 * "is there already an active policy covering these dates for this
 * employer?" ({@code checkOverlappingActivePolicy}). It takes
 * {@code pg_advisory_xact_lock} keyed on the employer id
 * (BenefitPolicyRepository.acquireTransactionLock,
 * BenefitPolicyService.java:460) BEFORE running that check, so the two
 * activations serialise on the employer rather than both reading "no active
 * policy yet" and both writing ACTIVE.
 *
 * Two real threads, released past a barrier at the same instant, each in its
 * own transaction -- a sequential/simulated call proves nothing about a race.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class BenefitPolicyActivationConcurrencyIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private BenefitPolicyService benefitPolicyService;
    @Autowired private BenefitPolicyRepository policies;
    @Autowired private BenefitPolicyRuleRepository rules;
    @Autowired private EmployerRepository employers;
    @Autowired private MedicalCategoryRepository categories;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private UserRepository users;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private SecurityContext adminContext;

    @BeforeEach
    void authenticateAsAnAdministrator() {
        String username = "policy-race-" + suffix();
        users.save(User.builder().username(username).password("x").fullName("Race Test")
                .email(username + "@waad.ly").userType("SUPER_ADMIN").active(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", java.util.List.of()));
        adminContext = SecurityContextHolder.getContext();
    }

    // Repeated within one JVM/Spring context: the race is timing-dependent,
    // one trial proves nothing either way.
    @RepeatedTest(20)
    @DisplayName("two overlapping draft policies race to activate: exactly one succeeds")
    void overlappingActivationsRaceToExactlyOneOutcome() throws Exception {
        String s = suffix();
        Employer employer = employers.save(Employer.builder()
                .name("جهة سباق التفعيل " + s).code("POLRACE-" + s).active(true).build());

        long policyAId = draftActivatablePolicy(employer, s, "A");
        long policyBId = draftActivatablePolicy(employer, s, "B");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> activatingA = pool.submit(() -> activateUnderRace(policyAId, ready, start));
            Future<String> activatingB = pool.submit(() -> activateUnderRace(policyBId, ready, start));

            ready.await(10, TimeUnit.SECONDS);
            start.countDown();

            String outcomeA = activatingA.get(30, TimeUnit.SECONDS);
            String outcomeB = activatingB.get(30, TimeUnit.SECONDS);

            BenefitPolicyStatus statusA = policies.findById(policyAId).orElseThrow().getStatus();
            BenefitPolicyStatus statusB = policies.findById(policyBId).orElseThrow().getStatus();

            // The property under test: never both active, and never neither
            // refused/activated in a way that contradicts the persisted state.
            long activeCount = (statusA == BenefitPolicyStatus.ACTIVE ? 1 : 0)
                    + (statusB == BenefitPolicyStatus.ACTIVE ? 1 : 0);
            assertThat(activeCount)
                    .as("exactly one of the two overlapping policies may end up ACTIVE (A=%s, B=%s)",
                            statusA, statusB)
                    .isEqualTo(1);

            long successCount = (outcomeA.equals("ACTIVATED") ? 1 : 0) + (outcomeB.equals("ACTIVATED") ? 1 : 0);
            assertThat(successCount)
                    .as("exactly one activate() call may report success (A=%s, B=%s)", outcomeA, outcomeB)
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private String activateUnderRace(long policyId, CountDownLatch ready, CountDownLatch start) {
        SecurityContextHolder.setContext(adminContext);
        ready.countDown();
        try {
            start.await();
            new TransactionTemplate(transactionManager)
                    .executeWithoutResult(tx -> benefitPolicyService.activate(policyId));
            return "ACTIVATED";
        } catch (Exception refused) {
            return "REFUSED";
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private long draftActivatablePolicy(Employer employer, String suffix, String tag) {
        BenefitPolicy policy = policies.save(BenefitPolicy.builder()
                .name("وثيقة سباق التفعيل " + tag + " " + suffix)
                .policyCode("POLRACE-" + tag + "-" + suffix)
                .employer(employer)
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusYears(1))
                .annualLimit(new BigDecimal("10000"))
                .defaultCoveragePercent(80)
                .status(BenefitPolicyStatus.DRAFT)
                .active(true)
                .build());
        MedicalCategory category = categories.save(MedicalCategory.builder()
                .code("POLRACE-CAT-" + tag + "-" + suffix)
                .name("فئة سباق التفعيل " + tag)
                .active(true)
                .build());
        rules.save(BenefitPolicyRule.builder()
                .benefitPolicy(policy)
                .medicalCategory(category)
                .coveragePercent(80)
                .active(true)
                .deleted(false)
                .build());
        return policy.getId();
    }
}
