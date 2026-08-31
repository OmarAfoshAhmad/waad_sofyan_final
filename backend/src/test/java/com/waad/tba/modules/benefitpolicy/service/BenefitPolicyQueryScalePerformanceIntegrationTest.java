package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.support.PostgresIntegrationTestBase;

import jakarta.persistence.EntityManagerFactory;

/**
 * P-10: the employer-facing benefit-policy paths do not degrade as the book
 * grows.
 *
 * 500 policies across 100 employers -- a realistic upper bound for a TPA's
 * book, the same scale Employer's own E-11 test used for the same reason
 * (there are never 30,000 employers or policies; that number belongs to
 * MEMBERS).
 *
 * A real N+1 was found and fixed while building this test, not merely
 * confirmed absent: {@code BenefitPolicyService.getSelectors()} called
 * {@code countByBenefitPolicyIdAndDeletedFalseAndActiveTrue} once PER
 * POLICY inside a stream `.map()`. Fixed with one bulk query
 * ({@code findPolicyIdsWithActiveUndeletedRules}) instead.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class BenefitPolicyQueryScalePerformanceIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private BenefitPolicyService benefitPolicyService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private com.waad.tba.modules.rbac.repository.UserRepository users;

    private static final int POLICY_COUNT = 500;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @BeforeEach
    void seedManyPoliciesAcrossManyEmployersAndAuthenticate() {
        String username = "polperf-" + suffix();
        users.save(com.waad.tba.modules.rbac.entity.User.builder()
                .username(username).password("x").fullName("Policy Performance Test")
                .email(username + "@waad.ly").userType("SUPER_ADMIN").active(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", java.util.List.of()));

        String s = suffix();
        for (int i = 0; i < POLICY_COUNT; i++) {
            long employerId = jdbc.queryForObject(
                    "INSERT INTO employers (code, name, active) VALUES (?, ?, true) RETURNING id",
                    Long.class, "POLPERF-" + s + "-" + i, "جهة أداء الوثائق " + s + " " + i);
            long policyId = jdbc.queryForObject(
                    "INSERT INTO benefit_policies (name, policy_code, employer_id, start_date, end_date,"
                            + " annual_limit, default_coverage_percent, status, active)"
                            + " VALUES (?, ?, ?, ?, ?, 50000.00, 100, 'DRAFT', true) RETURNING id",
                    Long.class, "وثيقة أداء " + s + " " + i, "POLPERF-POL-" + s + "-" + i, employerId,
                    LocalDate.now().minusDays(1), LocalDate.now().plusYears(1));
            // Every third policy has at least one active rule -- the
            // selector's "hasRules" must be right for a mixed book, not
            // just uniformly true or uniformly false.
            if (i % 3 == 0) {
                long categoryId = jdbc.queryForObject(
                        "INSERT INTO medical_categories (code, name, active) VALUES (?, ?, true) RETURNING id",
                        Long.class, "POLPERF-CAT-" + s + "-" + i, "فئة أداء " + i);
                jdbc.update("INSERT INTO benefit_policy_rules (benefit_policy_id, medical_category_id,"
                                + " encounter_type, claim_context_code, coverage_percent, inheritance_enabled, priority,"
                                + " requires_pre_approval, active, deleted, version, created_at)"
                                + " VALUES (?, ?, 'OUTPATIENT', 'OUTPATIENT', 80, false, 100, false, true, false, 0, now())",
                        policyId, categoryId);
            }
        }
    }

    @Test
    @DisplayName("the policy selector list costs a bounded number of queries regardless of book size")
    void selectorListStaysBoundedAgainstFiveHundredPolicies() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        long startedAt = System.nanoTime();

        var selectors = benefitPolicyService.getSelectors();

        long tookMillis = (System.nanoTime() - startedAt) / 1_000_000;
        long statementCount = statistics.getPrepareStatementCount();

        assertThat(selectors.size()).isGreaterThanOrEqualTo(POLICY_COUNT);
        // One query for the policies, one bulk query for which of them have
        // rules, and a handful more from @BatchSize(100) batching the eager
        // excludedCategoryCodes collection (500 policies / 100 = 5 batches)
        // -- a small constant that grows with the batch size, not with the
        // row count. Before either fix this was 501 (one COUNT per policy)
        // and then 502 (also one collection SELECT per policy) -- the
        // difference a real N+1 makes once the book is large enough to see.
        System.out.println("[P-10] getSelectors() against " + POLICY_COUNT + " policies: "
                + statementCount + " statements, " + tookMillis + " ms");
        assertThat(statementCount)
                .as("getSelectors() must not issue one query per policy (%d ms, %d statements, %d policies)",
                        tookMillis, statementCount, selectors.size())
                .isLessThanOrEqualTo(10);
    }

    /**
     * Bounded, not exactly equal. {@code excludedCategoryCodes}' {@code
     * @BatchSize(100)} groups the collection fetch by however many distinct,
     * not-yet-loaded policy ids are pending at flush time -- which page
     * boundary a given id falls on can shift that grouping by one batch
     * either way. A real N+1 would show up as roughly +20 statements (one
     * per row on the page), not a difference of two or three; that is the
     * property this test guards, not bit-for-bit identical counts.
     */
    @Test
    @DisplayName("listing a page of policies costs roughly the same whether the book holds 20 or 500")
    void listingQueryCountIsBoundedAcrossTheBook() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        var firstPage = benefitPolicyService.findManagementPage(
                true, null, null, "", PageRequest.of(0, 20));
        assertThat(firstPage.getContent()).hasSize(20);
        long forFirstPage = statistics.getPrepareStatementCount();

        statistics.clear();
        var latePage = benefitPolicyService.findManagementPage(
                true, null, null, "", PageRequest.of(POLICY_COUNT / 20 - 1, 20));
        assertThat(latePage.getContent()).isNotEmpty();
        long forLatePage = statistics.getPrepareStatementCount();

        System.out.println("[P-10] policy list page: " + forFirstPage
                + " statements first page, " + forLatePage + " statements late page");
        assertThat(Math.abs(forLatePage - forFirstPage))
                .as("a late page of %d policies must not scale with book size the way an N+1 would "
                        + "(%d vs %d statements) -- a real per-row read would differ by roughly the page size (20)",
                        POLICY_COUNT, forFirstPage, forLatePage)
                .isLessThanOrEqualTo(5);
    }
}
