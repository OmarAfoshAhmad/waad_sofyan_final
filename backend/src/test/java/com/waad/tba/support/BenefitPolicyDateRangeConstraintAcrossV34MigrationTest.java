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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * P-09: BenefitPolicyService.validateDates already refused end &lt; start in
 * Java, but nothing enforced it at the database until V34 added
 * chk_policy_date_range. V33, which runs just before it, spends its own
 * effort backfilling null start_date/end_date rows before making them NOT
 * NULL -- proof that this table is not assumed empty at migration time.
 * V34's CHECK addition does the opposite: {@code ALTER TABLE ... ADD
 * CONSTRAINT chk_policy_date_range CHECK (...)} with no {@code NOT VALID}
 * clause and no backfill step.
 *
 * That difference matters against real data, and this test proves it rather
 * than assumes it either way: a plain {@code ADD CONSTRAINT} validates every
 * existing row as part of the same DDL statement, so if any benefit_policies
 * row already has end_date before start_date when V34 runs, the migration
 * ABORTS -- Flyway reports the whole deploy as failed, not just that row.
 *
 * A fresh V1..latest run on an empty schema, as this project's other
 * migration-boundary tests already do for other tables, would never surface
 * this: no bad row exists in a schema being built from nothing, so the
 * ADD CONSTRAINT trivially "passes" against zero rows. The finding only
 * appears when a violating row is written under V33's schema (which allowed
 * any date pair) before V34 ever runs against it -- exactly the same shape
 * of gap this project's migration history already caught twice before
 * (V174, V180): a migration proven only against an empty schema said
 * nothing about what running it over real data would do.
 *
 * This is a historical finding, not a live incident: V34 has already run
 * successfully in every environment currently at V34 or later, which means
 * none of them held a violating row at the moment it ran there. It is
 * recorded here as a documented hazard and a convention gap -- new CHECK
 * constraints on a live table in this codebase should use the
 * NOT VALID + VALIDATE CONSTRAINT pattern this project already established
 * for exactly this reason (see Employer's V209 migration) -- not as
 * something to retroactively edit, since V34 is already applied and
 * checksummed everywhere that matters.
 */
class BenefitPolicyDateRangeConstraintAcrossV34MigrationTest {

    private PostgreSQLContainer<?> postgres;

    @BeforeEach
    void start() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("migration_test_v34")
                .withUsername("test_user")
                .withPassword("test_password");
        postgres.start();
    }

    @AfterEach
    void stop() {
        postgres.stop();
    }

    @Test
    @DisplayName("a pre-existing reversed date range makes V34 fail to apply")
    void v34FailsToApplyOverAnExistingReversedRow() throws Exception {
        migrateTo("33");

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("insert into employers (code, name, active)"
                    + " values ('V34-EMP', 'جهة سابقة على الهجرة', true)");
            // Exactly what V33's own schema allowed: no CHECK on the date pair
            // yet, so a reversed range writes without complaint.
            statement.execute("insert into benefit_policies (name, employer_id, start_date, end_date,"
                    + " annual_limit, default_coverage_percent, status, active)"
                    + " select 'وثيقة بتواريخ مقلوبة', id, '2026-06-01', '2026-01-01', 10000, 80,"
                    + " 'DRAFT', true from employers where code = 'V34-EMP'");
        }

        // The finding: this DOES throw. ADD CONSTRAINT with no NOT VALID
        // clause validates every existing row in the same statement, and one
        // of them already violates it.
        assertThatThrownBy(() -> migrateTo(null))
                .as("V34's plain ADD CONSTRAINT aborts the whole migration against a "
                        + "pre-existing violator -- it was never proven safe against real data, "
                        + "only lucky that no such row existed when it actually ran")
                .hasMessageContaining("chk_policy_date_range");
    }

    @Test
    @DisplayName("V34 applies cleanly, and enforces the CHECK from then on, when no prior row violates it")
    void v34AppliesAndThenRefusesNewReversedRows() throws Exception {
        migrateTo("33");

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("insert into employers (code, name, active)"
                    + " values ('V34-EMP-OK', 'جهة سابقة على الهجرة', true)");
            statement.execute("insert into benefit_policies (name, employer_id, start_date, end_date,"
                    + " annual_limit, default_coverage_percent, status, active)"
                    + " select 'وثيقة تواريخ صحيحة', id, '2026-01-01', '2026-06-01', 10000, 80,"
                    + " 'DRAFT', true from employers where code = 'V34-EMP-OK'");
        }

        assertThatCode(() -> migrateTo(null))
                .as("no pre-existing row violates the CHECK, so V34 applies cleanly")
                .doesNotThrowAnyException();

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            long employerId = idOf(statement, "select id from employers where code = 'V34-EMP-OK'");

            assertThat(count(statement,
                    "select count(*) from benefit_policies where employer_id = " + employerId))
                    .isEqualTo(1);

            assertThatThrownBy(() -> statement.execute(
                    "insert into benefit_policies (name, employer_id, start_date, end_date,"
                            + " annual_limit, default_coverage_percent, status, active)"
                            + " values ('وثيقة جديدة مقلوبة', " + employerId + ", '2026-06-01', '2026-01-01',"
                            + " 10000, 80, 'DRAFT', true)"))
                    .as("a new reversed range is exactly what Java's validateDates already refused")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_policy_date_range");

            assertThatCode(() -> statement.execute(
                    "insert into benefit_policies (name, employer_id, start_date, end_date,"
                            + " annual_limit, default_coverage_percent, status, active)"
                            + " values ('وثيقة يوم واحد', " + employerId + ", '2026-06-01', '2026-06-01',"
                            + " 10000, 80, 'DRAFT', true)"))
                    .as("end equal to start is a same-day policy, not a reversed one")
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("running the full migration set twice changes nothing")
    void theMigrationIsRepeatable() {
        assertThatCode(() -> {
            migrateTo(null);
            migrateTo(null);
        }).doesNotThrowAnyException();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private long count(Statement statement, String sql) throws SQLException {
        try (var rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private long idOf(Statement statement, String sql) throws SQLException {
        return count(statement, sql);
    }

    private void migrateTo(String target) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true);
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }
}
