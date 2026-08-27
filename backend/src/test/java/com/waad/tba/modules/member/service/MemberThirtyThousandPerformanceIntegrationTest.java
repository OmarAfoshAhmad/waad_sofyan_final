package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
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
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

import jakarta.persistence.EntityManagerFactory;

@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class MemberThirtyThousandPerformanceIntegrationTest extends PostgresIntegrationTestBase {

    private static final long PAGE_P95_BUDGET_MS = 500;
    private static final long SEARCH_P95_BUDGET_MS = 250;

    @Autowired private UnifiedMemberService service;
    @Autowired private UnifiedSearchService unifiedSearchService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listingThirtyThousandMembersKeepsTheDeclaredQueryAndLatencyBudget() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        long employerId = jdbc.queryForObject(
                "insert into employers(code,name) values (?,?) returning id",
                Long.class, "P30-" + tag, "Thirty Thousand " + tag);
        long policyId = jdbc.queryForObject("""
                insert into benefit_policies(name,policy_code,employer_id,annual_limit,
                  default_coverage_percent,start_date,end_date,status,active)
                values (?,?,?,60000,80,current_date-30,current_date+365,'ACTIVE',true)
                returning id
                """, Long.class, "P30 Policy " + tag, "P30-POL-" + tag, employerId);

        jdbc.update("""
                insert into members(employer_id,benefit_policy_id,full_name,card_number,barcode,status,active)
                select ?, ?, 'Performance Member ' || g,
                       ? || '-' || g, ? || '-' || g, 'ACTIVE', true
                from generate_series(1,30000) g
                """, employerId, policyId, "P30C-" + tag, "P30C-" + tag);
        jdbc.execute("analyze members");
        actingAsSuperAdmin(tag);

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        for (int i = 0; i < 2; i++) {
            service.getAllMembers(PageRequest.of(0, 200), employerId, "ACTIVE", null);
        }

        List<Long> samplesMs = new ArrayList<>();
        long page200Queries = -1;
        for (int i = 0; i < 5; i++) {
            statistics.clear();
            long started = System.nanoTime();
            var page = service.getAllMembers(PageRequest.of(0, 200), employerId, "ACTIVE", null);
            samplesMs.add((System.nanoTime() - started) / 1_000_000);

            assertThat(page.getContent()).hasSize(200);
            assertThat(page.getTotalElements()).isEqualTo(30_000);
            assertThat(statistics.getPrepareStatementCount())
                    .as("queries=%s entityFetches=%s collectionFetches=%s hql=%s",
                            statistics.getPrepareStatementCount(), statistics.getEntityFetchCount(),
                            statistics.getCollectionFetchCount(), List.of(statistics.getQueries()))
                    .isLessThanOrEqualTo(4);
            page200Queries = statistics.getPrepareStatementCount();
        }

        statistics.clear();
        var ten = service.getAllMembers(PageRequest.of(0, 10), employerId, "ACTIVE", null);
        long page10Queries = statistics.getPrepareStatementCount();
        assertThat(ten.getContent()).hasSize(10);
        assertThat(page200Queries).as("query count must not grow with page size").isEqualTo(page10Queries);

        Collections.sort(samplesMs);
        long p95 = samplesMs.get(samplesMs.size() - 1);
        assertThat(p95).as("local PostgreSQL page-200 p95 samples=%s", samplesMs)
                .isLessThan(PAGE_P95_BUDGET_MS);

        for (int i = 0; i < 2; i++) unifiedSearchService.search("Performance Member", employerId);
        List<Long> searchSamplesMs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            statistics.clear();
            long started = System.nanoTime();
            var results = unifiedSearchService.search("Performance Member", employerId);
            searchSamplesMs.add((System.nanoTime() - started) / 1_000_000);
            assertThat(results).hasSize(20);
            assertThat(statistics.getPrepareStatementCount())
                    .as("fixed search queries=%s hql=%s", statistics.getPrepareStatementCount(),
                            List.of(statistics.getQueries()))
                    .isLessThanOrEqualTo(3);
        }
        Collections.sort(searchSamplesMs);
        long searchP95 = searchSamplesMs.get(searchSamplesMs.size() - 1);
        assertThat(searchP95).as("local PostgreSQL SQL-limited name search p95 samples=%s", searchSamplesMs)
                .isLessThan(SEARCH_P95_BUDGET_MS);

        System.out.printf("[member-30k] pageSamplesMs=%s pageP95=%d pageQueries=%d; searchSamplesMs=%s searchP95=%d%n",
                samplesMs, p95, page200Queries, searchSamplesMs, searchP95);

        List<String> plan = jdbc.queryForList("""
                explain (analyze, buffers, format text)
                select m.id from members m
                where m.employer_id = ? and m.active = true
                order by m.id limit 200
                """, String.class, employerId);
        assertThat(plan).anyMatch(line -> line.contains("Limit"));
        assertThat(String.join(System.lineSeparator(), plan)).contains("actual time=");
    }

    private void actingAsSuperAdmin(String tag) {
        String username = "p30-admin-" + tag;
        users.save(User.builder().username(username).password("x").fullName("Performance Admin")
                .email(username + "@waad.ly").userType("SUPER_ADMIN").active(true).build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "x", List.of()));
    }
}
