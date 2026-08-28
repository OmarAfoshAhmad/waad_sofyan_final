package com.waad.tba.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/** Proves V183 against a populated V182 database, not an empty fresh schema. */
class MemberEmployerAssignmentsAcrossV183MigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("migration_test_v183").withUsername("test_user").withPassword("test_password");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    void v183BackfillsExistingMembersAndEnforcesHistoricalIntegrity() throws SQLException {
        migrateTo("182");

        long employerA;
        long employerB;
        long memberId;
        try (Connection conn = connection()) {
            employerA = insertEmployer(conn, "A");
            employerB = insertEmployer(conn, "B");
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO members (employer_id, full_name, card_number, active, status, start_date) "
                            + "VALUES (?, 'Existing Member', 'V183-CARD', false, 'TERMINATED', DATE '2024-03-15') "
                            + "RETURNING id")) {
                ps.setLong(1, employerA);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    memberId = rs.getLong(1);
                }
            }
        }

        migrateTo(null);

        try (Connection conn = connection()) {
            long assignmentId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, employer_id, assignment_start_date, assignment_end_date, assignment_source, "
                            + "member_full_name, member_card_number FROM member_employer_assignments WHERE member_id = ?")) {
                ps.setLong(1, memberId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assignmentId = rs.getLong("id");
                    assertThat(rs.getLong("employer_id")).isEqualTo(employerA);
                    assertThat(rs.getDate("assignment_start_date").toLocalDate()).isEqualTo("2024-03-15");
                    assertThat(rs.getDate("assignment_end_date")).isNull();
                    assertThat(rs.getString("assignment_source")).isEqualTo("BACKFILL");
                    assertThat(rs.getString("member_full_name")).isEqualTo("Existing Member");
                    assertThat(rs.getString("member_card_number")).isEqualTo("V183-CARD");
                    assertThat(rs.next()).isFalse();
                }
            }

            assertThat(hasMemberForeignKey(conn)).as("history must survive physical member deletion").isFalse();
            assertThat(triggerEnabled(conn, "trg_member_employer_assignment_no_delete")).isTrue();
            assertThat(triggerEnabled(conn, "trg_member_employer_assignment_update_guard")).isTrue();

            assertThat(updateEndDateSucceeds(conn, assignmentId, "2025-01-01")).isTrue();
            assertThat(updateEndDateSucceeds(conn, assignmentId, "2025-02-01"))
                    .as("a closed assignment is immutable").isFalse();
            assertThat(updateEmployerSucceeds(conn, assignmentId, employerB)).isFalse();
            assertThat(deleteSucceeds(conn, assignmentId)).isFalse();

            // A half-open successor beginning exactly at the old end is valid.
            assertThat(insertAssignmentSucceeds(conn, memberId, employerB, "2025-01-01", null)).isTrue();
            // Any overlap with that open successor is rejected by PostgreSQL itself.
            assertThat(insertAssignmentSucceeds(conn, memberId, employerA, "2025-06-01", null)).isFalse();
        }
    }

    private void migrateTo(String target) {
        var config = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
        if (target != null) config.target(target);
        config.load().migrate();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private long insertEmployer(Connection conn, String suffix) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(
                "INSERT INTO employers (code, name) VALUES ('V183-" + suffix + "', 'Employer " + suffix + "') RETURNING id")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private boolean updateEndDateSucceeds(Connection conn, long id, String date) {
        return execute(conn, "UPDATE member_employer_assignments SET assignment_end_date = DATE '" + date + "' WHERE id = " + id);
    }

    private boolean updateEmployerSucceeds(Connection conn, long id, long employerId) {
        return execute(conn, "UPDATE member_employer_assignments SET employer_id = " + employerId + " WHERE id = " + id);
    }

    private boolean deleteSucceeds(Connection conn, long id) {
        return execute(conn, "DELETE FROM member_employer_assignments WHERE id = " + id);
    }

    private boolean insertAssignmentSucceeds(Connection conn, long memberId, long employerId, String start, String end) {
        String endValue = end == null ? "NULL" : "DATE '" + end + "'";
        return execute(conn, "INSERT INTO member_employer_assignments "
                + "(member_id, employer_id, assignment_start_date, assignment_end_date, assignment_reason, assignment_source) VALUES ("
                + memberId + ", " + employerId + ", DATE '" + start + "', " + endValue + ", 'test', 'MANUAL')");
    }

    private boolean execute(Connection conn, String sql) {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
            return true;
        } catch (SQLException ex) {
            return false;
        }
    }

    private boolean hasMemberForeignKey(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid "
                        + "JOIN pg_class r ON r.oid=c.confrelid WHERE c.contype='f' "
                        + "AND t.relname='member_employer_assignments' AND r.relname='members'")) {
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private boolean triggerEnabled(Connection conn, String triggerName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT tgenabled <> 'D' FROM pg_trigger WHERE tgname = ?")) {
            ps.setString(1, triggerName);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() && rs.getBoolean(1); }
        }
    }
}
