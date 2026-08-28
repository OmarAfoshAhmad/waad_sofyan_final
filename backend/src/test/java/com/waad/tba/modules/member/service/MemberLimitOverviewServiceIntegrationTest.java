package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.member.dto.CurrentGeneralLimitSummary;
import com.waad.tba.modules.member.dto.CurrentGeneralLimitSummary.AlertStatus;
import com.waad.tba.modules.member.dto.CurrentGeneralLimitSummary.Mode;
import com.waad.tba.support.PostgresIntegrationTestBase;

import jakarta.persistence.EntityManagerFactory;

/**
 * What a members-list row actually receives.
 *
 * Two properties matter more than the arithmetic. The cost of a page must not
 * depend on how many rows it holds, nor on how many distinct policies those
 * rows sit under -- otherwise the column becomes unusable at exactly the page
 * sizes people use. And every outcome without figures has to arrive labelled
 * as such: a row that renders "0" because the read failed tells someone
 * deciding on treatment that the ceiling is spent.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class MemberLimitOverviewServiceIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberLimitOverviewService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private static final BigDecimal CEILING = new BigDecimal("60000.00");

    /**
     * annual_limit is NOT NULL since V33, so a policy with no monetary ceiling
     * is written as zero -- the same value BenefitPolicyCoverageService already
     * reads as "no ceiling".
     */
    private static final BigDecimal NO_CEILING = new BigDecimal("0.00");

    private static final String YEAR_START = "DATE_TRUNC('year', CURRENT_DATE)::date";
    private static final String YEAR_END =
            "(DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date";

    private long policyId;
    private final Map<Long, Long> assignmentByMember = new HashMap<>();

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 10);
    }

    @BeforeEach
    void seed() {
        policyId = newPolicy(CEILING);
    }

    /** A policy under an employer of its own, so policies never collide. */
    private long newPolicy(BigDecimal annualLimit) {
        return newPolicy(annualLimit, "ACTIVE");
    }

    private long newPolicy(BigDecimal annualLimit, String status) {
        String s = suffix();
        Long employerId = jdbc.queryForObject("INSERT INTO employers (name, code, active) VALUES "
                + "('Overview Employer " + s + "', 'OVW-" + s + "', true) RETURNING id", Long.class);
        return jdbc.queryForObject("INSERT INTO benefit_policies (name, policy_code, employer_id, "
                + "start_date, end_date, annual_limit, default_coverage_percent, status, active) VALUES "
                + "('Overview Policy', ?, ?, " + YEAR_START + ", " + YEAR_END
                + ", ?, 80, ?, true) RETURNING id",
                Long.class, "OVW-" + s, employerId, annualLimit, status);
    }

    private long newEmployer() {
        String s = suffix();
        return jdbc.queryForObject("INSERT INTO employers (name, code, active) VALUES "
                + "('Other Employer " + s + "', 'OTH-" + s + "', true) RETURNING id", Long.class);
    }

    private long employerOf(long ofPolicyId) {
        return jdbc.queryForObject("SELECT employer_id FROM benefit_policies WHERE id = ?",
                Long.class, ofPolicyId);
    }

    /** A member with a dated assignment to the given policy, covering today. */
    private long memberOn(long onPolicyId) {
        Long memberId = jdbc.queryForObject("INSERT INTO members (full_name, card_number, employer_id, "
                + "benefit_policy_id, status, active) VALUES ('Overview Member', ?, ?, ?, 'ACTIVE', true) "
                + "RETURNING id", Long.class, "OVW-" + suffix(), employerOf(onPolicyId), onPolicyId);
        Long assignmentId = jdbc.queryForObject(
                "INSERT INTO member_policy_assignments (member_id, policy_id, assignment_start_date, "
                        + "assignment_source) VALUES (?, ?, CURRENT_DATE - 60, 'MANUAL') RETURNING id",
                Long.class, memberId, onPolicyId);
        assignEmployer(memberId, employerOf(onPolicyId));
        assignmentByMember.put(memberId, assignmentId);
        return memberId;
    }

    /**
     * The dated employer the policy assignment is checked against. Without it
     * a member resolves as EMPLOYER_MISMATCH -- which is the point: a policy
     * belonging to an employer the member is not on may not price their care.
     */
    private void assignEmployer(long memberId, long employerId) {
        jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id, "
                + "assignment_start_date, assignment_reason, assignment_source) "
                + "VALUES (?, ?, CURRENT_DATE - 60, 'fixture', 'MANUAL')", memberId, employerId);
    }

    private long member() {
        return memberOn(policyId);
    }

    /**
     * A member carrying the legacy benefit_policy_id pointer but no dated
     * assignment row -- the data gap V171 backfilled, and the shape that any
     * fallback-to-the-pointer implementation would silently paper over.
     */
    private long memberWithPointerButNoAssignment() {
        long employerId = employerOf(policyId);
        Long memberId = jdbc.queryForObject("INSERT INTO members (full_name, card_number, employer_id, "
                + "benefit_policy_id, status, active) VALUES ('Unassigned Member', ?, ?, ?, 'ACTIVE', true) "
                + "RETURNING id", Long.class, "OVW-NA-" + suffix(), employerId, policyId);
        assignEmployer(memberId, employerId);
        return memberId;
    }

    private void spend(long memberId, long underPolicyId, String amount) {
        Long batchId = jdbc.queryForObject(
                "INSERT INTO member_opening_balance_batches (batch_reference, reason, performed_by, "
                        + "source_reference) VALUES (?, 'opening balance for test', 'tester', 'test') "
                        + "RETURNING id", Long.class, "OVW-BATCH-" + suffix());
        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, period_start, "
                + "period_end, approved_amount, times_consumed, calculation_version, idempotency_key, "
                + "status, source_type, limit_scope, opening_batch_id, created_at) VALUES (?, ?, "
                + YEAR_START + ", " + YEAR_END + ", ?, 0, 1, ?, 'COMMITTED', 'OPENING_IMPORT', "
                + "'POLICY_GENERAL', ?, now())",
                underPolicyId, memberId, new BigDecimal(amount), "OVW-C-" + suffix(), batchId);
    }

    private void spend(long memberId, String amount) {
        spend(memberId, policyId, amount);
    }

    private void hold(long memberId, long underPolicyId, String amount) {
        Long preauthId = jdbc.queryForObject("INSERT INTO pre_authorizations (member_id, policy_id, "
                + "status, request_date, created_at, updated_at) VALUES (?, ?, 'APPROVED', now(), "
                + "now(), now()) RETURNING id", Long.class, memberId, underPolicyId);
        Long lineId = jdbc.queryForObject("INSERT INTO pre_authorization_lines (pre_authorization_id, "
                + "requested_amount) VALUES (?, ?) RETURNING id", Long.class, preauthId,
                new BigDecimal(amount));
        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, preauth_id, "
                + "preauth_line_id, member_policy_assignment_id, period_start, period_end, "
                + "approved_amount, times_consumed, calculation_version, idempotency_key, status, "
                + "source_type, limit_scope, created_at) VALUES (?, ?, ?, ?, ?, " + YEAR_START + ", "
                + YEAR_END + ", ?, 0, 1, ?, 'RESERVED', 'PREAUTH', 'POLICY_GENERAL', now())",
                underPolicyId, memberId, preauthId, lineId, assignmentByMember.get(memberId),
                new BigDecimal(amount), "OVW-R-" + suffix());
    }

    private void hold(long memberId, String amount) {
        hold(memberId, policyId, amount);
    }

    private CurrentGeneralLimitSummary summaryOf(long memberId) {
        return service.summariesFor(List.of(memberId)).get(memberId);
    }

    @Test
    void theHeadlineFigureIsWhatMayStillBeCommitted() {
        long memberId = member();
        spend(memberId, "10000.00");
        hold(memberId, "5000.00");

        CurrentGeneralLimitSummary summary = summaryOf(memberId);

        assertThat(summary.mode()).isEqualTo(Mode.FOUND);
        assertThat(summary.reservableAvailable())
                .as("held money is not available to commit again")
                .isEqualByComparingTo("45000.00");
        assertThat(summary.actualRemaining())
                .as("but it has not been spent either, and reconciliation needs that figure")
                .isEqualByComparingTo("50000.00");
        assertThat(summary.utilizationPercent())
                .as("utilisation measures consumption, not unavailability")
                .isEqualByComparingTo("16.7");
        assertThat(summary.alertStatus()).isEqualTo(AlertStatus.NORMAL);
    }

    @Test
    void everySummaryCarriesTheDateItAnswersForAndTheInstantItWasRead() {
        long memberId = member();

        CurrentGeneralLimitSummary summary = summaryOf(memberId);

        assertThat(summary.asOfDate()).isEqualTo(LocalDate.now());
        assertThat(summary.readAt())
                .as("the list and the drawer are separate reads; without this a claim "
                        + "approved between them looks like one screen being wrong")
                .isNotNull();
    }

    @Test
    void aLargeHoldRaisesTheAlarmBeforeAnyOfItIsSpent() {
        long memberId = member();
        hold(memberId, "55000.00");

        CurrentGeneralLimitSummary summary = summaryOf(memberId);

        assertThat(summary.utilizationPercent())
                .as("nothing has been consumed yet")
                .isEqualByComparingTo("0.0");
        assertThat(summary.alertStatus())
                .as("but only 5000 of 60000 may still be committed, which is the fact "
                        + "someone approving treatment needs")
                .isEqualTo(AlertStatus.CRITICAL);
    }

    @Test
    void twentyPercentLeftIsAWarningAndTenPercentIsCritical() {
        long warned = member();
        spend(warned, "48000.00");
        assertThat(summaryOf(warned).alertStatus()).isEqualTo(AlertStatus.WARNING);

        long critical = member();
        spend(critical, "54000.00");
        assertThat(summaryOf(critical).alertStatus()).isEqualTo(AlertStatus.CRITICAL);
    }

    @Test
    void aFullySpentCeilingIsExhaustedRatherThanNormalAtZero() {
        long memberId = member();
        spend(memberId, "60000.00");

        CurrentGeneralLimitSummary summary = summaryOf(memberId);

        assertThat(summary.reservableAvailable()).isEqualByComparingTo("0.00");
        assertThat(summary.alertStatus()).isEqualTo(AlertStatus.EXHAUSTED);
    }

    @Test
    void spendingBeyondTheCeilingIsExceededAndKeepsTheOverageVisible() {
        long memberId = member();
        spend(memberId, "65000.00");

        CurrentGeneralLimitSummary summary = summaryOf(memberId);

        assertThat(summary.alertStatus()).isEqualTo(AlertStatus.EXCEEDED);
        assertThat(summary.actualRemaining())
                .as("the overage stays a negative number; clamping it to zero would hide it")
                .isEqualByComparingTo("-5000.00");
    }

    @Test
    void aPolicyWithoutACeilingIsUnlimitedAndCarriesNoInventedFigures() {
        long unlimitedPolicyId = newPolicy(NO_CEILING);
        long memberId = memberOn(unlimitedPolicyId);
        spend(memberId, unlimitedPolicyId, "9000.00");

        CurrentGeneralLimitSummary summary = summaryOf(memberId);

        assertThat(summary.mode()).isEqualTo(Mode.UNLIMITED);
        assertThat(summary.alertStatus()).isEqualTo(AlertStatus.UNLIMITED);
        assertThat(summary.committed())
                .as("consumption is real even where nothing caps it")
                .isEqualByComparingTo("9000.00");
        assertThat(summary.limit()).isNull();
        assertThat(summary.reservableAvailable()).isNull();
        assertThat(summary.utilizationPercent())
                .as("there is no proportion of nothing")
                .isNull();
    }

    @Test
    void aMemberWithNoAssignmentIsNotConfiguredRatherThanFullySpent() {
        long memberId = memberWithPointerButNoAssignment();

        CurrentGeneralLimitSummary summary = summaryOf(memberId);

        assertThat(summary.mode())
                .as("the legacy pointer is display-only; reading a ceiling off it would "
                        + "grant coverage without knowing when it started applying")
                .isEqualTo(Mode.NOT_CONFIGURED);
        assertThat(summary.limit()).isNull();
        assertThat(summary.committed())
                .as("no dated policy means no figures at all, not zeroes that read as "
                        + "a spent ceiling")
                .isNull();
    }

    @Test
    void anAssignmentToAPolicyNoLongerInForceReportsNoCeilingRatherThanAStaleOne() {
        long suspendedPolicyId = newPolicy(CEILING, "SUSPENDED");
        long memberId = memberOn(suspendedPolicyId);
        spend(memberId, suspendedPolicyId, "10000.00");

        CurrentGeneralLimitSummary summary = summaryOf(memberId);

        assertThat(summary.mode())
                .as("assignments are left open-ended and closed only when a new one "
                        + "starts, so without this check one would keep answering with "
                        + "a policy that stopped applying")
                .isEqualTo(Mode.NOT_CONFIGURED);
        assertThat(summary.limit()).isNull();
    }

    @Test
    void aPolicyBelongingToAnotherEmployerIsRefusedRatherThanUsedToPriceCare() {
        Long memberId = jdbc.queryForObject("INSERT INTO members (full_name, card_number, "
                + "employer_id, benefit_policy_id, status, active) VALUES ('Moved Member', ?, ?, ?, "
                + "'ACTIVE', true) RETURNING id",
                Long.class, "OVW-MM-" + suffix(), employerOf(policyId), policyId);
        jdbc.update("INSERT INTO member_policy_assignments (member_id, policy_id, "
                + "assignment_start_date, assignment_source) VALUES (?, ?, CURRENT_DATE - 60, 'MANUAL')",
                memberId, policyId);
        // The member sits with a different employer on this date than the one
        // whose policy they are assigned to.
        assignEmployer(memberId, newEmployer());

        CurrentGeneralLimitSummary summary = summaryOf(memberId);

        assertThat(summary.mode())
                .as("a mismatch says the data is wrong, not that the member has no cover; "
                        + "the two demand different responses from whoever reads the screen")
                .isEqualTo(Mode.UNAVAILABLE);
        assertThat(summary.reservableAvailable()).isNull();
    }

    @Test
    void theDateIsTheServersAndTestsPinItOnlyThroughTheClock() {
        long memberId = member();

        CurrentGeneralLimitSummary summary = service
                .summariesFor(List.of(memberId), Clock.systemDefaultZone())
                .get(memberId);

        assertThat(summary.asOfDate()).isEqualTo(LocalDate.now(ZoneId.systemDefault()));
    }

    @Test
    void aPageCostsAFixedNumberOfQueriesWhateverItHoldsAndHoweverManyPoliciesItSpans() {
        List<Long> five = membersOnDistinctPolicies(5);
        List<Long> thirty = membersOnDistinctPolicies(30);

        long forFive = statementsFor(five);
        long forThirty = statementsFor(thirty);

        assertThat(forFive)
                .as("policy assignments, policies in force, employer assignments, "
                        + "annual limits, committed, reserved")
                .isEqualTo(6L);
        assertThat(forThirty)
                .as("six times the rows and six times the policies, the same six queries")
                .isEqualTo(forFive);
    }

    /**
     * Every member on a policy of their own. A page whose members all share one
     * policy would not notice a per-policy read, and the members list is
     * exactly where they do not share it: a page is filtered down to a single
     * employer far less often than it is not.
     */
    private List<Long> membersOnDistinctPolicies(int count) {
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long ownPolicyId = newPolicy(CEILING);
            long memberId = memberOn(ownPolicyId);
            spend(memberId, ownPolicyId, "100.00");
            hold(memberId, ownPolicyId, "50.00");
            memberIds.add(memberId);
        }
        return memberIds;
    }

    private long statementsFor(List<Long> memberIds) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        Map<Long, CurrentGeneralLimitSummary> summaries = service.summariesFor(memberIds);

        assertThat(summaries).hasSize(memberIds.size());
        assertThat(summaries.values()).allMatch(s -> s.mode() == Mode.FOUND);
        return statistics.getPrepareStatementCount();
    }
}
