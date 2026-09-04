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
 * Runs V216 through V220 in one real Flyway pass, on a fresh database, over
 * row shapes written before any of them existed -- proving the migration
 * FILES themselves compose correctly end to end, not a hand-patched copy of
 * one of them. This is deliberately separate from
 * ClaimsHistoricalPolicySnapshotAcrossV217MigrationTest and
 * ClaimsHistoricalContextStatusAcrossV219MigrationTest (which each isolate
 * one migration's behavior): waad_production_review_20260903 had V219's
 * trigger function hand-corrected via CREATE OR REPLACE after the fact
 * (2026-09-04), so verifying against that database alone would not catch a
 * mistake in what V219__claims_historical_context_status.sql itself
 * contains. This test only ever touches disposable Testcontainers
 * databases.
 */
class ClaimsHistoricalContextV216ThroughV220CleanMigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("v216_v220_root").withUsername("test_user").withPassword("test_password");

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

    private String stringScalar(String url, String sql) throws SQLException {
        try (Connection c = conn(url); Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
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

    private record World(long employerId, long policyId, long memberId, long employerAssignmentId,
            long policyAssignmentId, long providerId, long claimId, long claimLineId) {}

    private World world(Connection c, String tag, String serviceDate,
            String employerAssignStart, String policyAssignStart) throws SQLException {
        long employerId = id(c, "INSERT INTO employers (code, name) VALUES ('V216-220-" + tag
                + "', 'Co " + tag + "') RETURNING id");
        long policyId = id(c, "INSERT INTO benefit_policies (name, policy_code, employer_id, annual_limit, "
                + "default_coverage_percent, start_date, end_date, status, active) VALUES ('P-" + tag
                + "', 'POL-216-220-" + tag + "', " + employerId
                + ", 10000, 80, CURRENT_DATE - 400, CURRENT_DATE + 400, 'ACTIVE', true) RETURNING id");
        long memberId = id(c, "INSERT INTO members (employer_id, full_name, benefit_policy_id, card_number, "
                + "barcode, status, active) VALUES (" + employerId + ", 'Member " + tag + "', " + policyId
                + ", 'C" + tag + "', 'C" + tag + "', 'ACTIVE', true) RETURNING id");
        long employerAssignmentId = id(c, "INSERT INTO member_employer_assignments (member_id, employer_id, "
                + "assignment_start_date, assignment_end_date, assignment_reason, assignment_source) VALUES ("
                + memberId + ", " + employerId + ", " + employerAssignStart + ", NULL, 'test', 'BACKFILL') "
                + "RETURNING id");
        long policyAssignmentId = id(c, "INSERT INTO member_policy_assignments (member_id, policy_id, "
                + "assignment_start_date, assignment_end_date) VALUES (" + memberId + ", " + policyId + ", "
                + policyAssignStart + ", NULL) RETURNING id");
        long providerId = id(c, "INSERT INTO providers (name, license_number, provider_type) VALUES "
                + "('Prov " + tag + "', 'LIC-" + tag + "', 'CLINIC') RETURNING id");
        long visitId = id(c, "INSERT INTO visits (member_id, provider_id, visit_date) VALUES ("
                + memberId + ", " + providerId + ", " + serviceDate + ") RETURNING id");
        long claimId = id(c, "INSERT INTO claims (claim_number, member_id, provider_id, visit_id, "
                + "service_date, requested_amount, status, claim_context_code) VALUES ('CLM-" + tag + "', "
                + memberId + ", " + providerId + ", " + visitId + ", " + serviceDate
                + ", 300, 'APPROVED', 'OUTPATIENT') RETURNING id");
        long claimLineId = id(c, "INSERT INTO claim_lines (claim_id, service_code, quantity, "
                + "unit_price, total_price) VALUES (" + claimId + ", 'SVC-" + tag
                + "', 1, 300.00, 300.00) RETURNING id");
        return new World(employerId, policyId, memberId, employerAssignmentId, policyAssignmentId,
                providerId, claimId, claimLineId);
    }

    @Test
    void v216ThroughV220ComposeCorrectlyOverRealisticPreExistingData() throws SQLException {
        String url = freshDatabase("v216_220_clean");
        migrateTo(url, "216");

        World resolvable;
        World lateAssignment;
        try (Connection c = conn(url)) {
            // Resolvable by serviceDate: assignment covers it, no snapshot
            // needed (mirrors a DRAFT claim never adjudicated).
            resolvable = world(c, "R", "CURRENT_DATE", "CURRENT_DATE - 400", "CURRENT_DATE - 400");
            // The exact shape found in waad_production_review_20260903: the
            // member's only assignment starts AFTER the claim's serviceDate.
            lateAssignment = world(c, "L", "CURRENT_DATE - 30", "CURRENT_DATE - 5", "CURRENT_DATE - 5");
        }

        // The real deployment path: one Flyway pass, straight through, no
        // hand-editing of any intermediate state.
        migrateTo(url, "220");

        // ── V217: backfill ──────────────────────────────────────────────
        assertThat(nullableScalar(url, "SELECT policy_id FROM claims WHERE id = " + resolvable.claimId()))
                .isEqualTo(resolvable.policyId());
        assertThat(nullableScalar(url, "SELECT policy_id FROM claims WHERE id = " + lateAssignment.claimId()))
                .as("no honest attribution exists for the late-assignment claim").isNull();

        // ── V218: FKs actually VALIDATED, not just present ─────────────
        assertThat(scalar(url, "SELECT COUNT(*) FROM pg_constraint WHERE conname IN "
                + "('fk_claims_policy', 'fk_claims_policy_assignment', 'fk_claims_employer_assignment') "
                + "AND convalidated = true")).isEqualTo(3L);

        // ── V219: status backfill ───────────────────────────────────────
        assertThat(stringScalar(url, "SELECT historical_context_status FROM claims WHERE id = "
                + resolvable.claimId())).isEqualTo("RESOLVED");
        assertThat(stringScalar(url, "SELECT historical_context_status FROM claims WHERE id = "
                + lateAssignment.claimId())).isEqualTo("LEGACY_UNRESOLVED");

        // ── V219: guard behavior from the FILE itself, not a hand patch ──
        final long legacyClaimId = lateAssignment.claimId();
        final long somePolicyId = lateAssignment.policyId();
        assertThatThrownBy(() -> exec(url, "INSERT INTO claims (claim_number, member_id, provider_id, "
                + "visit_id, service_date, requested_amount, status, claim_context_code, "
                + "historical_context_status) SELECT 'NEW-CLM', member_id, provider_id, visit_id, "
                + "service_date, 50, 'DRAFT', 'OUTPATIENT', 'LEGACY_UNRESOLVED' FROM claims WHERE id = "
                + legacyClaimId))
                .as("no new row may ever start LEGACY_UNRESOLVED")
                .hasMessageContaining("reserved for rows the V219 migration found already unresolved");
        assertThatThrownBy(() -> exec(url, "UPDATE claims SET policy_id = " + somePolicyId
                + " WHERE id = " + legacyClaimId))
                .as("no partial edit while LEGACY_UNRESOLVED -- the file itself, not a hand patch")
                .hasMessageContaining("may only change as part of a single, validated transition to RESOLVED");

        final long resolvedClaimId = resolvable.claimId();
        final long otherPolicyId = lateAssignment.policyId();
        assertThatThrownBy(() -> exec(url, "UPDATE claims SET policy_id = " + otherPolicyId
                + " WHERE id = " + resolvedClaimId))
                .as("RESOLVED is permanent").hasMessageContaining("permanent once RESOLVED");

        // ── V220: partial indexes exist ─────────────────────────────────
        assertThat(scalar(url, "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'claims' AND indexname IN "
                + "('idx_claims_legacy_unresolved_batch', 'idx_claims_legacy_unresolved_member')"))
                .isEqualTo(2L);
    }
}
