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
 * V217 records, on the claim row itself, WHICH policy/policy-assignment/
 * employer-assignment applied when the claim was created -- the same
 * serviceDate-dated question MemberContextResolver already answers to
 * authorize the claim, but that answer was previously discarded after
 * authorization instead of being kept as a historical fact (see
 * V217__claims_historical_policy_snapshot.sql's header).
 *
 * Following the project's established migration-test discipline (see
 * ReservationNamesPolicyAssignmentAcrossV187MigrationTest): this project has
 * twice shipped a migration that passed against empty tables and would have
 * failed on production data. So every test here migrates to 216, writes row
 * shapes that mirror what a real claim/snapshot/assignment history actually
 * looks like, and only then runs 217.
 *
 * Each test gets its own database -- Flyway cannot migrate backwards, so a
 * shared one would leave every test after the first already at 217.
 */
class ClaimsHistoricalPolicySnapshotAcrossV217MigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("v217_root").withUsername("test_user").withPassword("test_password");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

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

    private Long nullableScalar(String url, String sql) throws SQLException {
        try (Connection c = conn(url); Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            long value = rs.getLong(1);
            return rs.wasNull() ? null : value;
        }
    }

    private long scalar(String url, String sql) throws SQLException {
        try (Connection c = conn(url); Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void migrateTo(String url, String version) {
        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target(version).load().migrate();
    }

    /** One member with employer/policy assignment history and a claim, written pre-217. */
    private record World(long employerId, long policyId, long memberId, long employerAssignmentId,
            long policyAssignmentId, long providerId, long claimId, long claimLineId) {}

    private long insertEmployer(Connection c, String tag) throws SQLException {
        return id(c, "INSERT INTO employers (code, name) VALUES ('V217-" + tag
                + "', 'V217 Co " + tag + "') RETURNING id");
    }

    private long insertPolicy(Connection c, long employerId, String tag) throws SQLException {
        return id(c, "INSERT INTO benefit_policies (name, policy_code, employer_id, annual_limit, "
                + "default_coverage_percent, start_date, end_date, status, active) VALUES ('V217P-" + tag
                + "', 'V217POL-" + tag + "', " + employerId
                + ", 10000, 80, CURRENT_DATE - 400, CURRENT_DATE + 400, 'ACTIVE', true) RETURNING id");
    }

    private long insertMember(Connection c, long employerId, long policyId, String tag) throws SQLException {
        return id(c, "INSERT INTO members (employer_id, full_name, benefit_policy_id, card_number, "
                + "barcode, status, active) VALUES (" + employerId + ", 'V217 Member', " + policyId
                + ", 'V217C" + tag + "', 'V217C" + tag + "', 'ACTIVE', true) RETURNING id");
    }

    /**
     * A claim's full pre-217 world: employer/policy assignment history that
     * covers serviceDate, a claim, and one claim line. The line-limit
     * snapshot (the primary backfill source) is written separately per test,
     * since whether it exists -- and whether it agrees -- is exactly what
     * each test varies.
     */
    private World world(Connection c, String tag, String serviceDate,
            String employerAssignStart, String employerAssignEnd,
            String policyAssignStart, String policyAssignEnd) throws SQLException {
        long employerId = insertEmployer(c, tag);
        long policyId = insertPolicy(c, employerId, tag);
        long memberId = insertMember(c, employerId, policyId, tag);
        long employerAssignmentId = id(c, "INSERT INTO member_employer_assignments (member_id, employer_id, "
                + "assignment_start_date, assignment_end_date, assignment_reason, assignment_source) VALUES ("
                + memberId + ", " + employerId + ", " + employerAssignStart + ", " + employerAssignEnd
                + ", 'test', 'BACKFILL') RETURNING id");
        long policyAssignmentId = id(c, "INSERT INTO member_policy_assignments (member_id, policy_id, "
                + "assignment_start_date, assignment_end_date) VALUES (" + memberId + ", " + policyId + ", "
                + policyAssignStart + ", " + policyAssignEnd + ") RETURNING id");
        long providerId = id(c, "INSERT INTO providers (name, license_number, provider_type) VALUES "
                + "('V217 Prov " + tag + "', 'V217LIC-" + tag + "', 'CLINIC') RETURNING id");
        long visitId = id(c, "INSERT INTO visits (member_id, provider_id, visit_date) VALUES ("
                + memberId + ", " + providerId + ", " + serviceDate + ") RETURNING id");
        long claimId = id(c, "INSERT INTO claims (claim_number, member_id, provider_id, visit_id, "
                + "service_date, requested_amount, status, claim_context_code) VALUES ('V217CLM-" + tag + "', "
                + memberId + ", " + providerId + ", " + visitId + ", " + serviceDate
                + ", 300, 'APPROVED', 'OUTPATIENT') RETURNING id");
        long claimLineId = id(c, "INSERT INTO claim_lines (claim_id, service_code, quantity, "
                + "unit_price, total_price) VALUES (" + claimId + ", 'V217SVC-" + tag
                + "', 1, 300.00, 300.00) RETURNING id");
        return new World(employerId, policyId, memberId, employerAssignmentId, policyAssignmentId,
                providerId, claimId, claimLineId);
    }

    private void insertAgreeingSnapshot(Connection c, World w, String tag) throws SQLException {
        c.createStatement().executeUpdate(
                "INSERT INTO claim_line_limit_snapshots (claim_id, claim_line_id, calculation_version, "
                        + "benefit_scope_type, limit_semantic_key, bucket_id, policy_id, "
                        + "member_policy_assignment_id, source_type, period_type, period_start, period_end, "
                        + "effective_limit, consumed_before, reserved_before, available_before, "
                        + "line_settlement_base, line_inside_limit, limit_consumption, patient_limit_excess, "
                        + "available_after, is_binding, consumption_order) VALUES (" + w.claimId() + ", "
                        + w.claimLineId() + ", 1, 'POLICY_GENERAL', 'GENERAL-" + tag + "', NULL, "
                        + w.policyId() + ", " + w.policyAssignmentId() + ", 'POLICY_DEFAULT', 'ANNUAL', "
                        + "CURRENT_DATE - 30, CURRENT_DATE + 335, 10000, 0, 0, 10000, 300, 300, 300, 0, "
                        + "9700, false, 1)");
    }

    @Test
    void backfillsFromAnAgreeingLimitSnapshot() throws SQLException {
        String url = freshDatabase("v217_snapshot_agree");
        migrateTo(url, "216");

        World w;
        try (Connection c = conn(url)) {
            w = world(c, "A", "CURRENT_DATE",
                    "CURRENT_DATE - 400", "NULL", "CURRENT_DATE - 400", "NULL");
            insertAgreeingSnapshot(c, w, "A");
        }

        migrateTo(url, "217");

        assertThat(nullableScalar(url, "SELECT policy_id FROM claims WHERE id = " + w.claimId()))
                .as("policy_id backfilled from the one snapshot that named it")
                .isEqualTo(w.policyId());
        assertThat(nullableScalar(url, "SELECT policy_assignment_id FROM claims WHERE id = " + w.claimId()))
                .as("policy_assignment_id backfilled alongside it")
                .isEqualTo(w.policyAssignmentId());
        assertThat(scalar(url, "SELECT COUNT(*) FROM claims_historical_context_backfill_gaps WHERE claim_id = "
                + w.claimId() + " AND missing IN ('policy_id', 'policy_assignment_id')"))
                .as("a clean backfill logs no gap for either column")
                .isZero();
    }

    @Test
    void fallsBackToResolvingByServiceDateWhenNoSnapshotExists() throws SQLException {
        String url = freshDatabase("v217_no_snapshot_single_match");
        migrateTo(url, "216");

        World w;
        try (Connection c = conn(url)) {
            // No claim_line_limit_snapshots row at all -- e.g. a DRAFT claim
            // that was never approved. The only source left is the member's
            // dated assignment history, exactly what MemberContextResolver
            // would have answered at creation time.
            w = world(c, "B", "CURRENT_DATE",
                    "CURRENT_DATE - 400", "NULL", "CURRENT_DATE - 400", "NULL");
        }

        migrateTo(url, "217");

        assertThat(nullableScalar(url, "SELECT policy_id FROM claims WHERE id = " + w.claimId()))
                .isEqualTo(w.policyId());
        assertThat(nullableScalar(url, "SELECT policy_assignment_id FROM claims WHERE id = " + w.claimId()))
                .isEqualTo(w.policyAssignmentId());
        assertThat(nullableScalar(url, "SELECT employer_assignment_id FROM claims WHERE id = " + w.claimId()))
                .isEqualTo(w.employerAssignmentId());
    }

    @Test
    void refusesToGuessWhenTheAssignmentStartsAfterServiceDate() throws SQLException {
        String url = freshDatabase("v217_late_assignment");
        migrateTo(url, "216");

        World w;
        try (Connection c = conn(url)) {
            // The exact V216-adjacent scenario found reviewing the restored
            // production data (2026-09-03): a member whose only assignment
            // starts AFTER the claim's serviceDate -- because it was
            // imported late and V216 deliberately skips repairing members
            // who already have claims. No assignment covers serviceDate, so
            // there is nothing honest to backfill.
            w = world(c, "C", "CURRENT_DATE - 30",
                    "CURRENT_DATE - 5", "NULL", "CURRENT_DATE - 5", "NULL");
        }

        migrateTo(url, "217");

        assertThat(nullableScalar(url, "SELECT policy_id FROM claims WHERE id = " + w.claimId())).isNull();
        assertThat(nullableScalar(url, "SELECT policy_assignment_id FROM claims WHERE id = " + w.claimId()))
                .isNull();
        assertThat(nullableScalar(url, "SELECT employer_assignment_id FROM claims WHERE id = " + w.claimId()))
                .isNull();
        assertThat(scalar(url, "SELECT COUNT(*) FROM claims_historical_context_backfill_gaps WHERE claim_id = "
                + w.claimId())).as("both gaps are logged, not guessed").isEqualTo(2L);
    }

    @Test
    void refusesToGuessWhenSnapshotsDisagreeOnThePolicy() throws SQLException {
        String url = freshDatabase("v217_snapshot_disagree");
        migrateTo(url, "216");

        World w;
        long otherPolicyId;
        try (Connection c = conn(url)) {
            w = world(c, "D", "CURRENT_DATE",
                    "CURRENT_DATE - 400", "NULL", "CURRENT_DATE - 400", "NULL");
            insertAgreeingSnapshot(c, w, "D1");
            // A second line's snapshot naming a DIFFERENT policy_id for the
            // same claim -- e.g. a correction cycle. Two disagreeing
            // snapshot rows must not resolve to either guess.
            otherPolicyId = insertPolicy(c, w.employerId(), "D2");
            long claimLine2 = id(c, "INSERT INTO claim_lines (claim_id, service_code, quantity, "
                    + "unit_price, total_price) VALUES (" + w.claimId() + ", 'V217SVC-D2', 1, 100.00, 100.00) "
                    + "RETURNING id");
            c.createStatement().executeUpdate(
                    "INSERT INTO claim_line_limit_snapshots (claim_id, claim_line_id, calculation_version, "
                            + "benefit_scope_type, limit_semantic_key, bucket_id, policy_id, source_type, "
                            + "period_type, period_start, period_end, effective_limit, consumed_before, "
                            + "reserved_before, available_before, line_settlement_base, "
                            + "line_inside_limit, limit_consumption, patient_limit_excess, available_after, "
                            + "is_binding, consumption_order) VALUES (" + w.claimId() + ", " + claimLine2
                            + ", 1, 'POLICY_GENERAL', 'GENERAL-D2', NULL, " + otherPolicyId
                            + ", 'POLICY_DEFAULT', 'ANNUAL', CURRENT_DATE - 30, CURRENT_DATE + 335, 10000, 0, "
                            + "0, 10000, 100, 100, 100, 0, 9900, false, 1)");
        }

        migrateTo(url, "217");

        // Falls through to the by-serviceDate resolver, which still
        // unambiguously names the ORIGINAL policy via the member's dated
        // assignment -- disagreement in the snapshots does not corrupt that
        // independent source.
        assertThat(nullableScalar(url, "SELECT policy_id FROM claims WHERE id = " + w.claimId()))
                .isEqualTo(w.policyId());
        assertThat(nullableScalar(url, "SELECT policy_id FROM claims WHERE id = " + w.claimId()))
                .isNotEqualTo(otherPolicyId);
    }

    @Test
    void theImmutabilityGuardAllowsEditsWhileDraftAndBlocksThemAfter() throws SQLException {
        String url = freshDatabase("v217_immutability");
        migrateTo(url, "216");

        World w;
        long otherPolicyId;
        try (Connection c = conn(url)) {
            w = world(c, "E", "CURRENT_DATE",
                    "CURRENT_DATE - 400", "NULL", "CURRENT_DATE - 400", "NULL");
            otherPolicyId = insertPolicy(c, w.employerId(), "E2");
            c.createStatement().executeUpdate("UPDATE claims SET status = 'DRAFT' WHERE id = " + w.claimId());
        }

        migrateTo(url, "217");

        // Still DRAFT: the snapshot columns may be corrected freely -- e.g.
        // ClaimMapper recalculating on a line edit before submission.
        exec(url, "UPDATE claims SET policy_id = " + otherPolicyId + " WHERE id = " + w.claimId());
        assertThat(nullableScalar(url, "SELECT policy_id FROM claims WHERE id = " + w.claimId()))
                .isEqualTo(otherPolicyId);

        exec(url, "UPDATE claims SET status = 'APPROVED' WHERE id = " + w.claimId());

        // Left as-is: setting the SAME value again must not trip the guard --
        // only a real change is forbidden.
        exec(url, "UPDATE claims SET policy_id = " + otherPolicyId + " WHERE id = " + w.claimId());

        final long claimId = w.claimId();
        final long policyId = w.policyId();
        assertThatThrownBy(() -> exec(url, "UPDATE claims SET policy_id = " + policyId
                + " WHERE id = " + claimId))
                .as("once the claim leaves DRAFT/NEEDS_CORRECTION, its historical snapshot is fact, not data")
                .hasMessageContaining("immutable once the claim leaves DRAFT/NEEDS_CORRECTION");
    }

    @Test
    void theImmutabilityGuardReopensForNeedsCorrection() throws SQLException {
        String url = freshDatabase("v217_needs_correction");
        migrateTo(url, "216");

        World w;
        long otherPolicyId;
        try (Connection c = conn(url)) {
            w = world(c, "F", "CURRENT_DATE",
                    "CURRENT_DATE - 400", "NULL", "CURRENT_DATE - 400", "NULL");
            otherPolicyId = insertPolicy(c, w.employerId(), "F2");
            c.createStatement().executeUpdate(
                    "UPDATE claims SET status = 'NEEDS_CORRECTION' WHERE id = " + w.claimId());
        }

        migrateTo(url, "217");

        // A returned-for-correction claim is, per the state machine's own
        // model, editable again -- the guard must not treat it as final.
        exec(url, "UPDATE claims SET policy_id = " + otherPolicyId + " WHERE id = " + w.claimId());
        assertThat(nullableScalar(url, "SELECT policy_id FROM claims WHERE id = " + w.claimId()))
                .isEqualTo(otherPolicyId);
    }
}
