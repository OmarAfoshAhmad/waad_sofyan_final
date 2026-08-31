package com.waad.tba.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
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
 * E-09: two rules EmployerService.validateEmployerTerms enforces in Java --
 * a contract cannot end before it starts, a member cap cannot be zero or
 * negative -- had no CHECK behind them. Anything writing to this table
 * outside that one service passed with no rule applied at all.
 *
 * V209 lands on a database that already has employer rows (from V2's own
 * seed data and anything created before this migration runs), which is the
 * only way this is a meaningful test: a CHECK verified only against an empty
 * schema proves the SQL parses, not that it validates against what is
 * already there.
 */
class EmployerTermsConstraintsAcrossV209MigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("migration_test_v209")
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
    @DisplayName("V209 applies over a live V208 database and both CHECKs refuse what Java already refused")
    void v200AppliesOverAnExistingDatabaseAndEnforcesBothRules() throws Exception {
        migrateTo("208");

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            // The database is not empty when V209 arrives -- an employer that
            // predates this migration, with terms already inside the rule.
            statement.execute("insert into employers (code, name, active, contract_start_date,"
                    + " contract_end_date, max_member_limit)"
                    + " values ('V209-PRE', 'جهة سابقة على الهجرة', true, '2026-01-01', '2026-12-31', 500)");
        }

        migrateTo(null);

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            // The pre-existing row is untouched -- VALIDATE CONSTRAINT does not
            // rewrite rows, and this one was compliant to begin with.
            assertThatCode(() -> statement.execute(
                    "update employers set phone = '0900000000' where code = 'V209-PRE'"))
                    .doesNotThrowAnyException();

            // ── the contract period ─────────────────────────────────────────
            assertThatThrownBy(() -> statement.execute(
                    "insert into employers (code, name, active, contract_start_date, contract_end_date)"
                            + " values ('V209-BADTERM', 'جهة بعقد مقلوب', true, '2026-06-01', '2026-01-01')"))
                    .as("an end date before the start date is exactly what Java already refused")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_employer_contract_period");

            assertThatCode(() -> statement.execute(
                    "insert into employers (code, name, active, contract_start_date, contract_end_date)"
                            + " values ('V209-EQUALTERM', 'جهة عقد يوم واحد', true, '2026-06-01', '2026-06-01')"))
                    .as("end equal to start is a same-day contract, not a reversed one")
                    .doesNotThrowAnyException();

            assertThatCode(() -> statement.execute(
                    "insert into employers (code, name, active, contract_start_date, contract_end_date)"
                            + " values ('V209-OPENTERM', 'جهة بلا تاريخ نهاية', true, '2026-06-01', null)"))
                    .as("an open-ended contract names no end to be before its start")
                    .doesNotThrowAnyException();

            // ── the member cap ──────────────────────────────────────────────
            assertThatThrownBy(() -> statement.execute(
                    "insert into employers (code, name, active, max_member_limit)"
                            + " values ('V209-ZEROCAP', 'جهة بحد صفري', true, 0)"))
                    .as("a cap of zero is not a limit an employer can operate under")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_employer_max_member_limit_positive");

            assertThatThrownBy(() -> statement.execute(
                    "insert into employers (code, name, active, max_member_limit)"
                            + " values ('V209-NEGCAP', 'جهة بحد سالب', true, -5)"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_employer_max_member_limit_positive");

            assertThatCode(() -> statement.execute(
                    "insert into employers (code, name, active, max_member_limit)"
                            + " values ('V209-NOCAP', 'جهة بلا حد', true, null)"))
                    .as("no cap configured is unlimited, not a violation of a positive cap")
                    .doesNotThrowAnyException();

            assertThat(count(statement, "select count(*) from employers where code = 'V209-PRE'"))
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("running V209 twice changes nothing")
    void theMigrationIsRepeatable() {
        assertThatCode(() -> {
            migrateTo(null);
            migrateTo(null);
        }).doesNotThrowAnyException();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private long count(Statement statement, String sql) throws SQLException {
        try (var rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
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
