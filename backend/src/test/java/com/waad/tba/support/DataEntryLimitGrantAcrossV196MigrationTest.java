package com.waad.tba.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * V196 takes MEMBER_LIMIT_VIEW off the DATA_ENTRY role template.
 *
 * The migration is one DELETE, which is exactly the kind that looks too small
 * to test and then removes more than it meant to. What it must not touch is
 * the thing the whole change exists to make possible: an administrator's
 * decision to grant the permission to one data-entry user anyway. That lives
 * in a different table, and a migration written against an empty database
 * cannot tell you whether it survived.
 *
 * So this runs the migration over rows that exist first -- a granted user, a
 * revoked user, and the same permission held by other roles -- and checks what
 * is left.
 */
class DataEntryLimitGrantAcrossV196MigrationTest {

    // Its own container, not the shared one: a migration checked against a
    // database some other test already migrated is checking nothing.
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("migration_test_v196")
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
    @DisplayName("V196 removes the role default and leaves every per-user decision standing")
    void v196RemovesTheDefaultAndKeepsExplicitDecisions() throws Exception {
        migrateTo("195");

        try (Connection connection = DriverManager.getConnection(
                     POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {

            long actorId = insertUser(statement, "v196-actor", "SUPER_ADMIN");
            long grantedId = insertUser(statement, "v196-granted", "DATA_ENTRY");
            long revokedId = insertUser(statement, "v196-revoked", "DATA_ENTRY");

            // Before the migration the role template carries it, which is the
            // state every existing database is in.
            assertThat(count(statement, "select count(*) from rbac_role_permissions "
                    + "where role_code='DATA_ENTRY' and permission_code='MEMBER_LIMIT_VIEW'"))
                    .isEqualTo(1);

            statement.executeUpdate("insert into rbac_user_permission_overrides"
                    + "(user_id,permission_code,effect,reason,changed_by) values("
                    + grantedId + ",'MEMBER_LIMIT_VIEW','GRANT','قرار إداري قبل الترقية'," + actorId + ")");
            statement.executeUpdate("insert into rbac_user_permission_overrides"
                    + "(user_id,permission_code,effect,reason,changed_by) values("
                    + revokedId + ",'MEMBER_LIMIT_VIEW','REVOKE','منع صريح قبل الترقية'," + actorId + ")");

            migrateTo(null);

            assertThat(count(statement, "select count(*) from rbac_role_permissions "
                    + "where role_code='DATA_ENTRY' and permission_code='MEMBER_LIMIT_VIEW'"))
                    .as("the role default is what V196 is for")
                    .isZero();

            assertThat(count(statement, "select count(*) from rbac_user_permission_overrides "
                    + "where user_id=" + grantedId + " and permission_code='MEMBER_LIMIT_VIEW' "
                    + "and effect='GRANT'"))
                    .as("an administrator's exception is the reason the rule moved onto the "
                            + "permission at all; a migration that swept it away would remove "
                            + "the capability while appearing to tidy a default")
                    .isEqualTo(1);

            assertThat(count(statement, "select count(*) from rbac_user_permission_overrides "
                    + "where user_id=" + revokedId + " and permission_code='MEMBER_LIMIT_VIEW' "
                    + "and effect='REVOKE'"))
                    .as("an explicit denial outlives the default it was denying")
                    .isEqualTo(1);

            assertThat(count(statement, "select count(*) from rbac_role_permissions "
                    + "where permission_code='MEMBER_LIMIT_VIEW' and role_code <> 'DATA_ENTRY'"))
                    .as("only DATA_ENTRY loses it; the provider portal and the employer "
                            + "administrator read ceilings through the same permission")
                    .isGreaterThan(0);

            assertThat(count(statement, "select count(*) from rbac_permissions "
                    + "where code='MEMBER_LIMIT_VIEW'"))
                    .as("the permission itself is not what is being removed")
                    .isEqualTo(1);
        }
    }

    private long insertUser(Statement statement, String prefix, String userType) throws Exception {
        String username = prefix + "-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        try (ResultSet rs = statement.executeQuery("insert into users"
                + "(username,email,password,full_name,user_type) values('" + username + "','"
                + username + "@test.local','x','V196 " + userType + "','" + userType + "') returning id")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private long count(Statement statement, String sql) throws Exception {
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
