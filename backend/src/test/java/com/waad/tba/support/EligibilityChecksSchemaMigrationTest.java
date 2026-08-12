package com.waad.tba.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * V166 drops eligibility_checks' dead legacy columns (is_eligible, check_date,
 * eligibility_reason, coverage_status, checked_by -- left over from an earlier
 * table design, never mapped by EligibilityCheck.java, and NOT NULL on
 * is_eligible in a way that made every real insert through the app fail --
 * see V166's own header comment and the audit-recorder fix in this same
 * change).
 *
 * This does not run against a fresh empty schema (PostgresIntegrationTestBase's
 * usual path already proves that works, implicitly, in every other
 * integration test). It runs against a schema migrated only up to V165, with
 * a row inserted the way the OLD V16-era design required -- populating the
 * legacy NOT NULL is_eligible column directly -- to prove V166 is safe against
 * a database that actually has rows in the shape the old schema demanded,
 * not just an empty table.
 */
class EligibilityChecksSchemaMigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("migration_test").withUsername("test_user").withPassword("test_password");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    void v166DropsLegacyColumnsWithoutLosingRowsOrCurrentColumns() throws SQLException {
        Flyway upTo165 = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target("165")
                .load();
        upTo165.migrate();

        Long insertedId;
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {

            // A member row is required by eligibility_checks' FK -- insert the
            // minimum needed via raw SQL rather than depending on JPA entities
            // (this test runs before the full Spring/Hibernate schema is even
            // relevant; only Flyway's SQL history matters here).
            long memberId = insertMinimalMember(conn);

            // Insert exactly the way the OLD (pre-V166) schema required:
            // both the legacy is_eligible column AND the current eligible
            // column populated, since is_eligible was NOT NULL.
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO eligibility_checks "
                            + "(member_id, is_eligible, request_id, check_timestamp, service_date, "
                            + "eligible, status, created_at) "
                            + "VALUES (?, ?, ?, now(), CURRENT_DATE, ?, 'ELIGIBLE', now()) RETURNING id")) {
                ps.setLong(1, memberId);
                ps.setBoolean(2, true);
                ps.setString(3, "legacy-row-" + System.nanoTime());
                ps.setBoolean(4, true);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    insertedId = rs.getLong(1);
                }
            }
        }

        Flyway latest = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();
        latest.migrate();

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {

            // The pre-existing row must have survived the migration untouched
            // in its still-current columns.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT member_id, eligible, status FROM eligibility_checks WHERE id = ?")) {
                ps.setLong(1, insertedId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("pre-existing row must survive V166").isTrue();
                    assertThat(rs.getBoolean("eligible")).isTrue();
                    assertThat(rs.getString("status")).isEqualTo("ELIGIBLE");
                }
            }

            // The dead legacy columns must actually be gone.
            for (String deadColumn : new String[] {
                    "is_eligible", "check_date", "eligibility_reason", "coverage_status", "checked_by"}) {
                assertThat(columnExists(conn, "eligibility_checks", deadColumn))
                        .as(deadColumn + " should have been dropped by V166")
                        .isFalse();
            }

            // The currently-mapped columns must still be present.
            for (String liveColumn : new String[] {"eligible", "check_timestamp", "request_id", "status"}) {
                assertThat(columnExists(conn, "eligibility_checks", liveColumn))
                        .as(liveColumn + " must NOT be dropped")
                        .isTrue();
            }

            // requestId uniqueness must be enforced by Postgres itself, not
            // merely assumed by the JPA entity or the service layer.
            assertThat(uniqueConstraintExists(conn, "eligibility_checks", "request_id"))
                    .as("request_id must be protected by a real unique constraint/index in Postgres")
                    .isTrue();
        }
    }

    private long insertMinimalMember(Connection conn) throws SQLException {
        long employerId;
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                        "INSERT INTO employers (code, name) VALUES ('EMP-MIGRATION-" + System.nanoTime()
                                + "', 'Migration Test Employer') RETURNING id")) {
            rs.next();
            employerId = rs.getLong(1);
        }
        try (Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                        "INSERT INTO members (employer_id, full_name, active) VALUES (" + employerId
                                + ", 'Migration Test Member', false) RETURNING id")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.columns WHERE table_name = ? AND column_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean uniqueConstraintExists(Connection conn, String table, String column) throws SQLException {
        // Covers both a UNIQUE constraint and a unique index -- Postgres
        // implements the former via the latter, and V16 declares both a
        // column-level UNIQUE and an explicit named constraint on request_id.
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM pg_indexes WHERE tablename = ? AND indexdef ILIKE '%UNIQUE%' AND indexdef ILIKE ?")) {
            ps.setString(1, table);
            ps.setString(2, "%" + column + "%");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
