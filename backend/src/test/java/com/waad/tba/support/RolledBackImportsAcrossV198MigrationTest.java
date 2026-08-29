package com.waad.tba.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * V198 upgrades a database that is already running, not an empty one.
 *
 * The interesting rows exist before the migration does: batches that completed
 * and were later reverted, whose logs still say COMPLETED because nothing used
 * to correct them. On a fresh schema there are none, so a migration verified
 * that way proves nothing about the case it was written for.
 *
 * Two separate things have to land together, and the reason they are in one
 * migration is that either alone leaves the system broken. Widening the CHECK
 * without backfilling leaves every past rollback still masquerading as
 * complete; backfilling without widening the CHECK fails on the first row.
 */
class RolledBackImportsAcrossV198MigrationTest {

    // Its own container: a migration checked against a database another test
    // already migrated is checking nothing.
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("migration_test_v198")
            .withUsername("test_user")
            .withPassword("test_password");

    @BeforeAll
    static void start() {
        POSTGRES.start();
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    @Test
    @DisplayName("a database at V197 with a reverted batch is corrected, and one with skips is left alone")
    void v198CorrectsRevertedBatchesAndLeavesPartialOnesAlone() throws Exception {
        migrateTo("197");

        try (Connection connection = DriverManager.getConnection(
                     POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {

            long reverted = importLog(statement, "v198-reverted");
            long revertedWithSkips = importLog(statement, "v198-skipped");
            long neverReverted = importLog(statement, "v198-intact");

            rollback(statement, reverted, 0);
            // One row could not be reverted, so part of that import survives.
            rollback(statement, revertedWithSkips, 2);

            // Before the migration every one of them reads as complete, which
            // is the state every existing database is in.
            assertThat(status(statement, reverted)).isEqualTo("COMPLETED");

            // And the column will not even accept the new value yet.
            assertThatThrownBy(() -> statement.executeUpdate(
                    "update member_import_logs set status='ROLLED_BACK' where id=" + neverReverted))
                    .as("the CHECK is what makes widening it part of this migration rather "
                            + "than an afterthought")
                    .isInstanceOf(SQLException.class);

            migrateTo(null);

            assertThat(status(statement, reverted))
                    .as("a batch whose rows were all deleted is not a completed import, and "
                            + "everything that reads this status -- the history screen, the "
                            + "idempotency guard -- is asking whether it still stands")
                    .isEqualTo("ROLLED_BACK");

            assertThat(status(statement, revertedWithSkips))
                    .as("part of this import survived the revert; calling the whole batch "
                            + "reverted would be a worse inaccuracy than the one being fixed")
                    .isEqualTo("COMPLETED");

            assertThat(status(statement, neverReverted))
                    .as("an import nobody reverted is untouched")
                    .isEqualTo("COMPLETED");

            assertThatCode(() -> statement.executeUpdate(
                    "update member_import_logs set status='ROLLED_BACK' where id=" + neverReverted))
                    .as("and the widened CHECK now admits the value the application writes")
                    .doesNotThrowAnyException();
        }
    }

    private long importLog(Statement statement, String batch) throws Exception {
        String id = batch + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        try (ResultSet rs = statement.executeQuery("insert into member_import_logs"
                + "(import_batch_id, file_name, total_rows, created_count, updated_count,"
                + " skipped_count, error_count, status, imported_by_username)"
                + " values('" + id + "', 'members.xlsx', 3, 3, 0, 0, 0, 'COMPLETED', 'tester')"
                + " returning id")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void rollback(Statement statement, long importLogId, int skipped) throws Exception {
        statement.executeUpdate("insert into member_import_rollbacks"
                + "(import_log_id, reason, performed_by, status, reverted_created_count,"
                + " reverted_updated_count, skipped_count, started_at, completed_at)"
                + " values(" + importLogId + ", 'أُرسل بالخطأ', 'tester', 'COMPLETED', 3, 0, "
                + skipped + ", now(), now())");
    }

    private String status(Statement statement, long importLogId) throws Exception {
        try (ResultSet rs = statement.executeQuery(
                "select status from member_import_logs where id=" + importLogId)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private void migrateTo(String target) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true);
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }
}
