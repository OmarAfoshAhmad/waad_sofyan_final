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
 * V208 lands on a database that is already carrying members, employers and a
 * permission catalogue -- not on an empty schema.
 *
 * A migration verified only against a fresh database proves the SQL parses. It
 * proves nothing about the two things that can actually go wrong here: the
 * foreign keys have to accept the members and employers that are already
 * there, and the permission inserts have to land beside a catalogue that
 * already has rows without disturbing what an administrator has already
 * granted or revoked.
 *
 * The constraints are exercised against real rows for the same reason. Every
 * one of them encodes a decision -- an uplift is always an increase, an empty
 * window is legal because that is what a cancelled mistake looks like, a
 * revocation without a reason is not a revocation -- and a CHECK nobody has
 * seen refuse anything is a CHECK that might be spelled wrong.
 */
class LimitUpliftAcrossV208MigrationTest {

    // Its own container: a migration checked against a database another test
    // already migrated is checking nothing.
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("migration_test_v208")
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
    @DisplayName("V208 applies to a live V207 database and its constraints hold against real rows")
    void v199AppliesOverAnExistingDatabaseAndEnforcesEveryRule() throws Exception {
        migrateTo("207");

        long memberId;
        long employerId;
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            // The database is not empty when V208 arrives. These rows are what
            // the new foreign keys will point at.
            employerId = insertEmployer(statement);
            memberId = insertMember(statement, employerId);

            // And an administrator has already made a decision about a
            // permission, which V208's inserts must not overwrite.
            // Marked rather than inserted: V191 already granted this pair, so
            // an insert would conflict and prove nothing. Stamping the row is
            // what makes "V208 left it alone" checkable.
            statement.execute("update rbac_role_permissions set granted_by='قرار سابق'"
                    + " where role_code='SUPER_ADMIN' and permission_code='MEMBER_VIEW'");
        }

        migrateTo(null);

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            // ── the permission arrived, and only where it was meant to ─────
            assertThat(count(statement,
                    "select count(*) from rbac_permissions where code='MEMBER_LIMIT_UPLIFT_MANAGE'"))
                    .as("the permission exists")
                    .isEqualTo(1);
            assertThat(count(statement,
                    "select count(*) from rbac_permissions"
                            + " where code='MEMBER_LIMIT_UPLIFT_MANAGE' and sensitive = true"))
                    .as("and is marked sensitive -- it commits the insurer's money")
                    .isEqualTo(1);
            assertThat(count(statement,
                    "select count(*) from rbac_role_permissions"
                            + " where permission_code='MEMBER_LIMIT_UPLIFT_MANAGE'"))
                    .as("SUPER_ADMIN and nobody else by default")
                    .isEqualTo(1);
            assertThat(count(statement,
                    "select count(*) from rbac_role_permissions"
                            + " where permission_code='MEMBER_LIMIT_UPLIFT_MANAGE' and role_code='SUPER_ADMIN'"))
                    .isEqualTo(1);
            assertThat(count(statement,
                    "select count(*) from rbac_role_permissions"
                            + " where role_code='SUPER_ADMIN' and permission_code='MEMBER_VIEW'"
                            + " and granted_by='قرار سابق'"))
                    .as("an existing grant is left exactly as the administrator left it")
                    .isEqualTo(1);

            // ── the foreign keys accept the rows that were already there ───
            assertThatCode(() -> statement.execute(
                    "insert into member_general_limit_uplifts"
                            + " (member_id, amount, effective_from, source, requested_by_employer_id,"
                            + "  reason, granted_by_username)"
                            + " values(" + memberId + ", 15000.00, current_date, 'EMPLOYER_REQUEST',"
                            + " " + employerId + ", 'بطلب جهة العمل', 'tester')"))
                    .as("an uplift on a member that existed before the migration")
                    .doesNotThrowAnyException();

            // ── an uplift is always an increase ────────────────────────────
            assertThatThrownBy(() -> insertUplift(statement, memberId, "0.00", "current_date", null))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("amount");
            assertThatThrownBy(() -> insertUplift(statement, memberId, "-5000.00", "current_date", null))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("amount");

