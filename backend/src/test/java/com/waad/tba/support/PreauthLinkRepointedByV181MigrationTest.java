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

/**
 * V181 repoints two live foreign keys away from the dead pre-authorization
 * model.
 *
 * claims.pre_authorization_id and pre_authorization_attachments.
 * pre_authorization_id both referenced preauthorization_requests -- a table
 * with no JPA entity and no writer -- while the entities they belong to are
 * mapped to pre_authorizations. Linking a claim or an attachment to a REAL
 * approval has therefore never been possible, which is what blocks
 * conversion.
 *
 * Two things are proven here in equal measure: that the repair works, and
 * that the migration keeps its hands off the remnant it was not asked to
 * clean up.
 */
class PreauthLinkRepointedByV181MigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("v181_migration").withUsername("test_user").withPassword("test_password");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void exec(String sql) throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private long id(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String referencedTableOf(String constraintName) throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT ccu.table_name FROM information_schema.table_constraints tc "
                                + "JOIN information_schema.constraint_column_usage ccu "
                                + "  ON ccu.constraint_name = tc.constraint_name "
                                + "WHERE tc.constraint_name = '" + constraintName + "'")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    @Test
    void v181PointsTheLiveLinksAtTheLiveTableAndLeavesTheRemnantAlone() throws SQLException {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();

        assertThat(referencedTableOf("fk_claim_preauth")).isEqualTo("pre_authorizations");
        assertThat(referencedTableOf("fk_pre_authorization_attachments_request"))
                .isEqualTo("pre_authorizations");

        // Untouched on purpose: retiring the dead model is a separate decision
        // with its own proof, and mixing it into a repair hides both.
        assertThat(referencedTableOf("fk_preauth_att"))
                .as("the remnant constraint must be left exactly as it was")
                .isEqualTo("preauthorization_requests");

        try (Connection c = conn();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.columns "
                                + "WHERE table_name = 'pre_authorization_attachments' "
                                + "AND column_name = 'preauthorization_request_id'")) {
            rs.next();
            assertThat(rs.getLong(1)).as("the remnant column must survive").isEqualTo(1L);
        }
    }

    @Test
    void aClaimAndAnAttachmentCanNowNameARealPreAuthorization() throws SQLException {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();

        try (Connection c = conn()) {
            String s = String.valueOf(System.nanoTime());
            long employerId = id(c, "INSERT INTO employers (code, name) VALUES ('L1-" + s
                    + "', 'Link Co " + s + "') RETURNING id");
            long policyId = id(c, "INSERT INTO benefit_policies (name, policy_code, employer_id, "
                    + "annual_limit, default_coverage_percent, start_date, end_date, status, active) "
                    + "VALUES ('LP-" + s + "', 'LPOL-" + s + "', " + employerId
                    + ", 10000, 80, CURRENT_DATE - 30, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id");
            long memberId = id(c, "INSERT INTO members (employer_id, full_name, benefit_policy_id, "
                    + "card_number, barcode, status, active) VALUES (" + employerId + ", 'Link Member', "
                    + policyId + ", 'LC" + s + "', 'LC" + s + "', 'ACTIVE', true) RETURNING id");
            long providerId = id(c, "INSERT INTO providers (name, license_number, provider_type) "
                    + "VALUES ('Prov " + s + "', 'LLIC-" + s + "', 'CLINIC') RETURNING id");
            long visitId = id(c, "INSERT INTO visits (member_id, provider_id, visit_date) VALUES ("
                    + memberId + ", " + providerId + ", CURRENT_DATE) RETURNING id");
            long preauthId = id(c, "INSERT INTO pre_authorizations (member_id, provider_id, status, "
                    + "request_date, created_at, updated_at) VALUES (" + memberId + ", " + providerId
                    + ", 'APPROVED', now(), now(), now()) RETURNING id");
            long policyAssignmentId = id(c, "INSERT INTO member_policy_assignments (member_id, policy_id, "
                    + "assignment_start_date, assignment_source) VALUES (" + memberId + ", " + policyId
                    + ", CURRENT_DATE - 30, 'MANUAL') RETURNING id");
            long employerAssignmentId = id(c, "INSERT INTO member_employer_assignments (member_id, "
                    + "employer_id, assignment_start_date, assignment_reason, assignment_source) VALUES ("
                    + memberId + ", " + employerId + ", CURRENT_DATE - 30, 'test', 'MANUAL') RETURNING id");

            // The link conversion needs, which was impossible before V181.
            long claimId = id(c, "INSERT INTO claims (claim_number, member_id, provider_id, visit_id, "
                    + "service_date, requested_amount, status, pre_authorization_id, claim_context_code, "
                    + "policy_id, policy_assignment_id, employer_assignment_id, historical_context_status) "
                    + "VALUES ('LCLM-" + s + "', " + memberId + ", " + providerId + ", " + visitId
                    + ", CURRENT_DATE, 100.00, 'APPROVED', " + preauthId + ", 'OUTPATIENT', " + policyId
                    + ", " + policyAssignmentId + ", " + employerAssignmentId + ", 'RESOLVED') RETURNING id");
            assertThat(claimId).isPositive();

            exec("INSERT INTO pre_authorization_attachments (pre_authorization_id, file_name, "
                    + "original_file_name, stored_file_name, file_path, created_at) VALUES ("
                    + preauthId + ", 'a.pdf', 'a.pdf', 'a-stored.pdf', '/tmp/a.pdf', now())");
        }
    }

    @Test
    void anIdThatIsNotARealPreAuthorizationIsRefusedOnBothTables() throws SQLException {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();

        String s = String.valueOf(System.nanoTime());
        try (Connection c = conn()) {
            long employerId = id(c, "INSERT INTO employers (code, name) VALUES ('L2-" + s
                    + "', 'Link Co2 " + s + "') RETURNING id");
            long policyId = id(c, "INSERT INTO benefit_policies (name, policy_code, employer_id, "
                    + "annual_limit, default_coverage_percent, start_date, end_date, status, active) "
                    + "VALUES ('L2P-" + s + "', 'L2POL-" + s + "', " + employerId
                    + ", 10000, 80, CURRENT_DATE - 30, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id");
            long memberId = id(c, "INSERT INTO members (employer_id, full_name, benefit_policy_id, "
                    + "card_number, barcode, status, active) VALUES (" + employerId
                    + ", 'Link Member 2', " + policyId + ", 'L2C" + s + "', 'L2C" + s
                    + "', 'ACTIVE', true) RETURNING id");
            long providerId = id(c, "INSERT INTO providers (name, license_number, provider_type) "
                    + "VALUES ('Prov2 " + s + "', 'L2LIC-" + s + "', 'CLINIC') RETURNING id");
            long visitId = id(c, "INSERT INTO visits (member_id, provider_id, visit_date) VALUES ("
                    + memberId + ", " + providerId + ", CURRENT_DATE) RETURNING id");
            long policyAssignmentId = id(c, "INSERT INTO member_policy_assignments (member_id, policy_id, "
                    + "assignment_start_date, assignment_source) VALUES (" + memberId + ", " + policyId
                    + ", CURRENT_DATE - 30, 'MANUAL') RETURNING id");
            long employerAssignmentId = id(c, "INSERT INTO member_employer_assignments (member_id, "
                    + "employer_id, assignment_start_date, assignment_reason, assignment_source) VALUES ("
                    + memberId + ", " + employerId + ", CURRENT_DATE - 30, 'test', 'MANUAL') RETURNING id");

            assertThatThrownBy(() -> exec("INSERT INTO claims (claim_number, member_id, provider_id, "
                    + "visit_id, service_date, requested_amount, status, pre_authorization_id, "
                    + "claim_context_code, policy_id, policy_assignment_id, employer_assignment_id, "
                    + "historical_context_status) VALUES "
                    + "('L2CLM-" + s + "', " + memberId + ", " + providerId + ", " + visitId
                    + ", CURRENT_DATE, 100.00, 'APPROVED', 987654321, 'OUTPATIENT', " + policyId + ", "
                    + policyAssignmentId + ", " + employerAssignmentId + ", 'RESOLVED')"))
                    .hasMessageContaining("fk_claim_preauth");
        }

        assertThatThrownBy(() -> exec("INSERT INTO pre_authorization_attachments (pre_authorization_id, "
                + "file_name, original_file_name, stored_file_name, file_path, created_at) VALUES (987654321, "
                + "'b.pdf', 'b.pdf', 'b-stored.pdf', '/tmp/b.pdf', now())"))
                .hasMessageContaining("fk_pre_authorization_attachments_request");
    }

    @Test
    void v181RefusesToRunWhereTheLegacyModelStillHoldsData() throws SQLException {
        // A separate database stopped one migration short, with a legacy row
        // in place. Repointing the key there would orphan a link, and matching
        // ids across the two tables would never have proven they describe the
        // same authorization.
        try (PostgreSQLContainer<?> other = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("v181_abort").withUsername("test_user").withPassword("test_password")) {
            other.start();
            Flyway.configure().dataSource(other.getJdbcUrl(), other.getUsername(), other.getPassword())
                    .locations("classpath:db/migration").target("180").load().migrate();

            try (Connection c = DriverManager.getConnection(
                    other.getJdbcUrl(), other.getUsername(), other.getPassword());
                    Statement st = c.createStatement()) {
                long employerId = id(c, "INSERT INTO employers (code, name) VALUES ('L3-x', 'Legacy Co') "
                        + "RETURNING id");
                long policyId = id(c, "INSERT INTO benefit_policies (name, policy_code, employer_id, "
                        + "annual_limit, default_coverage_percent, start_date, end_date, status, active) "
                        + "VALUES ('L3P', 'L3POL', " + employerId
                        + ", 10000, 80, CURRENT_DATE - 30, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id");
                long memberId = id(c, "INSERT INTO members (employer_id, full_name, benefit_policy_id, "
                        + "card_number, barcode, status, active) VALUES (" + employerId
                        + ", 'Legacy Member', " + policyId + ", 'L3C', 'L3C', 'ACTIVE', true) RETURNING id");
                long providerId = id(c, "INSERT INTO providers (name, license_number, provider_type) "
                        + "VALUES ('Legacy Prov', 'L3LIC', 'CLINIC') RETURNING id");
                st.executeUpdate("INSERT INTO preauthorization_requests (member_id, provider_id, status, "
                        + "created_at) VALUES (" + memberId + ", " + providerId + ", 'PENDING', now())");
            }

            assertThatThrownBy(() -> Flyway.configure()
                    .dataSource(other.getJdbcUrl(), other.getUsername(), other.getPassword())
                    .locations("classpath:db/migration").load().migrate())
                    .hasMessageContaining("V181 aborted");
        }
    }
}
