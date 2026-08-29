package com.waad.tba.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.service.LimitBalanceReader;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.member.service.MemberEmployerResolver;
import com.waad.tba.modules.member.service.MemberPolicyResolver;

/**
 * One member, two employers, two policies, two service dates.
 *
 * This is the test the whole dated model exists for, and the one the system
 * cannot be called integrated without. Every other temporal test proves a
 * piece: that an assignment has a half-open window, that a resolver reads it,
 * that a ledger row belongs to a policy. This proves they compose -- that a
 * decision taken about a date in the past still resolves to the employer, the
 * policy and the balance that applied on that date, after the member has moved
 * on.
 *
 * The failure it guards against is not a crash. It is a claim from March being
 * measured against the ceiling of the policy the member joined in July, which
 * produces a number that looks entirely reasonable and is wrong -- and which
 * nobody discovers until a reconciliation months later.
 *
 * The timeline:
 *
 *   Jan 1 .......... M joins Employer A on Policy A (ceiling 40,000)
 *   Feb ............ M consumes 30,000 under Policy A
 *   Jul 1 .......... M moves to Employer B on Policy B (ceiling 60,000)
 *   Aug ............ M consumes 10,000 under Policy B
 *
 * What must hold afterwards:
 *
 *   a February service date resolves to Employer A, Policy A, ceiling 40,000,
 *   30,000 committed, 10,000 remaining
 *
 *   an August service date resolves to Employer B, Policy B, ceiling 60,000,
 *   10,000 committed, 50,000 remaining
 *
 *   and neither consumption appears in the other's balance
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class MemberMovesBetweenEmployersJointTemporalIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MemberRepository members;
    @Autowired private MemberEmployerResolver employerResolver;
    @Autowired private MemberPolicyResolver policyResolver;
    @Autowired private LimitBalanceReader limitBalanceReader;

    private static final int YEAR = LocalDate.now().getYear();
    private static final LocalDate YEAR_START = LocalDate.of(YEAR, 1, 1);
    private static final LocalDate YEAR_END = LocalDate.of(YEAR, 12, 31);

    /** The instant the member moves. Half-open: A ends here, B begins here. */
    private static final LocalDate MOVE_DATE = LocalDate.of(YEAR, 7, 1);
    private static final LocalDate BEFORE_THE_MOVE = LocalDate.of(YEAR, 2, 15);
    private static final LocalDate AFTER_THE_MOVE = LocalDate.of(YEAR, 8, 15);

    private long employerA;
    private long employerB;
    private long policyA;
    private long policyB;
    private long memberId;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @BeforeEach
    void buildTheTimeline() {
        String s = suffix();
        employerA = employer("A-" + s);
        employerB = employer("B-" + s);
        policyA = policy(employerA, "POL-A-" + s, "40000.00");
        policyB = policy(employerB, "POL-B-" + s, "60000.00");

        // The member's CURRENT pointers say B. That is the point: every
        // assertion about February below has to come from the dated
        // assignments, not from these.
        memberId = jdbc.queryForObject(
                "INSERT INTO members (full_name, card_number, employer_id, benefit_policy_id, status, active)"
                        + " VALUES ('عضو انتقل بين جهتين', ?, ?, ?, 'ACTIVE', true) RETURNING id",
                Long.class, "JT-" + s, employerB, policyB);

        // [Jan 1, Jul 1) with A, [Jul 1, ...) with B -- half-open, no overlap
        // and no gap.
        assignEmployer(employerA, YEAR_START, MOVE_DATE);
        assignEmployer(employerB, MOVE_DATE, null);
        assignPolicy(policyA, YEAR_START, MOVE_DATE);
        assignPolicy(policyB, MOVE_DATE, null);

        spend(policyA, "30000.00");
        spend(policyB, "10000.00");
    }

    @Test
    @DisplayName("a service date before the move resolves to the employer, policy and balance that applied then")
    void theOldServiceDateStillResolvesToTheOldContext() {
        Member member = members.findById(memberId).orElseThrow();

        assertThat(employerResolver.resolveFor(member, BEFORE_THE_MOVE).orElseThrow().getId())
                .as("February belongs to Employer A, whatever the member's pointer says now")
                .isEqualTo(employerA);
        assertThat(policyResolver.resolveFor(member, BEFORE_THE_MOVE).orElseThrow().getId())
                .isEqualTo(policyA);

        var ceiling = limitBalanceReader.readGeneralCeiling(
                memberId, policyA, new BigDecimal("40000.00"), YEAR_START, YEAR_END, null);

        assertThat(ceiling.annualLimit()).isEqualByComparingTo("40000.00");
        assertThat(ceiling.committed())
                .as("only what was consumed under Policy A")
                .isEqualByComparingTo("30000.00");
        assertThat(ceiling.actualRemaining()).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("a service date after the move resolves to the new context, unreduced by the old one")
    void theNewServiceDateResolvesToTheNewContext() {
        Member member = members.findById(memberId).orElseThrow();

        assertThat(employerResolver.resolveFor(member, AFTER_THE_MOVE).orElseThrow().getId())
                .isEqualTo(employerB);
        assertThat(policyResolver.resolveFor(member, AFTER_THE_MOVE).orElseThrow().getId())
                .isEqualTo(policyB);

        var ceiling = limitBalanceReader.readGeneralCeiling(
                memberId, policyB, new BigDecimal("60000.00"), YEAR_START, YEAR_END, null);

        assertThat(ceiling.annualLimit()).isEqualByComparingTo("60000.00");
        assertThat(ceiling.committed())
                .as("the 30,000 spent under Policy A is not this policy's consumption")
                .isEqualByComparingTo("10000.00");
        assertThat(ceiling.actualRemaining())
                .as("a new employer's cover starts whole; the previous one's spending does not follow")
                .isEqualByComparingTo("50000.00");
    }

    @Test
    @DisplayName("the move date itself: half-open, so it belongs to B and not to both")
    void theBoundaryDayBelongsToExactlyOneContext() {
        Member member = members.findById(memberId).orElseThrow();

        // The day A's window ends is the day B's begins. Exactly one of them
        // may claim it, or a claim on that date has two answers.
        assertThat(employerResolver.resolveFor(member, MOVE_DATE).orElseThrow().getId())
                .as("the end date of a half-open window is outside it")
                .isEqualTo(employerB);
        assertThat(policyResolver.resolveFor(member, MOVE_DATE).orElseThrow().getId())
                .isEqualTo(policyB);

        assertThat(employerResolver.resolveFor(member, MOVE_DATE.minusDays(1)).orElseThrow().getId())
                .as("and the day before it is still inside")
                .isEqualTo(employerA);
        assertThat(policyResolver.resolveFor(member, MOVE_DATE.minusDays(1)).orElseThrow().getId())
                .isEqualTo(policyA);
    }

    @Test
    @DisplayName("consuming under the new policy does not move the old policy's balance")
    void newConsumptionDoesNotMutateTheOldContext() {
        var beforeMore = limitBalanceReader.readGeneralCeiling(
                memberId, policyA, new BigDecimal("40000.00"), YEAR_START, YEAR_END, null);

        spend(policyB, "25000.00");

        var afterMore = limitBalanceReader.readGeneralCeiling(
                memberId, policyA, new BigDecimal("40000.00"), YEAR_START, YEAR_END, null);

        assertThat(afterMore.committed())
                .as("Policy A's history is closed; spending under B cannot rewrite it")
                .isEqualByComparingTo(beforeMore.committed());
        assertThat(afterMore.actualRemaining()).isEqualByComparingTo(beforeMore.actualRemaining());

        var newCeiling = limitBalanceReader.readGeneralCeiling(
                memberId, policyB, new BigDecimal("60000.00"), YEAR_START, YEAR_END, null);
        assertThat(newCeiling.committed()).isEqualByComparingTo("35000.00");
    }

    // ── fixture ────────────────────────────────────────────────────────────

    private long employer(String code) {
        return jdbc.queryForObject(
                "INSERT INTO employers (code, name, active) VALUES (?, ?, true) RETURNING id",
                Long.class, "JT-" + code, "جهة " + code);
    }

    private long policy(long employerId, String code, String annualLimit) {
        return jdbc.queryForObject(
                "INSERT INTO benefit_policies (name, policy_code, employer_id, start_date, end_date,"
                        + " annual_limit, default_coverage_percent, status)"
                        + " VALUES (?, ?, ?, ?, ?, ?, 100, 'ACTIVE') RETURNING id",
                Long.class, "وثيقة " + code, code, employerId, YEAR_START, YEAR_END,
                new BigDecimal(annualLimit));
    }

    private void assignEmployer(long employerId, LocalDate from, LocalDate to) {
        jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id,"
                + " assignment_start_date, assignment_end_date, assignment_reason, assignment_source)"
                + " VALUES (?, ?, ?, ?, 'تجهيز اختبار زمني مشترك', 'MANUAL')",
                memberId, employerId, from, to);
    }

    private void assignPolicy(long policyId, LocalDate from, LocalDate to) {
        jdbc.update("INSERT INTO member_policy_assignments (member_id, policy_id,"
                + " assignment_start_date, assignment_end_date, assignment_source)"
                + " VALUES (?, ?, ?, ?, 'MANUAL')",
                memberId, policyId, from, to);
    }

    /**
     * A committed general-scope ledger row under one policy.
     *
     * Written as an opening-balance movement rather than through a claim: the
     * property under test is which POLICY a consumption belongs to, and
     * building a claim, its lines and its calculation version would add a
     * great deal of fixture without adding anything the assertions read.
     */
    private void spend(long underPolicyId, String amount) {
        Long batch = jdbc.queryForObject(
                "INSERT INTO member_opening_balance_batches (batch_reference, reason, performed_by,"
                        + " source_reference) VALUES (?, 'رصيد افتتاحي للاختبار', 'tester', 'joint temporal')"
                        + " RETURNING id",
                Long.class, "JT-BATCH-" + UUID.randomUUID());

        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, period_start,"
                + " period_end, approved_amount, times_consumed, calculation_version, idempotency_key,"
                + " status, source_type, limit_scope, opening_batch_id, created_at)"
                + " VALUES (?, ?, ?, ?, ?, 0, 1, ?, 'COMMITTED', 'OPENING_IMPORT', 'POLICY_GENERAL', ?, now())",
                underPolicyId, memberId, YEAR_START, YEAR_END, new BigDecimal(amount),
                "JT-C-" + suffix(), batch);
    }
}
