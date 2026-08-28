package com.waad.tba.modules.benefitpolicy.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * A page of members must cost a page's worth of work, not a ledger's worth.
 *
 * The query-count tests prove the number of round trips does not grow with the
 * page. They say nothing about what each round trip reads, and the two are
 * easy to confuse: a single query that scans the whole ledger is still a
 * single query, and it is the one that shows up as a p95 that gets worse every
 * month while the code stops changing.
 *
 * The suspect was the reversal join. Every balance read in this system nets
 * reversals through a subquery that aggregates REVERSED rows without
 * mentioning the members being asked about, so nothing in the SQL says the
 * work belongs to the page.
 *
 * Measured, it does. Adding 4,000 reversals for members the page never names
 * moved the rows touched from 120 to 153 -- Postgres pushes the join down
 * through idx_bucket_consumption_reversal_of (V173) and aggregates only the
 * rows the page asks about, rather than materialising the whole thing. The
 * shape reads alarming and behaves correctly, which is the reason to keep
 * measuring it: nothing in the query text would tell you if that stopped
 * being true, and the index it depends on lives in a migration five years of
 * commits away.
 *
 * Rows, not milliseconds: rows are what the planner reports and what scales,
 * and a wall-clock threshold on a shared machine is a flaky test wearing a
 * performance test's clothes.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class GeneralCeilingReadScalesWithThePageIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private JdbcTemplate jdbc;

    private static final int PAGE = 30;

    /** Reversals belonging to members nobody asked about. */
    private static final int UNRELATED_REVERSALS = 4000;

    private static final String YEAR_START = "DATE_TRUNC('year', CURRENT_DATE)::date";
    private static final String YEAR_END =
            "(DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date";

    private long policyId;
    private long employerId;
    private long batchId;
    private long correctionBatchId;
    private final List<Long> pageMembers = new ArrayList<>();

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 10);
    }

    @BeforeEach
    void seed() {
        String s = suffix();
        employerId = jdbc.queryForObject("INSERT INTO employers (name, code, active) VALUES "
                + "('Scale Employer " + s + "', 'SCL-" + s + "', true) RETURNING id", Long.class);
        policyId = jdbc.queryForObject("INSERT INTO benefit_policies (name, policy_code, employer_id, "
                + "start_date, end_date, annual_limit, default_coverage_percent, status, active) VALUES "
                + "('Scale Policy', ?, ?, " + YEAR_START + ", " + YEAR_END
                + ", ?, 80, 'ACTIVE', true) RETURNING id",
                Long.class, "SCL-" + s, employerId, new BigDecimal("60000.00"));
        batchId = jdbc.queryForObject(
                "INSERT INTO member_opening_balance_batches (batch_reference, reason, performed_by, "
                        + "source_reference) VALUES (?, 'رصيد افتتاحي', 'tester', 'scale') RETURNING id",
                Long.class, "SCL-BATCH-" + suffix());
        // V188 refuses an opening correction recorded in the batch it corrects,
        // so the reversals get a batch of their own -- as they would in life.
        correctionBatchId = jdbc.queryForObject(
                "INSERT INTO member_opening_balance_batches (batch_reference, reason, performed_by, "
                        + "source_reference) VALUES (?, 'تصحيح رصيد افتتاحي', 'tester', 'scale') "
                        + "RETURNING id", Long.class, "SCL-FIX-" + suffix());

        for (int i = 0; i < PAGE; i++) {
            pageMembers.add(memberWithSpending());
        }
    }

    private long memberWithSpending() {
        Long memberId = jdbc.queryForObject("INSERT INTO members (full_name, card_number, employer_id, "
                + "benefit_policy_id, status, active) VALUES ('Scale Member', ?, ?, ?, 'ACTIVE', true) "
                + "RETURNING id", Long.class, "SCL-" + suffix(), employerId, policyId);
        jdbc.update("INSERT INTO member_policy_assignments (member_id, policy_id, "
                + "assignment_start_date, assignment_source) VALUES (?, ?, CURRENT_DATE - 60, 'MANUAL')",
                memberId, policyId);
        jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id, "
                + "assignment_start_date, assignment_reason, assignment_source) "
                + "VALUES (?, ?, CURRENT_DATE - 60, 'fixture', 'MANUAL')", memberId, employerId);
        commit(memberId, "100.00");
        return memberId;
    }

    private long commit(long memberId, String amount) {
        return jdbc.queryForObject("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, "
                + "period_start, period_end, approved_amount, times_consumed, calculation_version, "
                + "idempotency_key, status, source_type, limit_scope, opening_batch_id, created_at) "
                + "VALUES (?, ?, " + YEAR_START + ", " + YEAR_END + ", ?, 0, 1, ?, 'COMMITTED', "
                + "'OPENING_IMPORT', 'POLICY_GENERAL', ?, now()) RETURNING id",
                Long.class, policyId, memberId, new BigDecimal(amount), "SCL-C-" + suffix(), batchId);
    }

    /**
     * A reversal for a member the page never mentions. Appended the way the
     * ledger actually records one: a REVERSED row pointing at the original.
     */
    private void unrelatedReversal(long memberId, long originalId) {
        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, period_start, "
                + "period_end, approved_amount, times_consumed, calculation_version, idempotency_key, "
                + "status, source_type, limit_scope, opening_batch_id, reversal_of_id, "
                + "reversal_reason, created_at) VALUES (?, ?, " + YEAR_START + ", " + YEAR_END
                + ", ?, 0, 1, ?, 'REVERSED', 'OPENING_IMPORT', 'POLICY_GENERAL', ?, ?, "
                + "'OPENING_CORRECTION', now())",
                policyId, memberId, new BigDecimal("10.00"), "SCL-X-" + suffix(),
                correctionBatchId, originalId);
    }

    private static final String BULK_COMMITTED = """
            select c.member_id, c.policy_id,
                   coalesce(sum(c.approved_amount - coalesce(r.reversed_amount, 0)), 0) as amount
              from benefit_bucket_consumptions c
              left join (
                    select reversal_of_id, sum(approved_amount) as reversed_amount
                      from benefit_bucket_consumptions
                     where status = 'REVERSED' and reversal_of_id is not null
                     group by reversal_of_id
              ) r on r.reversal_of_id = c.id
             where c.member_id in (%s)
               and c.limit_scope = 'POLICY_GENERAL'
               and c.status = 'COMMITTED'
               and c.period_start = ?
               and c.period_end is not distinct from cast(? as date)
             group by c.member_id, c.policy_id
            """;

    /** Total rows the planner actually touched, summed over every plan node. */
    private long rowsTouched() {
        LocalDate start = LocalDate.of(LocalDate.now().getYear(), 1, 1);
        LocalDate end = LocalDate.of(LocalDate.now().getYear(), 12, 31);
        // Ids are this test's own primary keys, inlined so the plan is the one
        // the page produces rather than one shaped by a parameter placeholder.
        String ids = pageMembers.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(", "));

        String plan = jdbc.queryForObject(
                "EXPLAIN (ANALYZE, FORMAT JSON) " + String.format(BULK_COMMITTED, ids),
                String.class, start, end);
        return sumActualRows(plan);
    }

    /**
     * Adds up every "Actual Rows" in the JSON plan. Crude on purpose -- the
     * figure is only ever compared against itself under a different ledger
     * size, so what matters is that it counts the same things both times.
     */
    private static long sumActualRows(String plan) {
        long total = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"Actual Rows\"\\s*:\\s*(\\d+)").matcher(plan);
        while (m.find()) {
            total += Long.parseLong(m.group(1));
        }
        return total;
    }

    @Test
    @DisplayName("reversals belonging to other members do not enter the page's read")
    void unrelatedReversalsDoNotEnterThePagesRead() {
        long before = rowsTouched();

        long otherMemberId = memberWithSpending();
        for (int i = 0; i < UNRELATED_REVERSALS; i++) {
            unrelatedReversal(otherMemberId, commit(otherMemberId, "10.00"));
        }
        jdbc.execute("ANALYZE benefit_bucket_consumptions");

        long after = rowsTouched();

        // Measured growth is ~33 rows on a base of ~120. The threshold is a
        // tenth of what was added, which leaves room for planner variance
        // while still failing by two orders of magnitude if the aggregate ever
        // goes back to being computed over the whole ledger.
        assertThat(after - before)
                .as("the page did not change, so its read must not have either. Growth "
                        + "proportional to what was added means the reversal aggregate is "
                        + "computed over the whole ledger on every page load -- one query, "
                        + "and a p95 that gets worse every month while the code stops "
                        + "changing. Rows before=%d after=%d, with %d unrelated reversals "
                        + "added.", before, after, UNRELATED_REVERSALS)
                .isLessThan(UNRELATED_REVERSALS / 10);
    }
}
