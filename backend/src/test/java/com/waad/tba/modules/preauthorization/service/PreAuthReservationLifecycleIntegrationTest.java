package com.waad.tba.modules.preauthorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * The reservation life cycle: approve, cancel, expire.
 *
 * These three exist together because a hold removes limit from a member before
 * any claim exists. If nothing could release it, that limit would be gone
 * permanently with no claim to explain where it went -- so the approval path
 * is only safe once both exits work, and this test covers them as one unit.
 *
 * The property running through all of it: approving must NOT change what the
 * member has actually got left. It changes what a NEW decision may take.
 * Confusing the two is how the same limit gets promised twice, and how a
 * report tells a member they have spent money they have not.
 */
@SpringBootTest(classes = TbaWaadApplication.class,
        properties = "waad.preauth.validity-days=30")
@ActiveProfiles("test")
class PreAuthReservationLifecycleIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private PreAuthReservationLedgerService service;
    @Autowired private JdbcTemplate jdbc;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record Scenario(long preauthId, long memberId, long policyId, long bucketId) {}

    private Scenario scenario(String amountLimit, Integer timesLimit, String requested,
            int coveragePercent, int quantity) {
        String s = suffix();
        Long employerId = jdbc.queryForObject("INSERT INTO employers (code, name) VALUES ('RL-" + s
                + "', 'Reserve Co " + s + "') RETURNING id", Long.class);
        Long policyId = jdbc.queryForObject("INSERT INTO benefit_policies (name, policy_code, employer_id, "
                + "annual_limit, default_coverage_percent, start_date, end_date, status, active) VALUES ('RP-" + s
                + "', 'RPOL-" + s + "', " + employerId + ", 1000000, " + coveragePercent
                + ", CURRENT_DATE - 60, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id", Long.class);
        Long memberId = jdbc.queryForObject("INSERT INTO members (employer_id, full_name, benefit_policy_id, "
                + "card_number, barcode, status, active) VALUES (" + employerId + ", 'Reserve Member', "
                + policyId + ", 'RC" + s + "', 'RC" + s + "', 'ACTIVE', true) RETURNING id", Long.class);
        jdbc.update("INSERT INTO member_policy_assignments (member_id, policy_id, assignment_start_date, "
                + "assignment_source) VALUES (?, ?, CURRENT_DATE - 60, 'MANUAL')", memberId, policyId);
        jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id, assignment_start_date, "
                + "assignment_reason, assignment_source) VALUES (?, ?, CURRENT_DATE - 60, "
                + "'test enrollment', 'MANUAL')", memberId, employerId);

        Long categoryId = jdbc.queryForObject("INSERT INTO medical_categories (code, name, active) "
                + "VALUES ('RCAT-" + s + "', 'Reserve Category', true) RETURNING id", Long.class);
        Long serviceId = jdbc.queryForObject("INSERT INTO medical_services (code, name, category_id, active) "
                + "VALUES ('RSRV-" + s + "', 'Reserve Service', " + categoryId + ", true) RETURNING id",
                Long.class);
        Long ruleId = jdbc.queryForObject("INSERT INTO benefit_policy_rules (benefit_policy_id, "
                + "medical_category_id, encounter_type, coverage_percent, active, deleted) VALUES ("
                + policyId + ", " + categoryId + ", 'OUTPATIENT', " + coveragePercent
                + ", true, false) RETURNING id", Long.class);
        Long groupId = jdbc.queryForObject("INSERT INTO benefit_groups (policy_id, code, name_ar, "
                + "context_type, aggregation_mode) VALUES (" + policyId + ", 'RG-" + s
                + "', 'مجموعة', 'OUTPATIENT', 'INDIVIDUAL') RETURNING id", Long.class);
        Long bucketId = jdbc.queryForObject("INSERT INTO benefit_limit_buckets (policy_id, benefit_group_id, "
                + "code, name_ar, amount_limit, times_limit, period_type, counting_method, consumption_basis, "
                + "benefit_scope_type, context_type, active) VALUES (" + policyId + ", " + groupId + ", 'RB-" + s
                + "', 'وعاء', " + amountLimit + ", " + (timesLimit == null ? "NULL" : timesLimit)
                + ", 'ANNUAL', 'EACH_UNIT', 'COMPANY_SHARE', 'CATEGORY', 'OUTPATIENT', true) RETURNING id",
                Long.class);
        jdbc.update("INSERT INTO benefit_rule_buckets (rule_id, bucket_id) VALUES (?, ?)", ruleId, bucketId);

        Long providerId = jdbc.queryForObject("INSERT INTO providers (name, license_number, provider_type) "
                + "VALUES ('Prov " + s + "', 'RLIC-" + s + "', 'CLINIC') RETURNING id", Long.class);

        Long preauthId = jdbc.queryForObject("INSERT INTO pre_authorizations (member_id, policy_id, provider_id, "
                + "service_category_id, status, request_date, expected_service_date, created_at, updated_at, "
                + "version) VALUES (" + memberId + ", " + policyId + ", " + providerId + ", " + categoryId
                + ", 'SUBMITTED', now(), CURRENT_DATE + 14, now(), now(), 0) RETURNING id", Long.class);
        jdbc.update("INSERT INTO pre_authorization_lines (pre_authorization_id, provider_service_id, "
                + "medical_service_id, medical_category_id, provider_service_code, service_name, "
                + "contract_price, requested_amount, coverage_percentage, encounter_type, "
                + "requested_quantity, approved_quantity) VALUES (?, " + serviceId + ", " + serviceId + ", "
                + categoryId + ", ?, ?, " + requested + ", " + requested + ", " + coveragePercent
                + ", 'OUTPATIENT', " + quantity + ", " + quantity + ")",
                preauthId, "SVC-" + s, "Service " + s);

        return new Scenario(preauthId, memberId, policyId, bucketId);
    }

    private long reservedRows(long preauthId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM benefit_bucket_consumptions "
                + "WHERE preauth_id = ? AND status = 'RESERVED'", Long.class, preauthId);
    }

    private long releaseRows(long preauthId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM benefit_bucket_consumptions "
                + "WHERE preauth_id = ? AND status = 'REVERSED'", Long.class, preauthId);
    }

    private BigDecimal netReserved(long memberId, long bucketId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(c.approved_amount - COALESCE(r.released, 0)), 0) "
                        + "FROM benefit_bucket_consumptions c LEFT JOIN ("
                        + "  SELECT reversal_of_id, SUM(approved_amount) AS released "
                        + "  FROM benefit_bucket_consumptions WHERE status='REVERSED' "
                        + "  GROUP BY reversal_of_id) r ON r.reversal_of_id = c.id "
                        + "WHERE c.member_id = ? AND c.bucket_id = ? AND c.status = 'RESERVED'",
                BigDecimal.class, memberId, bucketId);
    }

    private String status(long preauthId) {
        return jdbc.queryForObject("SELECT status FROM pre_authorizations WHERE id = ?",
                String.class, preauthId);
    }

    // ── approval ────────────────────────────────────────────────────────

    @Test
    void approvingWritesTheSnapshotAndTheHoldsInOneTransaction() {
        Scenario sc = scenario("10000", null, "1000.00", 80, 1);

        var snapshot = service.approveAndReserve(sc.preauthId(), 0L, "reviewer");

        assertThat(snapshot.getId()).isNotNull();
        assertThat(snapshot.getCompanyShareTotal()).isEqualByComparingTo("800.00");
        assertThat(status(sc.preauthId())).isEqualTo("APPROVED");

        // The bucket and the general ceiling each hold the decision in their
        // own terms -- two rows, never one summed figure.
        assertThat(reservedRows(sc.preauthId())).isEqualTo(2);
        assertThat(netReserved(sc.memberId(), sc.bucketId())).isEqualByComparingTo("800.00");

        // An expiry is always set: a hold with no end date would keep the
        // member's limit forever.
        assertThat(jdbc.queryForObject("SELECT expiry_date FROM pre_authorizations WHERE id = ?",
                LocalDate.class, sc.preauthId())).isEqualTo(LocalDate.now().plusDays(30));
    }

    @Test
    void approvingDoesNotChangeWhatTheMemberHasActuallyGotLeft() {
        Scenario sc = scenario("10000", null, "1000.00", 80, 1);

        service.approveAndReserve(sc.preauthId(), 0L, "reviewer");

        // Nothing is CONSUMED by an approval, so committed stays zero and the
        // member's actual remaining balance is untouched. Only what a NEW
        // decision may take has fallen.
        BigDecimal committed = jdbc.queryForObject(
                "SELECT COALESCE(SUM(approved_amount), 0) FROM benefit_bucket_consumptions "
                        + "WHERE member_id = ? AND status = 'COMMITTED'",
                BigDecimal.class, sc.memberId());
        assertThat(committed).as("a hold is not a consumption").isEqualByComparingTo("0");
        assertThat(netReserved(sc.memberId(), sc.bucketId())).isEqualByComparingTo("800.00");
    }

    @Test
    void reApprovingWithTheSameKeyReturnsTheFirstDecisionWithoutASecondHold() {
        Scenario sc = scenario("10000", null, "1000.00", 80, 1);

        var first = service.approveAndReserve(sc.preauthId(), 0L, "reviewer");
        var second = service.approveAndReserve(sc.preauthId(), null, "reviewer");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(reservedRows(sc.preauthId())).as("no second set of holds").isEqualTo(2);
    }

    @Test
    void approvingWithAStaleVersionIsRefused() {
        Scenario sc = scenario("10000", null, "1000.00", 80, 1);

        assertThatThrownBy(() -> service.approveAndReserve(sc.preauthId(), 99L, "reviewer"))
                .hasMessageContaining("مستخدم آخر");
        assertThat(reservedRows(sc.preauthId())).isZero();
    }

    @Test
    void anExhaustedCeilingRecordsTheSnapshotButHoldsNothing() {
        Scenario sc = scenario("1000", null, "1000.00", 80, 1);
        // Consume the whole bucket first.
        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, bucket_id, period_start, "
                + "period_end, approved_amount, times_consumed, calculation_version, idempotency_key, status, "
                + "source_type, limit_scope, created_at) VALUES (?, ?, ?, "
                + "DATE_TRUNC('year', CURRENT_DATE)::date, "
                + "(DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date, "
                + "1000.00, 0, 1, ?, 'COMMITTED', 'OPENING_IMPORT', 'BUCKET', now())",
                sc.policyId(), sc.memberId(), sc.bucketId(), "EX-" + suffix());

        var snapshot = service.approveAndReserve(sc.preauthId(), 0L, "reviewer");

        // The snapshot explains the decision; the ledger records only real
        // movement, and a hold of zero in both dimensions is not one.
        assertThat(snapshot.getCoverageOutcome()).isEqualTo("LIMIT_EXHAUSTED");
        assertThat(snapshot.getCompanyShareTotal()).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM preauth_line_limit_snapshots pls "
                + "JOIN preauth_line_snapshots ls ON ls.id = pls.line_snapshot_id "
                + "WHERE ls.decision_snapshot_id = ?", Long.class, snapshot.getId()))
                .as("the snapshot is still written").isPositive();
        assertThat(reservedRows(sc.preauthId())).as("no zero-movement hold").isZero();
    }

    @Test
    void anOccurrenceLimitIsHeldOnTheSameRowAsTheMoney() {
        Scenario sc = scenario("10000", 3, "1000.00", 80, 2);

        service.approveAndReserve(sc.preauthId(), 0L, "reviewer");

        Integer heldTimes = jdbc.queryForObject(
                "SELECT times_consumed FROM benefit_bucket_consumptions "
                        + "WHERE preauth_id = ? AND bucket_id = ? AND status = 'RESERVED'",
                Integer.class, sc.preauthId(), sc.bucketId());
        BigDecimal heldAmount = jdbc.queryForObject(
                "SELECT approved_amount FROM benefit_bucket_consumptions "
                        + "WHERE preauth_id = ? AND bucket_id = ? AND status = 'RESERVED'",
                BigDecimal.class, sc.preauthId(), sc.bucketId());

        // One movement carrying both ceilings, exactly as the snapshot does.
        assertThat(heldTimes).isEqualTo(2);
        assertThat(heldAmount).isEqualByComparingTo("800.00");
    }

    // ── the exits ───────────────────────────────────────────────────────

    @Test
    void cancellingReleasesEveryHoldAndFreesWhatANewDecisionMayTake() {
        Scenario sc = scenario("10000", null, "1000.00", 80, 1);
        service.approveAndReserve(sc.preauthId(), 0L, "reviewer");

        int released = service.cancelAndRelease(sc.preauthId(), "طلب المستفيد", "reviewer");

        assertThat(released).isEqualTo(2);
        assertThat(status(sc.preauthId())).isEqualTo("CANCELLED");

        // Net zero: the originals are untouched and the compensating
        // movements carry the release.
        assertThat(netReserved(sc.memberId(), sc.bucketId())).isEqualByComparingTo("0");
        assertThat(reservedRows(sc.preauthId())).as("originals survive").isEqualTo(2);
        assertThat(releaseRows(sc.preauthId())).isEqualTo(2);
    }

    @Test
    void cancellingTwiceReleasesOnlyOnce() {
        Scenario sc = scenario("10000", null, "1000.00", 80, 1);
        service.approveAndReserve(sc.preauthId(), 0L, "reviewer");

        service.cancelAndRelease(sc.preauthId(), "طلب المستفيد", "reviewer");
        int second = service.cancelAndRelease(sc.preauthId(), "سبب مختلف", "reviewer");

        assertThat(second).as("nothing left to release").isZero();
        assertThat(releaseRows(sc.preauthId())).isEqualTo(2);
        // The first reason stands: a later call does not rewrite history.
        assertThat(jdbc.queryForObject("SELECT decision_notes FROM pre_authorizations WHERE id = ?",
                String.class, sc.preauthId())).isEqualTo("طلب المستفيد");
    }

    @Test
    void cancellingRequiresAReason() {
        Scenario sc = scenario("10000", null, "1000.00", 80, 1);
        service.approveAndReserve(sc.preauthId(), 0L, "reviewer");

        assertThatThrownBy(() -> service.cancelAndRelease(sc.preauthId(), "  ", "reviewer"))
                .hasMessageContaining("سبباً صريحاً");
        assertThat(releaseRows(sc.preauthId())).isZero();
    }

    @Test
    void expiringReleasesTheHoldsAndIsSafeToRetry() {
        Scenario sc = scenario("10000", null, "1000.00", 80, 1);
        service.approveAndReserve(sc.preauthId(), 0L, "reviewer");
        // Bring the expiry date into the past so the sweep may act.
        jdbc.update("UPDATE pre_authorizations SET expiry_date = CURRENT_DATE - 1 WHERE id = ?",
                sc.preauthId());

        String eventId = "sweep-" + suffix();
        int first = service.expireAndRelease(sc.preauthId(), eventId);
        int second = service.expireAndRelease(sc.preauthId(), eventId);

        assertThat(first).isEqualTo(2);
        assertThat(second).as("the same sweep must not release twice").isZero();
        assertThat(status(sc.preauthId())).isEqualTo("EXPIRED");
        assertThat(netReserved(sc.memberId(), sc.bucketId())).isEqualByComparingTo("0");
        assertThat(releaseRows(sc.preauthId())).isEqualTo(2);
    }

    @Test
    void anApprovalMayNotExpireBeforeItsValidityEnds() {
        Scenario sc = scenario("10000", null, "1000.00", 80, 1);
        service.approveAndReserve(sc.preauthId(), 0L, "reviewer");

        assertThatThrownBy(() -> service.expireAndRelease(sc.preauthId(), "early-" + suffix()))
                .hasMessageContaining("قبل انتهاء صلاحيتها");
        assertThat(releaseRows(sc.preauthId())).isZero();
    }

    @Test
    void aConvertedApprovalCanNeitherBeCancelledNorExpired() {
        Scenario sc = scenario("10000", null, "1000.00", 80, 1);
        service.approveAndReserve(sc.preauthId(), 0L, "reviewer");
        jdbc.update("UPDATE pre_authorizations SET status = 'CONSUMED' WHERE id = ?", sc.preauthId());

        assertThatThrownBy(() -> service.cancelAndRelease(sc.preauthId(), "سبب", "reviewer"))
                .hasMessageContaining("تحولت إلى مطالبة");
        assertThat(releaseRows(sc.preauthId())).isZero();
    }

    @Test
    void aReleasedHoldFreesTheOccurrenceForTheNextApproval() {
        Scenario sc = scenario("10000", 1, "1000.00", 80, 1);
        service.approveAndReserve(sc.preauthId(), 0L, "reviewer");

        Integer heldTimes = jdbc.queryForObject(
                "SELECT COALESCE(SUM(times_consumed), 0) FROM benefit_bucket_consumptions "
                        + "WHERE bucket_id = ? AND status = 'RESERVED'",
                Integer.class, sc.bucketId());
        assertThat(heldTimes).isEqualTo(1);

        service.cancelAndRelease(sc.preauthId(), "طلب المستفيد", "reviewer");

        // The compensating movement gives the occurrence back, so the next
        // approval sees it available again.
        Integer releasedTimes = jdbc.queryForObject(
                "SELECT COALESCE(SUM(times_consumed), 0) FROM benefit_bucket_consumptions "
                        + "WHERE bucket_id = ? AND status = 'REVERSED'",
                Integer.class, sc.bucketId());
        assertThat(releasedTimes).isEqualTo(1);
    }

    // ── transaction boundaries ──────────────────────────────────────────

    @Test
    void aFailureOnTheLastScopeLeavesNoSnapshotNoHoldAndNoStatusChange() {
        Scenario sc = scenario("10000", null, "1000.00", 80, 1);

        String statusBefore = status(sc.preauthId());
        Long versionBefore = jdbc.queryForObject(
                "SELECT version FROM pre_authorizations WHERE id = ?", Long.class, sc.preauthId());

        // Fail on the LAST scope written -- the general ceiling -- so the
        // bucket hold and the whole snapshot are already in the transaction
        // when it blows up. Anything less would test rollback of nothing.
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION fail_on_general_scope() RETURNS trigger AS $$
                BEGIN
                    IF NEW.limit_scope = 'POLICY_GENERAL' THEN
                        RAISE EXCEPTION 'injected failure on the last scope';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql;
                """);
        jdbc.execute("""
                CREATE TRIGGER trg_fail_on_general_scope
                BEFORE INSERT ON benefit_bucket_consumptions
                FOR EACH ROW EXECUTE FUNCTION fail_on_general_scope();
                """);
        try {
            assertThatThrownBy(() -> service.approveAndReserve(sc.preauthId(), 0L, "reviewer"))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS trg_fail_on_general_scope "
                    + "ON benefit_bucket_consumptions");
            jdbc.execute("DROP FUNCTION IF EXISTS fail_on_general_scope()");
        }

        // Read afterwards, in a new transaction: a half-written approval is
        // the worst outcome available -- limit held with no snapshot to
        // explain it, or a snapshot promising money never reserved.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM preauth_decision_snapshots WHERE preauth_id = ?",
                Long.class, sc.preauthId())).as("no decision head").isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM preauth_line_snapshots ls "
                + "JOIN preauth_decision_snapshots d ON d.id = ls.decision_snapshot_id "
                + "WHERE d.preauth_id = ?", Long.class, sc.preauthId()))
                .as("no line snapshot").isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM benefit_bucket_consumptions "
                + "WHERE preauth_id = ?", Long.class, sc.preauthId()))
                .as("not even the bucket hold that succeeded before the failure").isZero();
        assertThat(status(sc.preauthId())).as("status untouched").isEqualTo(statusBefore);
        assertThat(jdbc.queryForObject("SELECT version FROM pre_authorizations WHERE id = ?",
                Long.class, sc.preauthId())).as("version untouched").isEqualTo(versionBefore);
    }

    // ── the remaining consumption bases ─────────────────────────────────

    @Test
    void aBucketWithOnlyAnOccurrenceLimitStillHoldsItsVisit() {
        Scenario sc = scenario("NULL", 3, "1000.00", 80, 2);

        service.approveAndReserve(sc.preauthId(), 0L, "reviewer");

        // Previously invisible: the monetary resolver declines a bucket with
        // no amount, and it was the approval path's only source of ceilings --
        // so two approvals could each hold the last visit.
        Integer heldTimes = jdbc.queryForObject(
                "SELECT times_consumed FROM benefit_bucket_consumptions "
                        + "WHERE preauth_id = ? AND bucket_id = ? AND status = 'RESERVED'",
                Integer.class, sc.preauthId(), sc.bucketId());
        assertThat(heldTimes).as("a bucket may cap occurrences without capping money").isEqualTo(2);

        // And its snapshot must not describe money it never measured. A false
        // audit trail is worse than a missing one: nothing signals it is wrong
        // to whoever reconstructs this decision later.
        String basis = jdbc.queryForObject(
                "SELECT pls.amount_consumption_basis FROM preauth_line_limit_snapshots pls "
                        + "JOIN preauth_line_snapshots ls ON ls.id = pls.line_snapshot_id "
                        + "JOIN preauth_decision_snapshots d ON d.id = ls.decision_snapshot_id "
                        + "WHERE d.preauth_id = ? AND pls.bucket_id = ?",
                String.class, sc.preauthId(), sc.bucketId());
        String unit = jdbc.queryForObject(
                "SELECT pls.amount_unit FROM preauth_line_limit_snapshots pls "
                        + "JOIN preauth_line_snapshots ls ON ls.id = pls.line_snapshot_id "
                        + "JOIN preauth_decision_snapshots d ON d.id = ls.decision_snapshot_id "
                        + "WHERE d.preauth_id = ? AND pls.bucket_id = ?",
                String.class, sc.preauthId(), sc.bucketId());

        assertThat(basis).as("no monetary basis for a ceiling that measures no money").isNull();
        assertThat(unit).as("no monetary unit either").isNull();
    }

    @Test
    void anEligibleAmountBucketHoldsTheEligibleAmountNotTheCompanyShare() {
        Scenario sc = scenario("10000", null, "1000.00", 80, 1);
        jdbc.update("UPDATE benefit_limit_buckets SET consumption_basis = 'ELIGIBLE_AMOUNT' WHERE id = ?",
                sc.bucketId());

        service.approveAndReserve(sc.preauthId(), 0L, "reviewer");

        BigDecimal bucketHold = jdbc.queryForObject(
                "SELECT approved_amount FROM benefit_bucket_consumptions "
                        + "WHERE preauth_id = ? AND bucket_id = ? AND status = 'RESERVED'",
                BigDecimal.class, sc.preauthId(), sc.bucketId());
        BigDecimal generalHold = jdbc.queryForObject(
                "SELECT approved_amount FROM benefit_bucket_consumptions "
                        + "WHERE preauth_id = ? AND bucket_id IS NULL AND status = 'RESERVED'",
                BigDecimal.class, sc.preauthId());

        // Each scope holds ITS OWN measure of one decision: the bucket counts
        // the eligible amount, the general ceiling counts the insurer's money.
        assertThat(bucketHold).isEqualByComparingTo("1000.00");
        assertThat(generalHold).isEqualByComparingTo("800.00");
    }
}
