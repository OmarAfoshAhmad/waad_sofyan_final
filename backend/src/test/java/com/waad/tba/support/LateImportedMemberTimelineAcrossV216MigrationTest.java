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

/** Proves V216 repairs only an unambiguous, unused late-import timeline. */
class LateImportedMemberTimelineAcrossV216MigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("migration_test_v216").withUsername("test_user").withPassword("test_password");

    @BeforeAll static void start() { POSTGRES.start(); }
    @AfterAll static void stop() { POSTGRES.stop(); }

    @Test
    void lateImportStartsWithThePolicyWhileManualHistoryRemainsUntouched() throws Exception {
        migrateTo("215");
        long imported;
        long manual;
        try (Connection c = connection(); Statement s = c.createStatement()) {
            long employer = id(s, "INSERT INTO employers (code,name,active) VALUES "
                    + "('V216-EMP','V216 Employer',true) RETURNING id");
            long policy = id(s, "INSERT INTO benefit_policies "
                    + "(name,policy_code,employer_id,start_date,end_date,annual_limit,default_coverage_percent,status) "
                    + "VALUES ('V216 Policy','V216-POL'," + employer
                    + ",DATE '2026-01-05',DATE '2027-08-05',60000,75,'ACTIVE') RETURNING id");
            imported = member(s, employer, policy, "V216-IMPORT");
            manual = member(s, employer, policy, "V216-MANUAL");
            assignments(s, imported, employer, policy, "IMPORT");
            assignments(s, manual, employer, policy, "MANUAL");
        }

        migrateTo(null);

        try (Connection c = connection(); Statement s = c.createStatement()) {
            assertThat(date(s, "member_employer_assignments", imported)).isEqualTo("2026-01-05");
            assertThat(date(s, "member_policy_assignments", imported)).isEqualTo("2026-01-05");
            assertThat(date(s, "member_employer_assignments", manual)).isEqualTo("2026-08-01");
            assertThat(date(s, "member_policy_assignments", manual)).isEqualTo("2026-08-01");
        }
    }

    private static long member(Statement s, long employer, long policy, String card) throws Exception {
        return id(s, "INSERT INTO members (employer_id,benefit_policy_id,full_name,card_number,barcode,"
                + "active,status,start_date,created_at) VALUES (" + employer + "," + policy
                + ",'Late Member','" + card + "','" + card
                + "',true,'ACTIVE',DATE '2026-08-01',TIMESTAMP '2026-08-01 09:00:00') RETURNING id");
    }

    private static void assignments(Statement s, long member, long employer, long policy, String source)
            throws Exception {
        s.executeUpdate("INSERT INTO member_employer_assignments (member_id,employer_id,assignment_start_date,"
                + "assignment_reason,assignment_source) VALUES (" + member + "," + employer
                + ",DATE '2026-08-01','fixture','" + source + "')");
        s.executeUpdate("INSERT INTO member_policy_assignments (member_id,policy_id,assignment_start_date,"
                + "assignment_reason,assignment_source) VALUES (" + member + "," + policy
                + ",DATE '2026-08-01','fixture','" + source + "')");
    }

    private static long id(Statement s, String sql) throws Exception {
        try (ResultSet rs = s.executeQuery(sql)) { rs.next(); return rs.getLong(1); }
    }

    private static String date(Statement s, String table, long member) throws Exception {
        try (ResultSet rs = s.executeQuery("SELECT assignment_start_date FROM " + table
                + " WHERE member_id=" + member)) { rs.next(); return rs.getDate(1).toString(); }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void migrateTo(String target) {
        var config = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
        if (target != null) config.target(target);
        config.load().migrate();
    }
}
