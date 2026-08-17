package com.waad.tba.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * V187 records WHICH enrollment period a reservation was posted under.
 *
 * On real rows, deliberately. This project has twice shipped a migration that
 * passed every test against empty tables and would have failed on production
 * data (V174's backfill against the append-only trigger, V180's). So each test
 * here migrates to 186, writes the row shapes production actually holds, and
 * only then runs 187.
 *
 * Each test gets its OWN database. Flyway cannot migrate backwards, so a
 * shared one would leave every test after the first running against a schema
 * that was already at 187 -- the "before" half of the proof would silently
 * stop happening while the tests kept passing.
 *
 * The shapes that matter are fixed by V174: a PREAUTH row is RESERVED or
 * REVERSED and nothing else, a CLAIM row is COMMITTED or REVERSED, and
 * PREAUTH+COMMITTED is structurally impossible. So the backfill has exactly
 * two populations to reach -- the holds, and the compensating movements that
 * point at them -- and the CHECK it then adds is what proves it missed
 * neither.
 */
class ReservationNamesPolicyAssignmentAcrossV187MigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("v187_root").withUsername("test_user").withPassword("test_password");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    /** A database of its own, so this test's "before 187" really is before. */
    private String freshDatabase(String name) throws SQLException {
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement st = c.createStatement()) {
            st.executeUpdate("CREATE DATABASE " + name);
        }
        return POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + name);
    }

    private Connection conn(String url) throws SQLException {
        return DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void exec(String url, String sql) throws SQLException {
        try (Connection c = conn(url); Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private long id(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private long scalar(String url, String sql) throws SQLException {
        try (Connection c = conn(url); Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private Long nullableScalar(String url, String sql) throws SQLException {
        try (Connection c = conn(url); Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            long value = rs.getLong(1);
            return rs.wasNull() ? null : value;
        }
    }

    private void migrateTo(String url, String version) {
        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target(version).load().migrate();
    }

    /** Ids of one complete pre-authorization world, written before V187 runs. */
    private record World(long memberId, long policyId, long assignmentId, long bucketId,
            long preauthId, long preauthLineId, long claimId, long claimLineId) {}

    /**
     * Everything a real reservation hangs off. Built through plain SQL because
     * the point is to reproduce rows that already exist in a production
     * database, not to exercise the writing services.
     *
     * @param snapshotNamesAssignment whether the decision snapshot records the
     *                                assignment -- false reproduces the legacy
     *                                rows written before V172 added it
     */
    private World legacyWorld(Connection c, String tag, boolean snapshotNamesAssignment)
            throws SQLException {
        long employerId = id(c, "INSERT INTO employers (code, name) VALUES ('V187-" + tag
                + "', 'V187 Co " + tag + "') RETURNING id");
        long policyId = id(c, "INSERT INTO benefit_policies (name, policy_code, employer_id, annual_limit, "
                + "default_coverage_percent, start_date, end_date, status, active) VALUES ('V187P-" + tag
                + "', 'V187POL-" + tag + "', " + employerId
                + ", 10000, 80, CURRENT_DATE - 30, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id");
        long memberId = id(c, "INSERT INTO members (employer_id, full_name, benefit_policy_id, card_number, "
                + "barcode, status, active) VALUES (" + employerId + ", 'V187 Member', " + policyId
                + ", 'V187C" + tag + "', 'V187C" + tag + "', 'ACTIVE', true) RETURNING id");
        long assignmentId = id(c, "INSERT INTO member_policy_assignments (member_id, policy_id, "
                + "assignment_start_date, assignment_end_date) VALUES (" + memberId + ", " + policyId
                + ", CURRENT_DATE - 30, NULL) RETURNING id");
        long groupId = id(c, "INSERT INTO benefit_groups (policy_id, code, name_ar, aggregation_mode) "
                + "VALUES (" + policyId + ", 'V187G-" + tag + "', 'مجموعة', 'INDIVIDUAL') RETURNING id");
        long bucketId = id(c, "INSERT INTO benefit_limit_buckets (policy_id, benefit_group_id, code, "
                + "name_ar, amount_limit, period_type, counting_method, consumption_basis, active) VALUES ("
                + policyId + ", " + groupId + ", 'V187B-" + tag
                + "', 'وعاء', 5000, 'ANNUAL', 'EACH_UNIT', 'COMPANY_SHARE', true) RETURNING id");
        long providerId = id(c, "INSERT INTO providers (name, license_number, provider_type) VALUES "
                + "('V187 Prov " + tag + "', 'V187LIC-" + tag + "', 'CLINIC') RETURNING id");
        long preauthId = id(c, "INSERT INTO pre_authorizations (member_id, policy_id, provider_id, status, "
                + "request_date, expected_service_date, created_at, updated_at) VALUES (" + memberId + ", "
                + policyId + ", " + providerId
                + ", 'APPROVED', now(), CURRENT_DATE, now(), now()) RETURNING id");
        long preauthLineId = id(c, "INSERT INTO pre_authorization_lines (pre_authorization_id, "
                + "service_name, contract_price, requested_amount, requested_quantity, "
                + "approved_quantity) VALUES (" + preauthId + ", 'خدمة', 300, 300, 1, 1) RETURNING id");

        // The decision snapshot is where the assignment is recorded today, and
        // therefore the only place the backfill can read it from. It is
        // append-only, so a test that needs it absent must write it absent --
        // nulling it afterwards is rejected by the ledger's own guard.
        id(c, "INSERT INTO preauth_decision_snapshots (preauth_id, calculation_version, member_id, "
                + "member_policy_assignment_id, policy_id, expected_service_date, provider_id, "
                + "requested_total, settlement_total, authorized_service_total, company_share_total, "
                + "decision_status, coverage_outcome, decided_by, idempotency_key) VALUES ("
                + preauthId + ", 1, " + memberId + ", "
                + (snapshotNamesAssignment ? String.valueOf(assignmentId) : "NULL") + ", " + policyId
                + ", CURRENT_DATE, " + providerId
                + ", 300, 300, 300, 300, 'APPROVED', 'FULLY_COVERED', 'tester', 'V187-SNAP-"
                + tag + "') RETURNING id");

        long visitId = id(c, "INSERT INTO visits (member_id, provider_id, visit_date) VALUES ("
                + memberId + ", " + providerId + ", CURRENT_DATE) RETURNING id");
        long claimId = id(c, "INSERT INTO claims (claim_number, member_id, provider_id, visit_id, "
                + "service_date, requested_amount, status) VALUES ('V187CLM-" + tag + "', " + memberId
                + ", " + providerId + ", " + visitId + ", CURRENT_DATE, 300, 'APPROVED') RETURNING id");
        long claimLineId = id(c, "INSERT INTO claim_lines (claim_id, service_code, quantity, "
                + "unit_price, total_price) VALUES (" + claimId + ", 'V187SVC-" + tag
                + "', 1, 300.00, 300.00) RETURNING id");

        return new World(memberId, policyId, assignmentId, bucketId, preauthId, preauthLineId,
                claimId, claimLineId);
    }

    private String reservationSql(World w, String key, String amount, String assignment) {
        return "INSERT INTO benefit_bucket_consumptions (policy_id, member_id, bucket_id, preauth_id, "
                + "preauth_line_id, " + (assignment == null ? "" : "member_policy_assignment_id, ")
                + "source_type, limit_scope, period_start, period_end, approved_amount, times_consumed, "
                + "status, calculation_version, idempotency_key) VALUES ("
                + w.policyId() + ", " + w.memberId() + ", " + w.bucketId() + ", " + w.preauthId() + ", "
                + w.preauthLineId() + ", " + (assignment == null ? "" : assignment + ", ")
                + "'PREAUTH', 'BUCKET', CURRENT_DATE - 10, CURRENT_DATE + 355, " + amount
                + ", 0, 'RESERVED', 1, '" + key + "') RETURNING id";
    }

    private String releaseSql(World w, long originalId, String key, String amount, String assignment,
            String reason) {
        return "INSERT INTO benefit_bucket_consumptions (policy_id, member_id, bucket_id, preauth_id, "
                + "preauth_line_id, " + (assignment == null ? "" : "member_policy_assignment_id, ")
                + "source_type, limit_scope, period_start, period_end, approved_amount, times_consumed, "
                + "status, calculation_version, idempotency_key, reversal_of_id, reversal_reason, "
                + "reversed_at) VALUES ("
                + w.policyId() + ", " + w.memberId() + ", " + w.bucketId() + ", " + w.preauthId() + ", "
                + w.preauthLineId() + ", " + (assignment == null ? "" : assignment + ", ")
                + "'PREAUTH', 'BUCKET', CURRENT_DATE - 10, CURRENT_DATE + 355, " + amount
                + ", 0, 'REVERSED', 1, '" + key + "', " + originalId + ", '" + reason + "', now())";
    }

    @Test
    void theBackfillReachesBothTheHoldAndTheMovementThatCompensatesIt() throws SQLException {
        String url = freshDatabase("v187_backfill");
        migrateTo(url, "186");

        World w;
        long holdId;
        long partialReleaseId;
        long claimRowId;
        try (Connection c = conn(url)) {
            w = legacyWorld(c, "A", true);

            // A hold, and a PARTIAL release already posted against it. Both are
            // PREAUTH rows, and the CHECK V187 adds refuses either without an
            // assignment -- so a backfill that reached only the holds would
            // pass its own validation block and then fail on the constraint.
            holdId = id(c, reservationSql(w, "V187-HOLD-A", "300.00", null));
            partialReleaseId = id(c, releaseSql(w, holdId, "V187-REL-A", "100.00", null,
                    "PREAUTH_RELEASE") + " RETURNING id");

            // A claim's own consumption, which names no pre-authorization and
            // must keep naming no assignment.
            claimRowId = id(c, "INSERT INTO benefit_bucket_consumptions (policy_id, member_id, "
                    + "bucket_id, claim_id, claim_line_id, source_type, limit_scope, period_start, "
                    + "period_end, approved_amount, times_consumed, status, calculation_version, "
                    + "idempotency_key) VALUES (" + w.policyId() + ", " + w.memberId() + ", "
                    + w.bucketId() + ", " + w.claimId() + ", " + w.claimLineId()
                    + ", 'CLAIM', 'BUCKET', CURRENT_DATE - 10, CURRENT_DATE + 355, 200.00, 0, "
                    + "'COMMITTED', 1, 'V187-CLAIM-A') RETURNING id");
        }

        migrateTo(url, "187");

        assertThat(nullableScalar(url, "SELECT member_policy_assignment_id FROM "
                + "benefit_bucket_consumptions WHERE id = " + holdId))
                .as("the hold names the enrollment period it was placed under")
                .isEqualTo(w.assignmentId());

        assertThat(nullableScalar(url, "SELECT member_policy_assignment_id FROM "
                + "benefit_bucket_consumptions WHERE id = " + partialReleaseId))
                .as("a compensating movement inherits its original's assignment")
                .isEqualTo(w.assignmentId());

        assertThat(nullableScalar(url, "SELECT member_policy_assignment_id FROM "
                + "benefit_bucket_consumptions WHERE id = " + claimRowId))
                .as("a claim's consumption belongs to no reservation and names no assignment")
                .isNull();
    }

    @Test
    void theAppendOnlyGuardIsBackInPlaceAfterTheBackfillSuspendedIt() throws SQLException {
        String url = freshDatabase("v187_guard");
        migrateTo(url, "186");

        long holdId;
        try (Connection c = conn(url)) {
            World w = legacyWorld(c, "B", true);
            holdId = id(c, reservationSql(w, "V187-HOLD-B", "300.00", null));
        }

        migrateTo(url, "187");

        // The migration disables the UPDATE trigger to write its own metadata.
        // Leaving it disabled would silently turn the ledger from append-only
        // into an ordinary mutable table, and nothing downstream would notice
        // until a row had already been rewritten.
        assertThat(scalar(url, "SELECT COUNT(*) FROM pg_trigger WHERE tgrelid = "
                + "'benefit_bucket_consumptions'::regclass AND tgname = "
                + "'trg_no_update_bucket_consumptions' AND tgenabled <> 'D'"))
                .as("the append-only guard must be enabled again").isEqualTo(1L);

        assertThatThrownBy(() -> exec(url, "UPDATE benefit_bucket_consumptions SET approved_amount = 1 "
                + "WHERE id = " + holdId))
                .as("and it must actually reject an update, not merely exist")
                .hasMessageContaining("append-only");
    }

    @Test
    void aReservationWhoseAssignmentCannotBeIdentifiedStopsTheMigration() throws SQLException {
        String url = freshDatabase("v187_unidentifiable");
        migrateTo(url, "186");

        try (Connection c = conn(url)) {
            // Written WITHOUT an assignment from the start: the snapshot table
            // is append-only, so the legacy shape has to be created, not
            // produced by editing a good row into a bad one.
            World w = legacyWorld(c, "C", false);
            id(c, reservationSql(w, "V187-HOLD-C", "300.00", null));
        }

        // Fail closed. Guessing an assignment here -- the member's current one,
        // say -- would attach a backdated hold to the wrong coverage period and
        // leave no trace that it was a guess.
        assertThatThrownBy(() -> migrateTo(url, "187"))
                .hasMessageContaining("cannot identify one policy assignment");
    }

    @Test
    void aConversionReleaseIsAVocabularyTheLedgerActuallyAccepts() throws SQLException {
        String url = freshDatabase("v187_vocabulary");
        migrateTo(url, "186");

        World w;
        long holdId;
        try (Connection c = conn(url)) {
            w = legacyWorld(c, "F", true);
            holdId = id(c, reservationSql(w, "V187-HOLD-F", "300.00", null));
        }

        migrateTo(url, "187");

        // The positive case, and the one that matters most: a conversion writes
        // a NEW reversal_reason, and that column is constrained to a fixed
        // vocabulary. The negative tests cannot see this -- a BEFORE ROW
        // trigger fires ahead of CHECK evaluation, so a row that trips the
        // trigger never reaches the constraint that would also have rejected
        // it. Without this assertion, every pre-authorized claim in production
        // would have failed at the moment it posted.
        exec(url, releaseSql(w, holdId, "V187-CONV-F", "300.00",
                String.valueOf(w.assignmentId()), "PREAUTH_CONVERSION_RELEASE"));

        assertThat(scalar(url, "SELECT COUNT(*) FROM benefit_bucket_consumptions WHERE reversal_of_id = "
                + holdId + " AND reversal_reason = 'PREAUTH_CONVERSION_RELEASE'")).isEqualTo(1L);
    }

    @Test
    void aReleaseMayNotBeFiledUnderADifferentAssignmentThanItsHold() throws SQLException {
        String url = freshDatabase("v187_release_assignment");
        migrateTo(url, "186");

        World w;
        long holdId;
        long otherAssignmentId;
        try (Connection c = conn(url)) {
            w = legacyWorld(c, "D", true);
            holdId = id(c, reservationSql(w, "V187-HOLD-D", "300.00", null));
            otherAssignmentId = id(c, "INSERT INTO member_policy_assignments (member_id, policy_id, "
                    + "assignment_start_date, assignment_end_date) VALUES (" + w.memberId() + ", "
                    + w.policyId() + ", CURRENT_DATE - 400, CURRENT_DATE - 31) RETURNING id");
        }

        migrateTo(url, "187");

        // The whole point of the column: a release naming a different
        // enrollment period would return the money to a coverage period that
        // never held it, and the arithmetic would still balance.
        final World world = w;
        final long finalHold = holdId;
        final long other = otherAssignmentId;
        assertThatThrownBy(() -> exec(url, releaseSql(world, finalHold, "V187-REL-D", "100.00",
                String.valueOf(other), "PREAUTH_CONVERSION_RELEASE")))
                .hasMessageContaining("must keep the policy assignment of its reservation");
    }

    @Test
    void aReservationMayNotNameSomeoneElsesEnrollmentPeriod() throws SQLException {
        String url = freshDatabase("v187_owner");
        migrateTo(url, "187");

        World w;
        long strangersAssignmentId;
        try (Connection c = conn(url)) {
            w = legacyWorld(c, "G", true);
            strangersAssignmentId = legacyWorld(c, "H", true).assignmentId();
        }

        // The FK and the NOT NULL rule are both satisfied here. Only the
        // ownership check stands between this row and a hold filed under a
        // different member's coverage period -- which would make the column
        // that distinguishes enrollment periods say nothing at all.
        final World world = w;
        final long stranger = strangersAssignmentId;
        assertThatThrownBy(() -> exec(url, reservationSql(world, "V187-HOLD-G", "50.00",
                String.valueOf(stranger))))
                .hasMessageContaining("may only name its own member");
    }

    @Test
    void aNewReservationWithoutAnAssignmentIsRefusedOutright() throws SQLException {
        String url = freshDatabase("v187_required");
        migrateTo(url, "187");

        World w;
        try (Connection c = conn(url)) {
            w = legacyWorld(c, "E", true);
        }

        final World world = w;
        assertThatThrownBy(() -> exec(url, reservationSql(world, "V187-HOLD-E", "50.00", null)))
                .as("after V187 a hold that names no enrollment period cannot be written at all")
                .hasMessageContaining("chk_preauth_consumption_has_policy_assignment");
    }
}
