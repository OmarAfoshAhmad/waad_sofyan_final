package com.waad.tba.modules.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.member.dto.CurrentGeneralLimitSummary;
import com.waad.tba.modules.member.dto.CurrentGeneralLimitSummary.Mode;
import com.waad.tba.modules.member.dto.MemberFinancialSummaryDto;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * One member, one date, two screens that must not disagree.
 *
 * Before this, the members list read the ledger for both halves of the
 * ceiling while the member financial summary read only what had been
 * committed -- and then clamped the result at zero. A clerk checking
 * eligibility therefore saw money already held by an approved
 * pre-authorization offered as available, and saw an overspent member as
 * exactly spent. The two figures are separate here on purpose, and both keep
 * their sign.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberBalanceAxisIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private MemberFinancialSummaryService summaryService;
    @Autowired private MemberLimitOverviewService overviewService;
    @Autowired private JdbcTemplate jdbc;

    private static final BigDecimal CEILING = new BigDecimal("60000.00");

    private static final String YEAR_START = "DATE_TRUNC('year', CURRENT_DATE)::date";
    private static final String YEAR_END =
            "(DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date";

    private long policyId;
    private long employerId;
    private Long assignmentId;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 10);
    }

    @BeforeEach
    void seed() {
        String s = suffix();
        employerId = jdbc.queryForObject("INSERT INTO employers (name, code, active) VALUES "
                + "('Axis Employer " + s + "', 'AX-" + s + "', true) RETURNING id", Long.class);
        policyId = newPolicy(CEILING);
    }

    private long newPolicy(BigDecimal annualLimit) {
        return jdbc.queryForObject("INSERT INTO benefit_policies (name, policy_code, employer_id, "
                + "start_date, end_date, annual_limit, default_coverage_percent, status, active) VALUES "
                + "('Axis Policy', ?, ?, " + YEAR_START + ", " + YEAR_END
                + ", ?, 80, 'ACTIVE', true) RETURNING id",
                Long.class, "AX-" + suffix(), employerId, annualLimit);
    }

    private long memberOn(long onPolicyId) {
        Long memberId = jdbc.queryForObject("INSERT INTO members (full_name, card_number, employer_id, "
                + "benefit_policy_id, status, active) VALUES ('Axis Member', ?, ?, ?, 'ACTIVE', true) "
                + "RETURNING id", Long.class, "AX-" + suffix(), employerId, onPolicyId);
        assignmentId = jdbc.queryForObject(
                "INSERT INTO member_policy_assignments (member_id, policy_id, assignment_start_date, "
                        + "assignment_source) VALUES (?, ?, CURRENT_DATE - 60, 'MANUAL') RETURNING id",
                Long.class, memberId, onPolicyId);
        jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id, "
                + "assignment_start_date, assignment_reason, assignment_source) "
                + "VALUES (?, ?, CURRENT_DATE - 60, 'fixture', 'MANUAL')", memberId, employerId);
        return memberId;
    }

    private long member() {
        return memberOn(policyId);
    }

    private void spend(long memberId, String amount) {
        Long batchId = jdbc.queryForObject(
                "INSERT INTO member_opening_balance_batches (batch_reference, reason, performed_by, "
                        + "source_reference) VALUES (?, 'opening balance for test', 'tester', 'test') "
                        + "RETURNING id", Long.class, "AX-BATCH-" + suffix());
        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, period_start, "
                + "period_end, approved_amount, times_consumed, calculation_version, idempotency_key, "
                + "status, source_type, limit_scope, opening_batch_id, created_at) VALUES (?, ?, "
                + YEAR_START + ", " + YEAR_END + ", ?, 0, 1, ?, 'COMMITTED', 'OPENING_IMPORT', "
                + "'POLICY_GENERAL', ?, now())",
                policyId, memberId, new BigDecimal(amount), "AX-C-" + suffix(), batchId);
    }

    /** Returns the ledger row id of the hold, so a release can point at it. */
    private long hold(long memberId, String amount) {
        Long preauthId = jdbc.queryForObject("INSERT INTO pre_authorizations (member_id, policy_id, "
                + "status, request_date, created_at, updated_at) VALUES (?, ?, 'APPROVED', now(), "
                + "now(), now()) RETURNING id", Long.class, memberId, policyId);
        Long lineId = jdbc.queryForObject("INSERT INTO pre_authorization_lines (pre_authorization_id, "
                + "requested_amount) VALUES (?, ?) RETURNING id", Long.class, preauthId,
                new BigDecimal(amount));
        return jdbc.queryForObject("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, "
                + "preauth_id, preauth_line_id, member_policy_assignment_id, period_start, period_end, "
                + "approved_amount, times_consumed, calculation_version, idempotency_key, status, "
                + "source_type, limit_scope, created_at) VALUES (?, ?, ?, ?, ?, " + YEAR_START + ", "
                + YEAR_END + ", ?, 0, 1, ?, 'RESERVED', 'PREAUTH', 'POLICY_GENERAL', now()) RETURNING id",
                Long.class, policyId, memberId, preauthId, lineId, assignmentId,
                new BigDecimal(amount), "AX-R-" + suffix());
    }

    /**
     * Releases a hold the way the ledger actually does it: a REVERSED row
     * pointing at the original, never an update or a delete.
     */
    private void release(long memberId, long reservationId, String amount) {
        Long preauthId = jdbc.queryForObject(
                "SELECT preauth_id FROM benefit_bucket_consumptions WHERE id = ?", Long.class, reservationId);
        Long lineId = jdbc.queryForObject(
                "SELECT preauth_line_id FROM benefit_bucket_consumptions WHERE id = ?", Long.class, reservationId);
        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, preauth_id, "
                + "preauth_line_id, member_policy_assignment_id, period_start, period_end, "
                + "approved_amount, times_consumed, calculation_version, idempotency_key, status, "
                + "source_type, limit_scope, reversal_of_id, reversal_reason, created_at) VALUES "
                + "(?, ?, ?, ?, ?, " + YEAR_START + ", " + YEAR_END + ", ?, 0, 1, ?, 'REVERSED', "
                + "'PREAUTH', 'POLICY_GENERAL', ?, 'PREAUTH_RELEASE', now())",
                policyId, memberId, preauthId, lineId, assignmentId, new BigDecimal(amount),
                "AX-X-" + suffix(), reservationId);
    }

    private MemberFinancialSummaryDto summaryOf(long memberId) {
        return summaryService.getFinancialSummary(memberId);
    }

    @Test
    void spendingAndHoldingProduceTwoDifferentRemainingFigures() {
        long memberId = member();
        spend(memberId, "10000.00");
        hold(memberId, "5000.00");

        MemberFinancialSummaryDto summary = summaryOf(memberId);

        assertThat(summary.getAnnualLimit()).isEqualByComparingTo("60000.00");
        assertThat(summary.getLimitConsumedAmount()).isEqualByComparingTo("10000.00");
        assertThat(summary.getReservedAmount()).isEqualByComparingTo("5000.00");
        assertThat(summary.getActualRemaining())
                .as("the accounting view: a hold is not a payment")
                .isEqualByComparingTo("50000.00");
        assertThat(summary.getReservableAvailable())
                .as("the decision view: a hold is not available to commit again")
                .isEqualByComparingTo("45000.00");
    }

    @Test
    void theFigureAClaimIsJudgedAgainstIsTheReservableOne() {
        long memberId = member();
        spend(memberId, "10000.00");
        hold(memberId, "5000.00");

        MemberFinancialSummaryDto summary = summaryOf(memberId);

        assertThat(summary.getReservableAvailable())
                .as("45,000, not 50,000 -- committing the held 5,000 a second time is "
                        + "exactly what the hold exists to prevent")
                .isEqualByComparingTo("45000.00");
        assertThat(summary.getReservableAvailable())
                .isNotEqualByComparingTo(summary.getActualRemaining());
    }

    @Test
    void placingAHoldLeavesActualRemainingUntouched() {
        long memberId = member();
        spend(memberId, "10000.00");
        BigDecimal before = summaryOf(memberId).getActualRemaining();

        hold(memberId, "5000.00");
        MemberFinancialSummaryDto after = summaryOf(memberId);

        assertThat(after.getActualRemaining())
                .as("nothing was consumed, so nothing changed on the consumption axis")
                .isEqualByComparingTo(before);
        assertThat(after.getReservableAvailable())
                .as("only what may still be committed moved")
                .isEqualByComparingTo("45000.00");
    }

    @Test
    void releasingAHoldRaisesOnlyTheReservableFigure() {
        long memberId = member();
        spend(memberId, "10000.00");
        long reservationId = hold(memberId, "5000.00");
        BigDecimal actualBefore = summaryOf(memberId).getActualRemaining();

        release(memberId, reservationId, "5000.00");
        MemberFinancialSummaryDto after = summaryOf(memberId);

        assertThat(after.getReservedAmount()).isEqualByComparingTo("0.00");
        assertThat(after.getReservableAvailable())
                .as("the released money returns to what may be committed")
                .isEqualByComparingTo("50000.00");
        assertThat(after.getActualRemaining())
                .as("and the consumption axis never moved")
                .isEqualByComparingTo(actualBefore);
    }

    @Test
    void anOverspendStaysNegativeOnBothFigures() {
        long memberId = member();
        spend(memberId, "65000.00");

        MemberFinancialSummaryDto summary = summaryOf(memberId);

        assertThat(summary.getActualRemaining())
                .as("clamping this at zero made an overspent member look exactly spent")
                .isEqualByComparingTo("-5000.00");
        assertThat(summary.getReservableAvailable()).isEqualByComparingTo("-5000.00");
    }

    @Test
    void anUnlimitedPolicyCarriesNoInventedCeiling() {
        long unlimitedPolicyId = newPolicy(new BigDecimal("0.00"));
        long memberId = memberOn(unlimitedPolicyId);

        MemberFinancialSummaryDto summary = summaryOf(memberId);

        assertThat(summary.getCeilingMode()).isEqualTo(Mode.UNLIMITED);
        assertThat(summary.getActualRemaining()).isNull();
        assertThat(summary.getReservableAvailable()).isNull();
        assertThat(summary.getUtilizationPercent()).isNull();
    }

    @Test
    void aMemberWithNoDatedPolicyGetsNullsRatherThanZeroes() {
        Long memberId = jdbc.queryForObject("INSERT INTO members (full_name, card_number, "
                + "employer_id, benefit_policy_id, status, active) VALUES ('No Policy', ?, ?, ?, "
                + "'ACTIVE', true) RETURNING id",
                Long.class, "AX-NP-" + suffix(), employerId, policyId);
        jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id, "
                + "assignment_start_date, assignment_reason, assignment_source) "
                + "VALUES (?, ?, CURRENT_DATE - 60, 'fixture', 'MANUAL')", memberId, employerId);

        MemberFinancialSummaryDto summary = summaryOf(memberId);

        assertThat(summary.getCeilingMode()).isEqualTo(Mode.NOT_CONFIGURED);
        assertThat(summary.getActualRemaining())
                .as("a zero here reads as a spent ceiling to whoever is deciding")
                .isNull();
        assertThat(summary.getReservableAvailable()).isNull();
        assertThat(summary.getLimitConsumedAmount()).isNull();
    }

    @Test
    void everySummaryCarriesTheDateAndInstantItWasReadFor() {
        long memberId = member();

        MemberFinancialSummaryDto summary = summaryOf(memberId);

        assertThat(summary.getAsOfDate()).isEqualTo(LocalDate.now());
        assertThat(summary.getReadAt())
                .as("without it, two screens differing because a claim landed between "
                        + "their reads looks like one of them being wrong")
                .isNotNull();
    }

    @Test
    void theSummaryAndTheListReportTheSameFiguresForTheSameDate() {
        long memberId = member();
        spend(memberId, "10000.00");
        hold(memberId, "5000.00");

        MemberFinancialSummaryDto summary = summaryOf(memberId);
        Map<Long, CurrentGeneralLimitSummary> list = overviewService.summariesFor(List.of(memberId));
        CurrentGeneralLimitSummary row = list.get(memberId);

        // Asserted against stated numbers as well as against each other: two
        // surfaces agreeing on the wrong figure is exactly what this change
        // was fixing, and a bare equality check would have passed then too.
        assertThat(summary.getReservableAvailable()).isEqualByComparingTo("45000.00");
        assertThat(row.reservableAvailable()).isEqualByComparingTo("45000.00");

        assertThat(summary.getAsOfDate()).isEqualTo(row.asOfDate());
        assertThat(summary.getLimitConsumedAmount()).isEqualByComparingTo(row.committed());
        assertThat(summary.getReservedAmount()).isEqualByComparingTo(row.reserved());
        assertThat(summary.getActualRemaining()).isEqualByComparingTo(row.actualRemaining());
        assertThat(summary.getReservableAvailable()).isEqualByComparingTo(row.reservableAvailable());
        assertThat(summary.getCeilingMode()).isEqualTo(row.mode());
    }

    @Test
    void thePaymentFieldsAreNullAndSayWhy() {
        long memberId = member();
        spend(memberId, "10000.00");

        MemberFinancialSummaryDto summary = summaryOf(memberId);

        assertThat(summary.getTotalPaid())
                .as("it summed approvedAmount over claims whose STATUS was SETTLED, which "
                        + "is a status change reported as a disbursement")
                .isNull();
        assertThat(summary.getClaimPaymentAttribution())
                .as("and the reason is stated, so a reader is not left to conclude the "
                        + "member simply has no payments")
                .isEqualTo(MemberFinancialSummaryDto.ClaimPaymentAttribution.NOT_SUPPORTED);
        assertThat(summary.getTotalApproved()).isNull();
        assertThat(summary.getTotalClaimed()).isNull();
    }
}
