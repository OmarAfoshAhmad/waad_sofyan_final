package com.waad.tba.modules.preauthorization.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * The locks, under real contention.
 *
 * Until this file existed the locks were an assertion in a comment: two
 * threads, two transactions, two connections, and a barrier that makes them
 * actually collide -- not two tasks submitted and hoped to overlap.
 *
 * What is at stake is double-promising. If two approvals for the same member
 * both read "800 available" and both hold 800, the member has been promised
 * 1600 of a 1000 limit, and nothing in the ledger says so until a claim
 * arrives and cannot be paid.
 */
@SpringBootTest(classes = TbaWaadApplication.class,
        properties = "waad.preauth.validity-days=30")
@ActiveProfiles("test")
class PreAuthReservationConcurrencyIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private PreAuthReservationLedgerService service;
    @Autowired private JdbcTemplate jdbc;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** A member with one bucket, and however many pre-authorizations are asked for. */
    private record World(long memberId, long policyId, long bucketId, List<Long> preauthIds) {}

    private World world(String amountLimit, Integer timesLimit, String requestedEach,
            int coveragePercent, int preauthCount) {
        String s = suffix();
        Long employerId = jdbc.queryForObject("INSERT INTO employers (code, name) VALUES ('CC-" + s
                + "', 'Concurrent Co " + s + "') RETURNING id", Long.class);
        Long policyId = jdbc.queryForObject("INSERT INTO benefit_policies (name, policy_code, employer_id, "
                + "annual_limit, default_coverage_percent, start_date, end_date, status, active) VALUES ('CP-" + s
                + "', 'CPOL-" + s + "', " + employerId + ", 1000000, " + coveragePercent
                + ", CURRENT_DATE - 60, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id", Long.class);
        Long memberId = jdbc.queryForObject("INSERT INTO members (employer_id, full_name, benefit_policy_id, "
                + "card_number, barcode, status, active) VALUES (" + employerId + ", 'Concurrent Member', "
                + policyId + ", 'CC" + s + "', 'CC" + s + "', 'ACTIVE', true) RETURNING id", Long.class);
        jdbc.update("INSERT INTO member_policy_assignments (member_id, policy_id, assignment_start_date, "
                + "assignment_source) VALUES (?, ?, CURRENT_DATE - 60, 'MANUAL')", memberId, policyId);
        jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id, assignment_start_date, "
                + "assignment_reason, assignment_source) VALUES (?, ?, CURRENT_DATE - 60, "
                + "'test enrollment', 'MANUAL')", memberId, employerId);

        Long categoryId = jdbc.queryForObject("INSERT INTO medical_categories (code, name, active) "
                + "VALUES ('CCAT-" + s + "', 'Concurrent Category', true) RETURNING id", Long.class);
        Long serviceId = jdbc.queryForObject("INSERT INTO medical_services (code, name, category_id, active) "
                + "VALUES ('CSRV-" + s + "', 'Concurrent Service', " + categoryId + ", true) RETURNING id",
                Long.class);
        Long ruleId = jdbc.queryForObject("INSERT INTO benefit_policy_rules (benefit_policy_id, "
                + "medical_category_id, encounter_type, coverage_percent, active, deleted) VALUES ("
                + policyId + ", " + categoryId + ", 'OUTPATIENT', " + coveragePercent
                + ", true, false) RETURNING id", Long.class);
        Long groupId = jdbc.queryForObject("INSERT INTO benefit_groups (policy_id, code, name_ar, "
                + "context_type, aggregation_mode) VALUES (" + policyId + ", 'CG-" + s
                + "', 'مجموعة', 'OUTPATIENT', 'INDIVIDUAL') RETURNING id", Long.class);
        Long bucketId = jdbc.queryForObject("INSERT INTO benefit_limit_buckets (policy_id, benefit_group_id, "
                + "code, name_ar, amount_limit, times_limit, period_type, counting_method, consumption_basis, "
                + "benefit_scope_type, context_type, active) VALUES (" + policyId + ", " + groupId + ", 'CB-" + s
                + "', 'وعاء', " + amountLimit + ", " + (timesLimit == null ? "NULL" : timesLimit)
                + ", 'ANNUAL', 'EACH_UNIT', 'COMPANY_SHARE', 'CATEGORY', 'OUTPATIENT', true) RETURNING id",
                Long.class);
        jdbc.update("INSERT INTO benefit_rule_buckets (rule_id, bucket_id) VALUES (?, ?)", ruleId, bucketId);

        Long providerId = jdbc.queryForObject("INSERT INTO providers (name, license_number, provider_type) "
                + "VALUES ('Prov " + s + "', 'CLIC-" + s + "', 'CLINIC') RETURNING id", Long.class);

        List<Long> preauthIds = new ArrayList<>();
        for (int i = 0; i < preauthCount; i++) {
            Long preauthId = jdbc.queryForObject("INSERT INTO pre_authorizations (member_id, policy_id, "
                    + "provider_id, service_category_id, status, request_date, expected_service_date, "
                    + "created_at, updated_at, version) VALUES (" + memberId + ", " + policyId + ", "
                    + providerId + ", " + categoryId + ", 'SUBMITTED', now(), CURRENT_DATE + 14, now(), "
                    + "now(), 0) RETURNING id", Long.class);
            jdbc.update("INSERT INTO pre_authorization_lines (pre_authorization_id, provider_service_id, "
                    + "medical_service_id, medical_category_id, provider_service_code, service_name, "
                    + "contract_price, requested_amount, coverage_percentage, encounter_type, "
                    + "requested_quantity, approved_quantity) VALUES (?, " + serviceId + ", " + serviceId
                    + ", " + categoryId + ", ?, ?, " + requestedEach + ", " + requestedEach + ", "
                    + coveragePercent + ", 'OUTPATIENT', 1, 1)",
                    preauthId, "SVC-" + s + "-" + i, "Service " + i);
            preauthIds.add(preauthId);
        }
        return new World(memberId, policyId, bucketId, preauthIds);
    }

    /** Runs both tasks so they provably collide, and reports what each did. */
    private List<Outcome> raceOf(Runnable first, Runnable second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            List<Future<Outcome>> futures = List.of(
                    pool.submit(() -> run(barrier, first)),
                    pool.submit(() -> run(barrier, second)));
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private Outcome run(CyclicBarrier barrier, Runnable task) {
        try {
            // Neither thread proceeds until both are here: the contention is
            // guaranteed rather than hoped for.
            barrier.await();
            task.run();
            return new Outcome(true, null);
        } catch (Exception e) {
            return new Outcome(false, e);
        }
    }

    private record Outcome(boolean succeeded, Exception failure) {}

    private BigDecimal netReservedAmount(long bucketId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(c.approved_amount - COALESCE(r.released, 0)), 0) "
                        + "FROM benefit_bucket_consumptions c LEFT JOIN ("
                        + "  SELECT reversal_of_id, SUM(approved_amount) AS released "
                        + "  FROM benefit_bucket_consumptions WHERE status='REVERSED' "
                        + "  GROUP BY reversal_of_id) r ON r.reversal_of_id = c.id "
                        + "WHERE c.bucket_id = ? AND c.status = 'RESERVED'",
                BigDecimal.class, bucketId);
    }

    private int netReservedTimes(long bucketId) {
        Integer held = jdbc.queryForObject(
                "SELECT COALESCE(SUM(c.times_consumed), 0) FROM benefit_bucket_consumptions c "
                        + "WHERE c.bucket_id = ? AND c.status = 'RESERVED' "
                        + "AND NOT EXISTS (SELECT 1 FROM benefit_bucket_consumptions r "
                        + "                 WHERE r.reversal_of_id = c.id AND r.status = 'REVERSED')",
                Integer.class, bucketId);
        return held == null ? 0 : held;
    }

    // ── two approvals of the SAME pre-authorization ─────────────────────

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void twoApprovalsOfTheSamePreAuthorizationProduceOneDecisionAndOneSetOfHolds() throws Exception {
        World w = world("10000", null, "1000.00", 80, 1);
        long preauthId = w.preauthIds().get(0);

        raceOf(() -> service.approveAndReserve(preauthId, null, "reviewer-a"),
               () -> service.approveAndReserve(preauthId, null, "reviewer-b"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM preauth_decision_snapshots WHERE preauth_id = ?",
                Long.class, preauthId)).as("one decision").isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM benefit_bucket_consumptions "
                + "WHERE preauth_id = ? AND status = 'RESERVED'", Long.class, preauthId))
                .as("one set of holds -- bucket and general ceiling").isEqualTo(2L);
        assertThat(netReservedAmount(w.bucketId()))
                .as("the member is promised 800 once, not twice").isEqualByComparingTo("800.00");
    }

    // ── two approvals racing for the last of the money ─────────────────

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void twoApprovalsNearAMonetaryCeilingNeverPromiseMoreThanIsAvailable() throws Exception {
        // 1000 of limit; each approval wants 800 of it. Both cannot have it.
        World w = world("1000", null, "1000.00", 80, 2);

        raceOf(() -> service.approveAndReserve(w.preauthIds().get(0), null, "reviewer-a"),
               () -> service.approveAndReserve(w.preauthIds().get(1), null, "reviewer-b"));

        BigDecimal held = netReservedAmount(w.bucketId());

        // The invariant is not "one wins" -- the second may legitimately be
        // capped down to what is left. The invariant is that the total
        // promised never exceeds the ceiling.
        assertThat(held)
                .as("no race may promise more than the bucket holds")
                .isLessThanOrEqualTo(new BigDecimal("1000.00"));
        assertThat(held).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void twoApprovalsNearAnOccurrenceCeilingHoldItOnlyOnce() throws Exception {
        // A single visit, wanted by two approvals.
        World w = world("1000000", 1, "1000.00", 80, 2);

        raceOf(() -> service.approveAndReserve(w.preauthIds().get(0), null, "reviewer-a"),
               () -> service.approveAndReserve(w.preauthIds().get(1), null, "reviewer-b"));

        assertThat(netReservedTimes(w.bucketId()))
                .as("one visit exists, so one visit may be held")
                .isEqualTo(1);
    }

    // ── the two exits racing each other ─────────────────────────────────

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void cancellingAndExpiringTogetherReleaseEachHoldExactlyOnce() throws Exception {
        World w = world("10000", null, "1000.00", 80, 1);
        long preauthId = w.preauthIds().get(0);
        service.approveAndReserve(preauthId, null, "reviewer");
        jdbc.update("UPDATE pre_authorizations SET expiry_date = CURRENT_DATE - 1 WHERE id = ?", preauthId);

        raceOf(() -> service.cancelAndRelease(preauthId, "طلب المستفيد", "reviewer"),
               () -> service.expireAndRelease(preauthId, "sweep-" + suffix()));

        // Whichever wins, the money comes back exactly once and the
        // compensating movements never exceed their originals.
        assertThat(netReservedAmount(w.bucketId()))
                .as("released once, not twice").isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM benefit_bucket_consumptions "
                + "WHERE preauth_id = ? AND status = 'REVERSED'", Long.class, preauthId))
                .as("one compensating movement per hold").isEqualTo(2L);

        String finalStatus = jdbc.queryForObject(
                "SELECT status FROM pre_authorizations WHERE id = ?", String.class, preauthId);
        assertThat(finalStatus).isIn("CANCELLED", "EXPIRED");
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void twoExpirySweepsReleaseOnce() throws Exception {
        World w = world("10000", null, "1000.00", 80, 1);
        long preauthId = w.preauthIds().get(0);
        service.approveAndReserve(preauthId, null, "reviewer");
        jdbc.update("UPDATE pre_authorizations SET expiry_date = CURRENT_DATE - 1 WHERE id = ?", preauthId);

        String eventId = "sweep-" + suffix();
        raceOf(() -> service.expireAndRelease(preauthId, eventId),
               () -> service.expireAndRelease(preauthId, eventId));

        assertThat(netReservedAmount(w.bucketId())).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM benefit_bucket_consumptions "
                + "WHERE preauth_id = ? AND status = 'REVERSED'", Long.class, preauthId))
                .isEqualTo(2L);
    }

    // ── an approval racing an exit ─────────────────────────────────────

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void anApprovalAndACancellationNeverLeaveAHoldWithoutAnApprovalOrTheReverse() throws Exception {
        World w = world("10000", null, "1000.00", 80, 1);
        long preauthId = w.preauthIds().get(0);

        raceOf(() -> service.approveAndReserve(preauthId, null, "reviewer"),
               () -> {
                   try {
                       service.cancelAndRelease(preauthId, "طلب المستفيد", "reviewer");
                   } catch (RuntimeException expected) {
                       // Cancelling before the approval lands is refused: there
                       // is nothing to release yet.
                   }
               });

        String status = jdbc.queryForObject(
                "SELECT status FROM pre_authorizations WHERE id = ?", String.class, preauthId);
        BigDecimal held = netReservedAmount(w.bucketId());

        // The two states that must never occur: an approval whose holds were
        // released out from under it, and a cancellation that left money held.
        if ("CANCELLED".equals(status)) {
            assertThat(held).as("a cancelled approval must hold nothing").isEqualByComparingTo("0");
        } else {
            assertThat(status).isEqualTo("APPROVED");
            assertThat(held).as("an approved pre-authorization must hold its money")
                    .isEqualByComparingTo("800.00");
        }
    }

    @Test
    @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    void twoApprovalsOnACountOnlyBucketCannotBothTakeTheOnlyVisit() throws Exception {
        // No monetary ceiling at all -- the case the approval path could not
        // even see until the counting dimension was separated out, and the
        // one where both approvals used to succeed.
        World w = world("NULL", 1, "1000.00", 80, 2);

        raceOf(() -> service.approveAndReserve(w.preauthIds().get(0), null, "reviewer-a"),
               () -> service.approveAndReserve(w.preauthIds().get(1), null, "reviewer-b"));

        assertThat(netReservedTimes(w.bucketId()))
                .as("one visit exists, so the two approvals may hold one between them")
                .isEqualTo(1);

        // The approval that missed out holds nothing at all -- no zero row.
        Long zeroHolds = jdbc.queryForObject(
                "SELECT COUNT(*) FROM benefit_bucket_consumptions "
                        + "WHERE bucket_id = ? AND status = 'RESERVED' "
                        + "AND COALESCE(times_consumed, 0) = 0 AND COALESCE(approved_amount, 0) = 0",
                Long.class, w.bucketId());
        assertThat(zeroHolds).as("a hold of nothing records an effect that did not happen").isZero();
    }
}
