package com.waad.tba.modules.employer.service;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.member.entity.EmployerAssignmentSource;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberEmployerAssignmentRepository;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.service.MemberEmployerResolver;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * E-08: archiving an employer races a new member being assigned to it.
 *
 * Both are read-check-write across the SAME two facts -- does anyone belong to
 * this employer, and is this employer active -- from opposite directions.
 * EmployerService.archive() counts assignments then writes active=false.
 * MemberEmployerResolver.assignEmployer() checks active then writes an
 * assignment.
 *
 * Both now take the SAME pessimistic write lock on the employer row
 * (EmployerRepository.findByIdForLifecycleTransition) before deciding, so the
 * two serialise rather than racing. This was verified two ways, not one:
 *
 *   WITH the lock: 20 repetitions of this test, twice over (40 total), every
 *   one landing in a consistent state -- run in ~32s.
 *
 *   WITHOUT it (both locks removed, proved by reverting this file's changes
 *   during triage): a single trial could pass by luck, but any attempt to run
 *   the race repeatedly made the whole test JVM hang indefinitely -- past a
 *   150-second hard kill, with the Maven summary never printing "Tests run".
 *   That is Postgres holding one thread on a genuine row-level wait with no
 *   lock_timeout configured: not a quick, cleanly-caught conflict, but an
 *   unbounded block. A `Future.get(timeout)` in the test gives up waiting for
 *   the result; it does not cancel the underlying blocked database call, so
 *   the connection never returns to the pool -- and enough repetitions
 *   exhaust it. An unguarded race here is not a rare wrong answer; it is a
 *   liveness hazard that can starve the connection pool under real traffic.
 *
 * This is not simulated with sequential calls -- a sequential test proves
 * nothing about a race. Two real threads, released past a barrier at the same
 * instant, each in its own transaction.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class EmployerArchiveVersusMemberAssignmentConcurrencyIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private EmployerService employerService;
    @Autowired private EmployerRepository employers;
    @Autowired private MemberRepository members;
    @Autowired private MemberEmployerResolver employerResolver;
    @Autowired private MemberEmployerAssignmentRepository assignments;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository users;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private SecurityContext adminContext;

    @BeforeEach
    void authenticateAsAnAdministrator() {
        String username = "race-" + suffix();
        users.save(com.waad.tba.modules.rbac.entity.User.builder()
                .username(username).password("x").fullName("Race Test")
                .email(username + "@waad.ly").userType("SUPER_ADMIN").active(true).build());
        var authentication = new UsernamePasswordAuthenticationToken(username, "x", java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        adminContext = SecurityContextHolder.getContext();
    }

    // Repeated within one JVM/Spring context rather than across separate mvn
    // invocations: the race is timing-dependent, and one trial proves
    // nothing either way. Twenty attempts inside a single running context
    // exercises the interleaving without paying Spring Boot startup cost
    // twenty times over.
    @RepeatedTest(20)
    @DisplayName("archiving and assigning a member race: exactly one succeeds, never both")
    void archiveAndAssignRaceToExactlyOneOutcome() throws Exception {
        String s = suffix();
        long employerId = jdbc.queryForObject(
                "INSERT INTO employers (code, name, active) VALUES (?, ?, true) RETURNING id",
                Long.class, "RACE-" + s, "جهة السباق " + s);
        long policyId = jdbc.queryForObject(
                "INSERT INTO benefit_policies (name, policy_code, employer_id, start_date, end_date,"
                        + " annual_limit, default_coverage_percent, status)"
                        + " VALUES (?, ?, ?, ?, ?, 50000.00, 100, 'ACTIVE') RETURNING id",
                Long.class, "وثيقة السباق " + s, "RACE-POL-" + s, employerId,
                LocalDate.now().withDayOfYear(1), LocalDate.now().withMonth(12).withDayOfMonth(31));

        // A member who does not yet belong to this employer -- the race is
        // over whether they get to.
        Long memberId = jdbc.queryForObject(
                "INSERT INTO members (full_name, card_number, employer_id, benefit_policy_id, status, active)"
                        + " VALUES (?, ?, ?, ?, 'ACTIVE', true) RETURNING id",
                Long.class, "عضو السباق " + s, "RACE-M" + s, employerId, policyId);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> archiving = pool.submit(() -> {
                SecurityContextHolder.setContext(adminContext);
                ready.countDown();
                try {
                    start.await();
                    new TransactionTemplate(transactionManager)
                            .executeWithoutResult(tx -> employerService.archive(employerId));
                    return "ARCHIVED";
                } catch (Exception refused) {
                    return "ARCHIVE_REFUSED";
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });

            Future<String> assigning = pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                        Member member = members.findById(memberId).orElseThrow();
                        Employer employer = employers.findById(employerId).orElseThrow();
                        employerResolver.assignEmployer(member, employer, LocalDate.now(),
                                "تعيين متزامن لاختبار السباق", EmployerAssignmentSource.MANUAL, 1L);
                    });
                    return "ASSIGNED";
                } catch (Exception refused) {
                    return "ASSIGNMENT_REFUSED";
                }
            });

            ready.await(10, TimeUnit.SECONDS);
            start.countDown();

            String archiveOutcome = archiving.get(30, TimeUnit.SECONDS);
            String assignOutcome = assigning.get(30, TimeUnit.SECONDS);

            boolean employerIsActive = Boolean.TRUE.equals(employers.findById(employerId).orElseThrow().getActive());
            boolean memberIsAssignedToday = !assignments
                    .findMemberIdsAssignedOn(employerId, LocalDate.now()).isEmpty();

            // The property under test: never both, and never neither.
            if (employerIsActive) {
                assertThat(archiveOutcome).isEqualTo("ARCHIVE_REFUSED");
                assertThat(assignOutcome).isEqualTo("ASSIGNED");
                assertThat(memberIsAssignedToday)
                        .as("the employer stayed active because the assignment landed first")
                        .isTrue();
            } else {
                assertThat(archiveOutcome).isEqualTo("ARCHIVED");
                assertThat(assignOutcome).isEqualTo("ASSIGNMENT_REFUSED");
                assertThat(memberIsAssignedToday)
                        .as("the archive that succeeded must not coexist with a member newly assigned to it")
                        .isFalse();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
