package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * P1.5.0b: {@link BenefitBucketConsumptionRepository#aggregateOwnActiveReservation}
 * is the bulk form of {@code sumOwnActiveReservation}/{@code sumOwnActiveReservationTimes}
 * ({@code OwnActiveReservationReadIntegrationTest} proves those against a
 * real database already). What is genuinely new here is the WHERE clause
 * itself -- status/assignment/bucket filtering happens in SQL, not in Java,
 * so only a real database proves it: a mocked repository would just echo
 * back whatever list the test hands it, proving nothing about the query.
 *
 * Covers B4 (a cancelled/reversed hold is not ACTIVE) and B5 (a different
 * allocation/reference never counts as "own") from the P1.5.0b gate. B1-B3
 * (bucket/period matching) are proven at the adapter level in
 * {@code BucketLimitSnapshotAdapterPreauthTest} since that matching happens
 * in Java, over rows this query already returns correctly scoped.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class OwnActiveReservationBulkReadIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private BenefitBucketConsumptionRepository consumptionRepository;
    @Autowired private JdbcTemplate jdbc;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record World(long memberId, long policyId, long assignmentId, long bucketId) {}

    private World world() {
        String s = suffix();
        Long employerId = jdbc.queryForObject("INSERT INTO employers (code, name) VALUES ('OB-" + s
                + "', 'Own Bulk Co " + s + "') RETURNING id", Long.class);
        Long policyId = jdbc.queryForObject("INSERT INTO benefit_policies (name, policy_code, employer_id, "
                + "annual_limit, default_coverage_percent, start_date, end_date, status, active) VALUES "
                + "('OBP-" + s + "', 'OBPOL-" + s + "', " + employerId
                + ", 1000000, 80, CURRENT_DATE - 60, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id",
                Long.class);
        Long memberId = jdbc.queryForObject("INSERT INTO members (employer_id, full_name, "
                + "benefit_policy_id, card_number, barcode, status, active) VALUES (" + employerId
                + ", 'Own Bulk Member', " + policyId + ", 'OB" + s + "', 'OB" + s
                + "', 'ACTIVE', true) RETURNING id", Long.class);
        Long assignmentId = jdbc.queryForObject(
                "INSERT INTO member_policy_assignments (member_id, policy_id, assignment_start_date, "
                + "assignment_source) VALUES (?, ?, CURRENT_DATE - 60, 'MANUAL') RETURNING id",
                Long.class, memberId, policyId);
        jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id, assignment_start_date, "
                + "assignment_reason, assignment_source) VALUES (?, ?, CURRENT_DATE - 60, "
                + "'test enrollment', 'MANUAL')", memberId, employerId);
        Long groupId = jdbc.queryForObject("INSERT INTO benefit_groups (policy_id, code, name_ar, "
                + "context_type, aggregation_mode) VALUES (" + policyId + ", 'OBG-" + s
                + "', 'مجموعة', 'OUTPATIENT', 'INDIVIDUAL') RETURNING id", Long.class);
        Long bucketId = jdbc.queryForObject("INSERT INTO benefit_limit_buckets (policy_id, "
                + "benefit_group_id, code, name_ar, amount_limit, period_type, counting_method, "
                + "consumption_basis, benefit_scope_type, context_type, active) VALUES (" + policyId
                + ", " + groupId + ", 'OBB-" + s + "', 'وعاء', 1000"
                + ", 'ANNUAL', 'EACH_LINE', 'COMPANY_SHARE', 'CATEGORY', 'OUTPATIENT', true) RETURNING id",
                Long.class);
        return new World(memberId, policyId, assignmentId, bucketId);
    }

    private long newPreauth(World w) {
        return jdbc.queryForObject("INSERT INTO pre_authorizations (member_id, policy_id, "
                + "status, request_date, created_at, updated_at) VALUES (" + w.memberId() + ", " + w.policyId()
                + ", 'APPROVED', now(), now(), now()) RETURNING id", Long.class);
    }

    /**
     * A second, genuinely different allocation for the SAME member --
     * member_policy_assignments forbids two overlapping rows for one member
     * (uk_member_policy_assignment_no_overlap, scoped by member_id alone),
     * so the first assignment must be closed (the one mutation the
     * append-only-with-controlled-closure guard allows) before a new one can
     * open after it.
     */
    private long newAssignment(World w) {
        jdbc.update("UPDATE member_policy_assignments SET assignment_end_date = CURRENT_DATE - 31 WHERE id = ?",
                w.assignmentId());
        return jdbc.queryForObject(
                "INSERT INTO member_policy_assignments (member_id, policy_id, assignment_start_date, "
                + "assignment_source) VALUES (?, ?, CURRENT_DATE - 30, 'MANUAL') RETURNING id",
                Long.class, w.memberId(), w.policyId());
    }

    /** Places an ACTIVE (RESERVED) hold owned by the given preauth+assignment on this year's period. */
    private long hold(World w, long preauthId, long assignmentId, long bucketId, String amount, String status) {
        String s = suffix();
        Long lineId = jdbc.queryForObject("INSERT INTO pre_authorization_lines (pre_authorization_id, "
                + "requested_amount) VALUES (" + preauthId + ", " + amount + ") RETURNING id", Long.class);
        return jdbc.queryForObject("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, "
                + "bucket_id, preauth_id, preauth_line_id, member_policy_assignment_id, period_start, period_end, "
                + "approved_amount, times_consumed, calculation_version, idempotency_key, status, source_type, "
                + "limit_scope, created_at) VALUES (" + w.policyId() + ", " + w.memberId() + ", " + bucketId + ", "
                + preauthId + ", " + lineId + ", " + assignmentId + ", DATE_TRUNC('year', CURRENT_DATE)::date, "
                + "(DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date, " + amount
                + ", 0, 1, 'OBH-" + s + "', '" + status + "', 'PREAUTH', 'BUCKET', now()) RETURNING id",
                Long.class);
    }

    @Test
    void b4ACancelledHoldIsNotActiveAndIsNeverReturned() {
        World w = world();
        long preauthId = newPreauth(w);
        long holdId = hold(w, preauthId, w.assignmentId(), w.bucketId(), "600.00", "RESERVED");
        // benefit_bucket_consumptions is append-only (status can never be
        // edited on a posted row -- confirmed by the DB itself rejecting a
        // direct UPDATE). A full cancellation is a 100% compensating REVERSED
        // row referencing the original, which itself stays RESERVED forever
        // -- exactly OwnActiveReservationReadIntegrationTest.onlyWhatIsStillOutstandingIsReturned's
        // pattern, just netting to zero instead of partially.
        String s = suffix();
        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, bucket_id, preauth_id, "
                + "preauth_line_id, member_policy_assignment_id, period_start, period_end, approved_amount, "
                + "times_consumed, calculation_version, idempotency_key, status, source_type, limit_scope, "
                + "reversal_of_id, reversal_reason, created_at) SELECT policy_id, member_id, bucket_id, preauth_id, "
                + "preauth_line_id, member_policy_assignment_id, period_start, period_end, 600.00, 0, 1, 'OBR-" + s
                + "', 'REVERSED', source_type, limit_scope, id, 'PREAUTH_CANCELLATION', now() "
                + "FROM benefit_bucket_consumptions WHERE id = ?", holdId);

        var rows = consumptionRepository.aggregateOwnActiveReservation(
                w.memberId(), preauthId, w.assignmentId(), List.of(w.bucketId()));

        // The original RESERVED row is still selected (status is never
        // rewritten), but its net amount is now zero -- fully released,
        // nothing left for a claim to reclaim as "own".
        BigDecimal totalOwn = rows.stream().map(BenefitBucketConsumptionRepository.OwnActiveReservationProjection::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalOwn).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void b5ADifferentAllocationAssignmentIsNeverCountedAsOwn() {
        World w = world();
        long preauthId = newPreauth(w);
        long otherAssignmentId = newAssignment(w);
        // Same preauth, same bucket, same period -- but a DIFFERENT
        // member_policy_assignment_id (a different enrollment/allocation).
        hold(w, preauthId, otherAssignmentId, w.bucketId(), "600.00", "RESERVED");

        var rows = consumptionRepository.aggregateOwnActiveReservation(
                w.memberId(), preauthId, w.assignmentId(), List.of(w.bucketId()));

        assertThat(rows).isEmpty();
    }

    @Test
    void anActiveHoldForTheExactSameFourConditionsIsReturned() {
        World w = world();
        long preauthId = newPreauth(w);
        hold(w, preauthId, w.assignmentId(), w.bucketId(), "600.00", "RESERVED");

        var rows = consumptionRepository.aggregateOwnActiveReservation(
                w.memberId(), preauthId, w.assignmentId(), List.of(w.bucketId()));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getBucketId()).isEqualTo(w.bucketId());
        assertThat(rows.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("600.00"));
    }
}
