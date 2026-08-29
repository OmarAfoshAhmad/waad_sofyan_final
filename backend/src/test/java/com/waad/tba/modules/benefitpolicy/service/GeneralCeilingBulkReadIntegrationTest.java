package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import com.waad.tba.modules.benefitpolicy.service.GeneralCeilingReading.Mode;
import com.waad.tba.support.PostgresIntegrationTestBase;

import jakarta.persistence.EntityManagerFactory;

/**
 * The general ceiling for a whole page, and the distinctions the column has to
 * be able to draw.
 *
 * Three of the four modes carry no figures at all. Rendering any of them as
 * 0.00 would tell someone deciding on treatment that the ceiling is spent,
 * when the truth might be that no ceiling exists, or that the read failed --
 * three different situations, one of which is not even about this member.
 *
 * Rows are inserted through JDBC rather than the entity builder, following the
 * pattern the reservation tests already use here: V174 constrains which entry
 * type each source may post and which links it must carry, so a RESERVED row
 * has to belong to a real pre-authorization line. Building fixtures that
 * satisfy those constraints is the point, not an obstacle -- a test that could
 * post a hold from nowhere would be testing a ledger this project does not
 * have.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class GeneralCeilingBulkReadIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private LimitBalanceReader reader;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private static final BigDecimal CEILING = new BigDecimal("60000.00");

    private long employerId;
    private long policyId;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 10);
    }

    private static final String PERIOD_START_SQL = "DATE_TRUNC('year', CURRENT_DATE)::date";
    private static final String PERIOD_END_SQL =
            "(DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date";

    private LocalDate periodStart() {
        return jdbc.queryForObject("SELECT " + PERIOD_START_SQL, LocalDate.class);
    }

    private LocalDate periodEnd() {
        return jdbc.queryForObject("SELECT " + PERIOD_END_SQL, LocalDate.class);
    }

    @BeforeEach
    void seed() {
        String s = suffix();
        employerId = jdbc.queryForObject("INSERT INTO employers (name, code, active) VALUES "
                + "('Ceiling Employer " + s + "', 'CEIL-" + s + "', true) RETURNING id", Long.class);
        policyId = insertPolicy("CEIL-" + s, CEILING);
    }

    private long insertPolicy(String code, BigDecimal annualLimit) {
        return jdbc.queryForObject("INSERT INTO benefit_policies (name, policy_code, employer_id, "
                + "start_date, end_date, annual_limit, default_coverage_percent, status, active) VALUES "
                + "('Ceiling Policy', ?, ?, " + PERIOD_START_SQL + ", " + PERIOD_END_SQL
                + ", ?, 80, 'ACTIVE', true) RETURNING id",
                Long.class, code, employerId, annualLimit);
    }

    /**
     * A member together with the dated policy assignment a hold has to name:
     * V-series constrains every PREAUTH row to carry the assignment it was
     * placed under, so a hold can always be traced to the policy that was in
     * force when it was made rather than the one in force when it is read.
     */
    private long insertMember() {
        Long memberId = jdbc.queryForObject("INSERT INTO members (full_name, card_number, employer_id, "
                + "benefit_policy_id, status, active) VALUES ('Ceiling Member', ?, ?, ?, 'ACTIVE', true) "
                + "RETURNING id", Long.class, "CEIL-" + suffix(), employerId, policyId);
        Long assignmentId = jdbc.queryForObject(
                "INSERT INTO member_policy_assignments (member_id, policy_id, assignment_start_date, "
                        + "assignment_source) VALUES (?, ?, CURRENT_DATE - 60, 'MANUAL') RETURNING id",
                Long.class, memberId, policyId);
        assignmentByMember.put(memberId, assignmentId);
        return memberId;
    }

    private final Map<Long, Long> assignmentByMember = new HashMap<>();

    /**
     * COMMITTED spending, posted as an opening import: that source needs no
     * claim links, but V-series constraints require it to name the batch it
     * arrived in, so an imported balance can always be traced to its import.
     */
    private void spend(long memberId, long underPolicyId, String amount) {
        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, period_start, "
                + "period_end, approved_amount, times_consumed, calculation_version, idempotency_key, "
                + "status, source_type, limit_scope, opening_batch_id, created_at) VALUES (?, ?, "
                + PERIOD_START_SQL + ", " + PERIOD_END_SQL + ", ?, 0, 1, ?, "
                + "'COMMITTED', 'OPENING_IMPORT', 'POLICY_GENERAL', ?, now())",
                underPolicyId, memberId, new BigDecimal(amount), "CEIL-C-" + suffix(), openingBatch());
    }

    private Long openingBatch() {
        return jdbc.queryForObject(
                "INSERT INTO member_opening_balance_batches (batch_reference, reason, performed_by, "
                        + "source_reference) VALUES (?, 'رصيد افتتاحي للاختبار', 'tester', "
                        + "'prior system export') RETURNING id",
                Long.class, "CEIL-BATCH-" + UUID.randomUUID());
    }

    /**
     * A hold, owned by a real pre-authorization line as V174 requires. Returns
     * the ledger row id so a release can point back at it.
     */
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
                + "source_type, limit_scope, created_at) VALUES (?, ?, ?, ?, ?, " + PERIOD_START_SQL
                + ", " + PERIOD_END_SQL + ", ?, 0, 1, ?, 'RESERVED', 'PREAUTH', 'POLICY_GENERAL', now()) "
                + "RETURNING id", Long.class, policyId, memberId, preauthId, lineId,
                assignmentByMember.get(memberId), new BigDecimal(amount), "CEIL-R-" + suffix());
    }

    /** Releases part or all of a hold, linked through reversal_of_id. */
    private void release(long memberId, long holdRowId, String amount) {
        Map<String, Object> source = jdbc.queryForMap(
                "SELECT preauth_id, preauth_line_id FROM benefit_bucket_consumptions WHERE id = ?",
                holdRowId);
        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, preauth_id, "
                + "preauth_line_id, member_policy_assignment_id, reversal_of_id, reversal_reason, "
                + "period_start, period_end, "
                + "approved_amount, times_consumed, calculation_version, idempotency_key, status, "
                + "source_type, limit_scope, created_at) VALUES (?, ?, ?, ?, ?, ?, 'PREAUTH_RELEASE', "
                + PERIOD_START_SQL + ", " + PERIOD_END_SQL + ", ?, 0, 1, ?, 'REVERSED', 'PREAUTH', "
                + "'POLICY_GENERAL', now())",
                policyId, memberId, source.get("preauth_id"), source.get("preauth_line_id"),
                assignmentByMember.get(memberId), holdRowId, new BigDecimal(amount),
                "CEIL-X-" + suffix());
    }

    private Map<Long, GeneralCeilingReading> read(List<Long> memberIds) {
        Map<Long, Long> policyByMember = new LinkedHashMap<>();
        memberIds.forEach(id -> policyByMember.put(id, policyId));
        return reader.readGeneralCeilingBulk(policyByMember,
                Map.of(policyId, CEILING), periodStart(), periodEnd());
    }

    @Test
    void aHoldReducesWhatMayBeCommittedWithoutReducingWhatWasSpent() {
        long memberId = insertMember();
        spend(memberId, policyId, "10000.00");
        hold(memberId, "5000.00");

        GeneralCeilingReading reading = read(List.of(memberId)).get(memberId);

        assertThat(reading.mode()).isEqualTo(Mode.FOUND);
        assertThat(reading.committed()).isEqualByComparingTo("10000.00");
        assertThat(reading.reserved()).isEqualByComparingTo("5000.00");
        assertThat(reading.actualRemaining())
                .as("a hold is not a payment; what was actually spent is unchanged")
                .isEqualByComparingTo("50000.00");
        assertThat(reading.reservableAvailable())
                .as("but it is money already spoken for, so it cannot be committed twice")
                .isEqualByComparingTo("45000.00");
    }

    @Test
    void aReleasedHoldStopsCountingAgainstWhatMayBeCommitted() {
        long memberId = insertMember();
        long holdRow = hold(memberId, "5000.00");
        release(memberId, holdRow, "5000.00");

        GeneralCeilingReading reading = read(List.of(memberId)).get(memberId);

        assertThat(reading.reserved())
                .as("the release points at the hold through reversal_of_id, so it nets to nothing")
                .isEqualByComparingTo("0.00");
        assertThat(reading.reservableAvailable()).isEqualByComparingTo(CEILING);
    }

    @Test
    void aPartiallyReleasedHoldKeepsOnlyItsRemainder() {
        long memberId = insertMember();
        long holdRow = hold(memberId, "5000.00");
        release(memberId, holdRow, "2000.00");

        assertThat(read(List.of(memberId)).get(memberId).reserved())
                .as("releasing part of a hold must not release all of it")
                .isEqualByComparingTo("3000.00");
    }

    @Test
    void aPolicyWithNoAnnualLimitIsUnlimitedRatherThanZero() {
        long memberId = insertMember();
        spend(memberId, policyId, "1000.00");

        GeneralCeilingReading reading = reader.readGeneralCeilingBulk(
                Map.of(memberId, policyId), Map.of(), periodStart(), periodEnd()).get(memberId);

        assertThat(reading.mode()).isEqualTo(Mode.UNLIMITED);
        assertThat(reading.limit()).as("no ceiling means no number, not a very large one").isNull();
        assertThat(reading.actualRemaining()).isNull();
        assertThat(reading.committed())
                .as("what was spent is still real and still worth showing")
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void aMemberWithNoResolvedPolicyIsNotConfiguredRatherThanZero() {
        long memberId = insertMember();

        Map<Long, Long> noPolicy = new HashMap<>();
        noPolicy.put(memberId, null);
        GeneralCeilingReading reading = reader.readGeneralCeilingBulk(
                noPolicy, Map.of(policyId, CEILING), periodStart(), periodEnd()).get(memberId);

        assertThat(reading.mode()).isEqualTo(Mode.NOT_CONFIGURED);
        assertThat(reading.limit()).isNull();
        assertThat(reading.committed()).isNull();
    }

    @Test
    void spendingIsNeverAttributedToACeilingItWasNotSpentAgainst() {
        long previousPolicyId = insertPolicy("PREV-" + suffix(), new BigDecimal("20000.00"));
        long memberId = insertMember();

        spend(memberId, previousPolicyId, "9000.00");
        spend(memberId, policyId, "1000.00");

        assertThat(read(List.of(memberId)).get(memberId).committed())
                .as("rows under a policy the member has since left belong to that policy's "
                        + "ceiling, never to this one")
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void aPageCostsTwoQueriesWhateverItHolds() {
        List<Long> ten = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            long memberId = insertMember();
            spend(memberId, policyId, "100.00");
            hold(memberId, "50.00");
            ten.add(memberId);
        }
        List<Long> forty = new ArrayList<>(ten);
        for (int i = 10; i < 40; i++) {
            long memberId = insertMember();
            spend(memberId, policyId, "100.00");
            hold(memberId, "50.00");
            forty.add(memberId);
        }

        // Three now, not two: the exceptional uplift is resolved here as well,
        // and it is resolved once for the page like the other two rather than
        // once per member. The point of the assertion is unchanged -- the
        // count must not move with the number of rows.
        assertThat(statementsReading(ten))
                .as("one query for committed, one for reserved, one for uplifts")
                .isEqualTo(3L);
        assertThat(statementsReading(forty))
                .as("and the same three for four times the rows")
                .isEqualTo(3L);
    }

    private long statementsReading(List<Long> memberIds) {
        Map<Long, Long> policyByMember = new LinkedHashMap<>();
        memberIds.forEach(id -> policyByMember.put(id, policyId));
        Map<Long, BigDecimal> limits = Map.of(policyId, CEILING);
        LocalDate start = periodStart();
        LocalDate end = periodEnd();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        Map<Long, GeneralCeilingReading> readings =
                reader.readGeneralCeilingBulk(policyByMember, limits, start, end);

        assertThat(readings).hasSize(memberIds.size());
        return statistics.getPrepareStatementCount();
    }
}
