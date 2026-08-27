package com.waad.tba.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * V168 corrects the idempotency key V167 originally shipped as part of
 * adc07211 (UNIQUE (employer_id, file_hash) WHERE status = 'COMPLETED' --
 * broken for NULL employer_id, and missing benefitPolicyId/headerRow/
 * clearOldMembers from the key). V167 itself is NOT edited here: it may
 * already be applied to a real development or test database (Flyway
 * validates checksums of already-applied migrations, so silently rewriting
 * a shipped migration would break every environment that ran it) -- the fix
 * is a new migration, V168, that drops V167's index and replaces it.
 *
 * This test proves V168 is safe against a database that already has
 * COMPLETED import-log rows in the shape V167 alone produced: migrates only
 * up to V167, inserts a row the way the FIRST version of
 * MemberExcelImportService.executeImport would have (file_hash + employer_id
 * populated, import_scope_hash not yet a column), then migrates to V168 and
 * confirms the row survives, the old index is gone, and the new
 * import_scope_hash index exists and actually enforces uniqueness.
 */
class MemberImportScopeHashMigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("migration_test_v168").withUsername("test_user").withPassword("test_password");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    void v168ReplacesTheEmployerFileHashIndexWithScopeHashWithoutLosingExistingRows() throws SQLException {
        Flyway upTo167 = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target("167")
                .load();
        upTo167.migrate();

        Long insertedId;
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {

            // Insert exactly the way V167-only code populated this table:
            // file_hash + employer_id, COMPLETED, no import_scope_hash column
            // exists yet.
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO member_import_logs "
                            + "(import_batch_id, file_hash, employer_id, status, created_at) "
                            + "VALUES (?, ?, ?, 'COMPLETED', now()) RETURNING id")) {
                ps.setString(1, "legacy-batch-" + System.nanoTime());
                ps.setString(2, "deadbeef" + System.nanoTime());
                ps.setLong(3, 42L);
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

            // The pre-existing row must have survived.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT status, employer_id FROM member_import_logs WHERE id = ?")) {
                ps.setLong(1, insertedId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).as("pre-existing V167-era row must survive V168").isTrue();
                    assertThat(rs.getString("status")).isEqualTo("COMPLETED");
                    assertThat(rs.getLong("employer_id")).isEqualTo(42L);
                }
            }

            assertThat(indexExists(conn, "member_import_logs", "uk_member_import_logs_employer_filehash_completed"))
                    .as("V167's original index must be dropped by V168")
                    .isFalse();

            assertThat(columnExists(conn, "member_import_logs", "import_scope_hash"))
                    .as("import_scope_hash column must exist after V168")
                    .isTrue();

            assertThat(indexExists(conn, "member_import_logs", "uk_member_import_logs_scope_completed"))
                    .as("V168's new scope-hash index must exist")
                    .isTrue();

            // The new index must actually enforce uniqueness: a second
            // COMPLETED row with the identical scope hash must be rejected.
            String scopeHash = "scope-" + System.nanoTime();
            insertCompletedWithScopeHash(conn, scopeHash);
            assertThat(insertCompletedWithScopeHashSucceeds(conn, scopeHash))
                    .as("a duplicate import_scope_hash on a second COMPLETED row must violate the unique index")
                    .isFalse();

            // NULL import_scope_hash rows (e.g. legacy rows never backfilled)
            // must NOT collide with each other -- the partial index excludes
            // NULLs entirely (WHERE ... AND import_scope_hash IS NOT NULL).
            assertThat(insertCompletedWithScopeHashSucceeds(conn, null)).isTrue();
            assertThat(insertCompletedWithScopeHashSucceeds(conn, null)).isTrue();
        }
    }

    private void insertCompletedWithScopeHash(Connection conn, String scopeHash) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO member_import_logs (import_batch_id, import_scope_hash, status, created_at) "
                        + "VALUES (?, ?, 'COMPLETED', now())")) {
            ps.setString(1, "batch-" + System.nanoTime());
            ps.setString(2, scopeHash);
            ps.executeUpdate();
        }
    }

    private boolean insertCompletedWithScopeHashSucceeds(Connection conn, String scopeHash) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO member_import_logs (import_batch_id, import_scope_hash, status, created_at) "
                        + "VALUES (?, ?, 'COMPLETED', now())")) {
            ps.setString(1, "batch-" + System.nanoTime());
            ps.setString(2, scopeHash);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
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

    private boolean indexExists(Connection conn, String table, String indexName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM pg_indexes WHERE tablename = ? AND indexname = ?")) {
            ps.setString(1, table);
            ps.setString(2, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