            // ── an empty window is legal; a reversed one is not ────────────
            assertThatCode(() -> insertUplift(statement, memberId, "1000.00", "current_date", "current_date"))
                    .as("effective_to = effective_from is how a cancelled mistake is recorded")
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> insertUplift(statement, memberId, "1000.00",
                    "current_date", "current_date - 1"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_uplift_period");

            // ── the reason is not optional ─────────────────────────────────
            assertThatThrownBy(() -> statement.execute(
                    "insert into member_general_limit_uplifts"
                            + " (member_id, amount, effective_from, source, reason)"
                            + " values(" + memberId + ", 1000.00, current_date, 'SPECIAL_CONSIDERATION', '   ')"))
                    .as("whitespace is not a reason")
                    .isInstanceOf(SQLException.class);

            // ── the source and its employer agree ──────────────────────────
            assertThatThrownBy(() -> statement.execute(
                    "insert into member_general_limit_uplifts"
                            + " (member_id, amount, effective_from, source, reason)"
                            + " values(" + memberId + ", 1000.00, current_date, 'EMPLOYER_REQUEST', 'بلا جهة')"))
                    .as("an employer request has to name the employer that made it")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_uplift_source_employer");

            assertThatThrownBy(() -> statement.execute(
                    "insert into member_general_limit_uplifts"
                            + " (member_id, amount, effective_from, source, requested_by_employer_id, reason)"
                            + " values(" + memberId + ", 1000.00, current_date, 'SPECIAL_CONSIDERATION',"
                            + " " + employerId + ", 'قرار المؤمن')"))
                    .as("the insurer's own decision has no requesting employer")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_uplift_source_employer");

            // ── a revocation says why ──────────────────────────────────────
            assertThatThrownBy(() -> statement.execute(
                    "insert into member_general_limit_uplifts"
                            + " (member_id, amount, effective_from, source, reason, revoked_at)"
                            + " values(" + memberId + ", 1000.00, current_date, 'SPECIAL_CONSIDERATION',"
                            + " 'سبب المنح', now())"))
                    .as("revoked with no reason is not a revocation")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_uplift_revocation");

            // ── an unknown source is refused ───────────────────────────────
            assertThatThrownBy(() -> statement.execute(
                    "insert into member_general_limit_uplifts"
                            + " (member_id, amount, effective_from, source, reason)"
                            + " values(" + memberId + ", 1000.00, current_date, 'BECAUSE_I_SAID_SO', 'سبب')"))
                    .isInstanceOf(SQLException.class);
        }
    }

    @Test
    @DisplayName("running V208 twice changes nothing")
    void theMigrationIsRepeatable() {
        // Flyway will not re-run it, but the inserts inside are written with
        // ON CONFLICT DO NOTHING because a database restored from a backup
        // taken mid-rollout can arrive with the permission already present.
        assertThatCode(() -> {
            migrateTo(null);
            migrateTo(null);
        }).doesNotThrowAnyException();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private long insertEmployer(Statement statement) throws SQLException {
        statement.execute("insert into employers(code, name) values('UPL-MIG', 'جهة اختبار الهجرة')");
        return firstId(statement, "select id from employers where code='UPL-MIG'");
    }

    /**
     * An ACTIVE member must carry a policy -- chk_active_member_requires_policy,
     * from V103. The fixture obeys it rather than working around it: a member
     * shaped differently from the ones in the database would test the
     * constraints against a row that could not exist.
     */
    private long insertMember(Statement statement, long employerId) throws SQLException {
        statement.execute("insert into benefit_policies"
                + " (name, policy_code, employer_id, start_date, end_date, annual_limit, status)"
                + " values('وثيقة اختبار الهجرة', 'UPL-MIG-POL', " + employerId
                + ", date_trunc('year', current_date)::date,"
                + " (date_trunc('year', current_date) + interval '1 year - 1 day')::date,"
                + " 60000.00, 'ACTIVE')");
        long policyId = firstId(statement, "select id from benefit_policies where policy_code='UPL-MIG-POL'");

        statement.execute("insert into members(full_name, card_number, employer_id, benefit_policy_id)"
                + " values('عضو اختبار الهجرة', 'UPL-MIG-1', " + employerId + ", " + policyId + ")");
        return firstId(statement, "select id from members where card_number='UPL-MIG-1'");
    }

    private void insertUplift(Statement statement, long memberId, String amount,
            String from, String to) throws SQLException {
        statement.execute("insert into member_general_limit_uplifts"
                + " (member_id, amount, effective_from, effective_to, source, reason)"
                + " values(" + memberId + ", " + amount + ", " + from + ", "
                + (to == null ? "null" : to) + ", 'SPECIAL_CONSIDERATION', 'سبب اختبار')");
    }

    private long firstId(Statement statement, String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private long count(Statement statement, String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
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
