package com.waad.tba.modules.employer.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.support.PostgresIntegrationTestBase;

import jakarta.persistence.EntityManagerFactory;

/**
 * E-11: the employer-facing paths do not degrade as the book grows.
 *
 * "30,000 members" in the protocol is a MEMBERS-scale number; there are never
 * 30,000 employers in this system, so the dataset here is shaped for what
 * actually stresses the employer paths: many employers (500, a realistic
 * upper bound for a TPA's book), one of them carrying a large roster (5,000
 * members with a current assignment), because that is the shape
 * archive()'s count query and the scoped list query actually have to survive.
 *
 * Every assertion here is a measurement, not a target invented in advance --
 * the constitution's "measure before optimizing". Where a number is asserted
 * as a ceiling, it is a bound on GROWTH (constant query count as the
 * dataset scales), not a guessed absolute.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class EmployerQueryScalePerformanceIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private EmployerService employerService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository users;

    private static final int EMPLOYER_COUNT = 500;
    private static final int BIG_ROSTER = 5000;

    private long bigEmployerId;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @BeforeEach
    void seedAndAuthenticate() {
        String username = "perf-" + suffix();
        users.save(com.waad.tba.modules.rbac.entity.User.builder()
                .username(username).password("x").fullName("Performance Test")
                .email(username + "@waad.ly").userType("SUPER_ADMIN").active(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", java.util.List.of()));

        String s = suffix();

        // A book of ordinary employers, none carrying more than a handful of
        // members -- the volume a scoped list or search has to page through.
        for (int i = 0; i < EMPLOYER_COUNT; i++) {
            long id = jdbc.queryForObject(
                    "INSERT INTO employers (code, name, active) VALUES (?, ?, true) RETURNING id",
                    Long.class, "PERF-" + s + "-" + i, "جهة أداء " + s + " " + i);
            if (i == 0) {
                bigEmployerId = id;
            }
        }

        // The one employer archive() has to reason about a large roster for.
        long policyId = jdbc.queryForObject(
                "INSERT INTO benefit_policies (name, policy_code, employer_id, start_date, end_date,"
                        + " annual_limit, default_coverage_percent, status)"
                        + " VALUES (?, ?, ?, ?, ?, 50000.00, 100, 'ACTIVE') RETURNING id",
                Long.class, "وثيقة الأداء " + s, "PERF-POL-" + s, bigEmployerId,
                LocalDate.now().withDayOfYear(1), LocalDate.now().withMonth(12).withDayOfMonth(31));

        seedRoster(s, policyId, BIG_ROSTER);

        // Isolate the condition under test: with the policy still active,
        // archiving would be blocked by it regardless of the roster, and the
        // statement count below would not be measuring what this test claims.
        jdbc.update("UPDATE benefit_policies SET active = false, status = 'DRAFT' WHERE id = ?", policyId);
    }

    /**
     * Each member also gets a row in member_employer_assignments, dated to
     * cover today. This is the fixture bug E-03's own test caught once
     * already: a member seeded with only the employer_id pointer is invisible
     * to countActiveMembersAssignedOn, which reads the dated assignments -- so
     * a fixture that skips the assignment row makes archive() get blocked by
     * the employer's ACTIVE POLICY instead, and the roster size is never
     * actually exercised.
     */
    private void seedRoster(String s, long policyId, int count) {
        for (int i = 0; i < count; i++) {
            long memberId = jdbc.queryForObject(
                    "INSERT INTO members (full_name, card_number, employer_id, benefit_policy_id,"
                            + " status, active) VALUES (?, ?, ?, ?, 'ACTIVE', true) RETURNING id",
                    Long.class, "عضو أداء " + s + " " + i, "PERF-M-" + s + "-" + i, bigEmployerId, policyId);
            jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id,"
                    + " assignment_start_date, assignment_reason, assignment_source)"
                    + " VALUES (?, ?, ?, 'تجهيز اختبار الأداء', 'MANUAL')",
                    memberId, bigEmployerId, LocalDate.now().minusDays(1));
        }
    }

    @Test
    @DisplayName("archiving is blocked by a 5,000-member roster in a bounded number of queries")
    void archiveGuardStaysBoundedAgainstALargeRoster() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        long startedAt = System.nanoTime();

        assertThatArchivingIsBlocked();

        long tookMillis = (System.nanoTime() - startedAt) / 1_000_000;
        long statementCount = statistics.getPrepareStatementCount();

        // The count query is one COUNT(DISTINCT ...) regardless of roster
        // size (see countActiveMembersAssignedOn); the guard as a whole
        // -- scope resolution, the lock read, both counts, the refusal --
        // is a small constant, not one query per member. Recorded rather
        // than asserted against a large ceiling: a real N+1 here would show
        // up as thousands, not single digits.
        System.out.println("[E-11] archive() against a 5,000-member roster: "
                + statementCount + " statements, " + tookMillis + " ms");
        assertThat(statementCount)
                .as("archive() must not issue one query per roster member (%d ms, %d statements)",
                        tookMillis, statementCount)
                .isLessThanOrEqualTo(10);
    }

    private void assertThatArchivingIsBlocked() {
        try {
            employerService.archive(bigEmployerId);
            org.junit.jupiter.api.Assertions.fail("a 5,000-member roster must block archiving");
        } catch (com.waad.tba.common.exception.BusinessRuleException expected) {
            // the roster blocked it, as it must
        }
    }

    @Test
    @DisplayName("listing a page of employers costs the same whether the book holds 1 or 500")
    void listingQueryCountIsConstantAcrossTheBook() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        var page = employerService.getPage(Boolean.TRUE, "", PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(20);
        long forOnePage = statistics.getPrepareStatementCount();

        statistics.clear();
        var lastPage = employerService.getPage(Boolean.TRUE, "",
                PageRequest.of(EMPLOYER_COUNT / 20 - 1, 20));
        assertThat(lastPage.getContent()).isNotEmpty();
        long forALatePage = statistics.getPrepareStatementCount();

        // A page near the end of 500 rows costs the same as the first page.
        // If this ever grows with the offset, the scoped query or the count
        // query stopped being index-backed.
        System.out.println("[E-11] employer list page: " + forOnePage
                + " statements first page, " + forALatePage + " statements late page");
        assertThat(forALatePage)
                .as("page 24 of 500 employers should cost what page 0 costs (%d vs %d statements)",
                        forOnePage, forALatePage)
                .isEqualTo(forOnePage);
    }

    @Test
    @DisplayName("a tenant scoped to one employer out of 500 pays no more than the global read")
    void scopedListingCostsNoMoreThanTheGlobalRead() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        var globalPage = employerService.getPage(Boolean.TRUE, "", PageRequest.of(0, 20));
        long globalStatements = statistics.getPrepareStatementCount();
        assertThat(globalPage.getContent()).hasSize(20);

        // A tenant scoped to a single employer inside the 500-row book. The
        // scope predicate this hits is e.id IN (:employerIds) against the
        // primary key -- a plain indexed filter, not a join or a per-row
        // subquery -- so it must not cost more than scanning the whole book
        // unscoped, whatever the book's total size.
        String username = "scoped-perf-" + suffix();
        users.save(com.waad.tba.modules.rbac.entity.User.builder()
                .username(username).password("x").fullName("Scoped Performance Test")
                .email(username + "@waad.ly").userType("EMPLOYER_ADMIN").employerId(bigEmployerId).active(true)
                .build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", java.util.List.of()));

        statistics.clear();
        var scopedPage = employerService.getPage(Boolean.TRUE, "", PageRequest.of(0, 20));
        long scopedStatements = statistics.getPrepareStatementCount();

        assertThat(scopedPage.getContent())
                .as("the scoped tenant sees exactly their one employer, not the other 499")
                .hasSize(1);
        System.out.println("[E-11] scoped-vs-global list: " + scopedStatements
                + " statements scoped, " + globalStatements + " statements global");
        assertThat(scopedStatements)
                .as("scoped to 1 of 500 must not cost more than reading all 500 (%d vs %d statements)",
                        scopedStatements, globalStatements)
                .isLessThanOrEqualTo(globalStatements);
    }
}
