package com.waad.tba.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class OperationalRolePermissionsAcrossV192MigrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("migration_test_v192")
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
    void v192AddsOperationalDefaultsWithoutOverwritingExistingRoleOrUserDecisions() throws Exception {
        migrateTo("191");

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            long actorId = returned(statement, "insert into users(username,email,password,full_name,user_type) "
                    + "values('v192-actor','v192-actor@test.local','x','Actor','SUPER_ADMIN') returning id");
            long targetId = returned(statement, "insert into users(username,email,password,full_name,user_type) "
                    + "values('v192-target','v192-target@test.local','x','Target','DATA_ENTRY') returning id");

            statement.executeUpdate("insert into rbac_role_permissions(role_code,permission_code,granted_by) "
                    + "values('DATA_ENTRY','CLAIM_VIEW','CUSTOM_BEFORE_V192')");
            statement.executeUpdate("insert into rbac_user_permission_overrides"
                    + "(user_id,permission_code,effect,reason,changed_by) values("
                    + targetId + ",'MEMBER_EXPORT','REVOKE','explicit deny before upgrade'," + actorId + ")");
            statement.executeUpdate("insert into rbac_permission_change_audit"
                    + "(target_type,target_user_id,permission_code,new_effect,reason,actor_user_id) values("
                    + "'USER'," + targetId + ",'MEMBER_EXPORT','REVOKE','pre-upgrade audit'," + actorId + ")");

            migrateTo(null);

            assertThat(returned(statement, "select count(*) from rbac_permissions where code in "
                    + "('MEMBER_LIMIT_VIEW','MEMBER_REINSTATE_TERMINATED')")).isEqualTo(2);
            assertThat(returned(statement, "select count(*) from rbac_role_permissions where role_code='SUPER_ADMIN' "
                    + "and permission_code in ('MEMBER_LIMIT_VIEW','MEMBER_REINSTATE_TERMINATED')")).isEqualTo(2);
            assertThat(returned(statement, "select count(*) from rbac_role_permissions where role_code='DATA_ENTRY' "
                    + "and permission_code='CLAIM_VIEW' and granted_by='CUSTOM_BEFORE_V192'")).isEqualTo(1);
            assertThat(returned(statement, "select count(*) from rbac_user_permission_overrides where user_id="
                    + targetId + " and permission_code='MEMBER_EXPORT' and effect='REVOKE' "
                    + "and reason='explicit deny before upgrade'")).isEqualTo(1);
            assertThat(returned(statement, "select count(*) from rbac_permission_change_audit where target_user_id="
                    + targetId + " and permission_code='MEMBER_EXPORT' and new_effect='REVOKE'")).isEqualTo(1);
        }
    }

    private void migrateTo(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private long returned(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}
