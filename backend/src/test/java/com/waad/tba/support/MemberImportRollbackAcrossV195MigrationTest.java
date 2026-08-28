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

class MemberImportRollbackAcrossV195MigrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("migration_test_v195")
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
    void v195CreatesDurableConflictAwareAppendOnlyRollbackAudit() throws Exception {
        migrateTo("194");

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            long logId = returned(statement, "insert into member_import_logs(import_batch_id,status) "
                    + "values('v195-batch','COMPLETED') returning id");

            migrateTo(null);

            statement.executeUpdate("insert into member_import_batch_rows"
                    + "(import_log_id,member_id,action,previous_snapshot,imported_snapshot) values ("
                    + logId + ",991,'UPDATED','{\"fullName\":\"before\"}'::jsonb,"
                    + "'{\"fullName\":\"after\",\"attributes\":[]}'::jsonb)");
            assertThat(returned(statement, "select count(*) from member_import_batch_rows where member_id=991"))
                    .isEqualTo(1);

            assertThatThrownBy(() -> statement.executeUpdate(
                    "update member_import_batch_rows set member_id=992 where member_id=991"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("append-only");
            rollback(connection);
            assertThatThrownBy(() -> statement.executeUpdate(
                    "delete from member_import_batch_rows where member_id=991"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("append-only");
            rollback(connection);

            // Failure audit deliberately has no FK to the row locked by the
            // outer transaction; it must be independently durable.
            statement.executeUpdate("insert into member_import_rollbacks"
                    + "(import_log_id,reason,performed_by,status,started_at,completed_at) values "
                    + "(999999,'technical failure','tester','FAILED',now(),now())");

            statement.executeUpdate("insert into member_import_rollbacks"
                    + "(import_log_id,reason,performed_by,status,started_at,completed_at) values ("
                    + logId + ",'first','tester','COMPLETED',now(),now())");
            assertThatThrownBy(() -> statement.executeUpdate("insert into member_import_rollbacks"
                    + "(import_log_id,reason,performed_by,status,started_at,completed_at) values ("
                    + logId + ",'second','tester','COMPLETED',now(),now())"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("uq_import_rollback_completed_once");
        }
    }

    private void migrateTo(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        configuration.load().migrate();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private long returned(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private void rollback(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) connection.rollback();
    }
}
