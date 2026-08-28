package com.waad.tba.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/** Exercises V184 on populated V183 data, including its pre-migration audit. */
class MemberFamilyIntegrityAcrossV184MigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("migration_test_v184").withUsername("test_user").withPassword("test_password");

    @BeforeAll static void start() { POSTGRES.start(); }
    @AfterAll static void stop() { POSTGRES.stop(); }

    @Test
    void v184RejectsBadExistingFamiliesThenEnforcesTheValidOneAndKeepsAuditHistory() throws Exception {
        migrateTo("183");
        long employerA;
        long employerB;
        long principal;
        long dependent;
        try (Connection c = connection(); Statement s = c.createStatement()) {
            employerA = returned(s, "INSERT INTO employers(code,name) VALUES('F-A','Family A') RETURNING id");
            employerB = returned(s, "INSERT INTO employers(code,name) VALUES('F-B','Family B') RETURNING id");
            principal = returned(s, "INSERT INTO members(employer_id,full_name,card_number,status,active) "
                    + "VALUES(" + employerA + ",'Principal','F-P','TERMINATED',false) RETURNING id");
            dependent = returned(s, "INSERT INTO members(employer_id,full_name,card_number,parent_id,relationship,status,active) "
                    + "VALUES(" + employerA + ",'Dependent','F-D'," + principal + ",'SON','TERMINATED',false) RETURNING id");
            s.executeUpdate("UPDATE members SET employer_id=" + employerB + " WHERE id=" + dependent);
        }

        assertThatThrownBy(() -> migrateTo(null))
                .hasMessageContaining("V184 family audit failed")
                .hasMessageContaining("cross_employer=1");

        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.executeUpdate("UPDATE members SET employer_id=" + employerA + " WHERE id=" + dependent);
        }
        migrateTo(null);

        try (Connection c = connection(); Statement s = c.createStatement()) {
            assertThat(executes(s, "UPDATE members SET parent_id=id, relationship='SON' WHERE id=" + dependent)).isFalse();
            assertThat(executes(s, "UPDATE members SET relationship=NULL WHERE id=" + dependent)).isFalse();
            assertThat(executes(s, "UPDATE members SET employer_id=" + employerB + " WHERE id=" + dependent)).isFalse();
            assertThat(executes(s, "UPDATE members SET parent_id=" + dependent + ", relationship='FATHER' WHERE id=" + principal)).isFalse();
            assertThat(executes(s, "DELETE FROM members WHERE id=" + principal)).isFalse();

            long transition = returned(s, "INSERT INTO member_family_transitions(transition_id,member_id,"
                    + "previous_parent_id,new_parent_id,previous_relationship,new_relationship,effective_date,"
                    + "reason,transition_type) VALUES(gen_random_uuid()," + dependent + "," + principal + ",NULL,"
                    + "'SON',NULL,CURRENT_DATE,'test','TRANSFER') RETURNING id");
            assertThat(executes(s, "UPDATE member_family_transitions SET reason='rewrite' WHERE id=" + transition)).isFalse();
            assertThat(executes(s, "DELETE FROM member_family_transitions WHERE id=" + transition)).isFalse();
        }
    }

    private void migrateTo(String target) {
        var config = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
        if (target != null) config.target(target);
        config.load().migrate();
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private long returned(Statement statement, String sql) throws Exception {
        try (ResultSet rs = statement.executeQuery(sql)) { rs.next(); return rs.getLong(1); }
    }

    private boolean executes(Statement statement, String sql) {
        try { statement.executeUpdate(sql); return true; }
        catch (Exception ignored) { return false; }
    }
}
