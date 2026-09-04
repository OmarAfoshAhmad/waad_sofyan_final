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
 * V219 gives claims.policy_id/policy_assignment_id/employer_assignment_id
 * (V217) an explicit trust status: RESOLVED (permanent) or LEGACY_UNRESOLVED
 * (a pre-V217 row this project could not attribute without guessing --
 * docs/testing/CLAIMS_POLICY_SNAPSHOT_BACKFILL_GAPS.md). This proves, on row
 * shapes written before V219, that:
 *  - the backfill of the new status column agrees with the three columns'
 *    actual completeness;
 *  - a RESOLVED row can never change (status or columns);
 *  - a LEGACY_UNRESOLVED row can only ever move to RESOLVED, and only when
 *    the three columns it's given actually belong together and cover
 *    service_date;
 *  - no new INSERT may ever start at LEGACY_UNRESOLVED.
 */
class ClaimsHistoricalContextStatusAcrossV219MigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("v219_root").withUsername("test_user").withPassword("test_password");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    private String freshDatabase(String name) throws SQLException {
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement st = c.createStatement()) {
            st.executeUpdate("CREATE DATABASE " + name);
        }
        return POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + name);
    }

    private Connection conn(String url) throws SQLException {
        return DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void exec(String url, String sql) throws SQLException {
        try (Connection c = conn(url); Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private long id(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String stringScalar(String url, String sql) throws SQLException {
        try (Connection c = conn(url); Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private void migrateTo(String url, String version) {
        Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target(version).load().migrate();
    }

    private record World(long employerId, long policyId, long memberId, long employerAssignmentId,
            long policyAssignmentId, long claimId) {}

    /** A claim written pre-219, with its snapshot columns already resolved by V217. */
    private World resolvedWorld(Connection c, String tag, String serviceDate) throws SQLException {
        long employerId = id(c, "INSERT INTO employers (code, name) VALUES ('V219-" + tag
                + "', 'V219 Co " + tag + "') RETURNING id");
        long policyId = id(c, "INSERT INTO benefit_policies (name, policy_code, employer_id, annual_limit, "
                + "default_coverage_percent, start_date, end_date, status, active) VALUES ('V219P-" + tag
                + "', 'V219POL-" + tag + "', " + employerId
                + ", 10000, 80, CURRENT_DATE - 400, CURRENT_DATE + 400, 'ACTIVE', true) RETURNING id");
        long memberId = id(c, "INSERT INTO members (employer_id, full_name, benefit_policy_id, card_number, "
                + "barcode, status, active) VALUES (" + employerId + ", 'V219 Member', " + policyId
                + ", 'V219C" + tag + "', 'V219C" + tag + "', 'ACTIVE', true) RETURNING id");
        long employerAssignmentId = id(c, "INSERT INTO member_employer_assignments (member_id, employer_id, "
                + "assignment_start_date, assignment_end_date, assignment_reason, assignment_source) VALUES ("
                + memberId + ", " + employerId + ", CURRENT_DATE - 400, NULL, 'test', 'BACKFILL') RETURNING id");
        long policyAssignmentId = id(c, "INSERT INTO member_policy_assignments (member_id, policy_id, "
                + "assignment_start_date, assignment_end_date) VALUES (" + memberId + ", " + policyId
                + ", CURRENT_DATE - 400, NULL) RETURNING id");
        long providerId = id(c, "INSERT INTO providers (name, license_number, provider_type) VALUES "
                + "('V219 Prov " + tag + "', 'V219LIC-" + tag + "', 'CLINIC') RETURNING id");
        long visitId = id(c, "INSERT INTO visits (member_id, provider_id, visit_date) VALUES ("
                + memberId + ", " + providerId + ", " + serviceDate + ") RETURNING id");
        long claimId = id(c, "INSERT INTO claims (claim_number, member_id, provider_id, visit_id, "
                + "service_date, requested_amount, status, claim_context_code, policy_id, "
                + "policy_assignment_id, employer_assignment_id) VALUES ('V219CLM-" + tag + "', " + memberId
                + ", " + providerId + ", " + visitId + ", " + serviceDate
                + ", 300, 'APPROVED', 'OUTPATIENT', " + policyId + ", " + policyAssignmentId + ", "
                + employerAssignmentId + ") RETURNING id");
        return new World(employerId, policyId, memberId, employerAssignmentId, policyAssignmentId, claimId);
    }

    /** A claim written pre-219 whose V217 backfill could not attribute it (all three NULL). */
    private World unresolvedWorld(Connection c, String tag, String serviceDate) throws SQLException {
        long employerId = id(c, "INSERT INTO employers (code, name) VALUES ('V219U-" + tag
                + "', 'V219U Co " + tag + "') RETURNING id");
        long policyId = id(c, "INSERT INTO benefit_policies (name, policy_code, employer_id, annual_limit, "
                + "default_coverage_percent, start_date, end_date, status, active) VALUES ('V219UP-" + tag
                + "', 'V219UPOL-" + tag + "', " + employerId
                + ", 10000, 80, " + serviceDate + " + 5, CURRENT_DATE + 400, 'ACTIVE', true) RETURNING id");
        long memberId = id(c, "INSERT INTO members (employer_id, full_name, benefit_policy_id, card_number, "
                + "barcode, status, active) VALUES (" + employerId + ", 'V219U Member', " + policyId
                + ", 'V219UC" + tag + "', 'V219UC" + tag + "', 'ACTIVE', true) RETURNING id");
        long employerAssignmentId = id(c, "INSERT INTO member_employer_assignments (member_id, employer_id, "
                + "assignment_start_date, assignment_end_date, assignment_reason, assignment_source) VALUES ("
                + memberId + ", " + employerId + ", " + serviceDate + " + 5, NULL, 'test', 'BACKFILL') "
                + "RETURNING id");
        long policyAssignmentId = id(c, "INSERT INTO member_policy_assignments (member_id, policy_id, "
                + "assignment_start_date, assignment_end_date) VALUES (" + memberId + ", " + policyId + ", "
                + serviceDate + " + 5, NULL) RETURNING id");
        long providerId = id(c, "INSERT INTO providers (name, license_number, provider_type) VALUES "
                + "('V219U Prov " + tag + "', 'V219ULIC-" + tag + "', 'CLINIC') RETURNING id");
        long visitId = id(c, "INSERT INTO visits (member_id, provider_id, visit_date) VALUES ("
                + memberId + ", " + providerId + ", " + serviceDate + ") RETURNING id");
        // No policy_id/policy_assignment_id/employer_assignment_id: reproduces
        // exactly what V217's backfill leaves when the only candidate
        // assignment starts after service_date (the real shape found in
        // waad_production_review_20260903).
        long claimId = id(c, "INSERT INTO claims (claim_number, member_id, provider_id, visit_id, "
                + "service_date, requested_amount, status, claim_context_code) VALUES ('V219UCLM-" + tag
                + "', " + memberId + ", " + providerId + ", " + visitId + ", " + serviceDate
                + ", 300, 'APPROVED', 'OUTPATIENT') RETURNING id");
        return new World(employerId, policyId, memberId, employerAssignmentId, policyAssignmentId, claimId);
    }

    @Test
    void backfillMarksACompleteRowResolvedAndAnIncompleteRowLegacyUnresolved() throws SQLException {
        String url = freshDatabase("v219_backfill_status");
        migrateTo(url, "217");

        World resolved;
        World unresolved;
        try (Connection c = conn(url)) {
            resolved = resolvedWorld(c, "A", "CURRENT_DATE");
            unresolved = unresolvedWorld(c, "B", "CURRENT_DATE - 30");
        }

        migrateTo(url, "219");

        assertThat(stringScalar(url, "SELECT historical_context_status FROM claims WHERE id = "
                + resolved.claimId())).isEqualTo("RESOLVED");
        assertThat(stringScalar(url, "SELECT historical_context_status FROM claims WHERE id = "
                + unresolved.claimId())).isEqualTo("LEGACY_UNRESOLVED");
    }

    @Test
    void aResolvedRowIsPermanentlyImmutable() throws SQLException {
        String url = freshDatabase("v219_resolved_immutable");
        migrateTo(url, "217");
        World resolved;
        long otherPolicyId;
        try (Connection c = conn(url)) {
            resolved = resolvedWorld(c, "C", "CURRENT_DATE");
            otherPolicyId = id(c, "INSERT INTO benefit_policies (name, policy_code, employer_id, "
                    + "annual_limit, default_coverage_percent, start_date, end_date, status, active) "
                    + "VALUES ('V219P-C2', 'V219POL-C2', " + resolved.employerId()
                    + ", 10000, 80, CURRENT_DATE - 400, CURRENT_DATE + 400, 'ACTIVE', true) RETURNING id");
        }
        migrateTo(url, "219");

        final long claimId = resolved.claimId();
        final long other = otherPolicyId;
        assertThatThrownBy(() -> exec(url, "UPDATE claims SET policy_id = " + other
                + " WHERE id = " + claimId))
                .hasMessageContaining("historical context is permanent once RESOLVED");
        assertThatThrownBy(() -> exec(url, "UPDATE claims SET historical_context_status = "
                + "'LEGACY_UNRESOLVED' WHERE id = " + claimId))
                .as("RESOLVED can never move backward").hasMessageContaining("permanent once RESOLVED");
    }

    @Test
    void aLegacyUnresolvedRowMayNotBePartiallyEdited() throws SQLException {
        String url = freshDatabase("v219_no_partial_edit");
        migrateTo(url, "217");
        World w;
        try (Connection c = conn(url)) {
            w = unresolvedWorld(c, "G", "CURRENT_DATE - 30");
        }
        migrateTo(url, "219");

        // Only one legal transition exists: LEGACY_UNRESOLVED -> RESOLVED,
        // applied atomically with the full consistency check. Setting just
        // policy_id while historical_context_status stays LEGACY_UNRESOLVED
        // -- exactly what V217's original DRAFT/NEEDS_CORRECTION carve-out
        // used to allow -- must be rejected outright, not silently accepted
        // as an unvalidated partial correction.
        final long claimId = w.claimId();
        final long policyId = w.policyId();
        assertThatThrownBy(() -> exec(url, "UPDATE claims SET policy_id = " + policyId
                + " WHERE id = " + claimId))
                .as("a LEGACY_UNRESOLVED claim's snapshot columns may not drift piecemeal")
                .hasMessageContaining("may only change as part of a single, validated transition to RESOLVED");
    }

    @Test
    void aLegacyUnresolvedRowMayBeResolvedOnlyWithConsistentAssignments() throws SQLException {
        String url = freshDatabase("v219_valid_transition");
        migrateTo(url, "217");
        World w;
        try (Connection c = conn(url)) {
            // The assignments already cover service_date (member_policy_
            // assignments is append-only -- a real correction can never
            // rewrite assignment_start_date, so this reproduces the OTHER
            // gap shape: a claim left unlinked by V217 despite a perfectly
            // good, already-covering assignment existing -- e.g. an
            // ambiguous snapshot disagreement (see V217 test), resolved
            // later by a reviewed, documented decision.
            w = resolvedWorld(c, "D", "CURRENT_DATE - 5");
            // V217's original guard only allows changing these columns while
            // the claim is DRAFT/NEEDS_CORRECTION; drop to DRAFT to null
            // them out, matching how a real pre-V217 gap would have looked.
            c.createStatement().executeUpdate("UPDATE claims SET status = 'DRAFT' WHERE id = " + w.claimId());
            c.createStatement().executeUpdate("UPDATE claims SET policy_id = NULL, policy_assignment_id = "
                    + "NULL, employer_assignment_id = NULL WHERE id = " + w.claimId());
            c.createStatement().executeUpdate("UPDATE claims SET status = 'APPROVED' WHERE id = " + w.claimId());
        }
        migrateTo(url, "219");

        assertThat(stringScalar(url, "SELECT historical_context_status FROM claims WHERE id = "
                + w.claimId())).isEqualTo("LEGACY_UNRESOLVED");

        exec(url, "UPDATE claims SET policy_id = " + w.policyId() + ", policy_assignment_id = "
                + w.policyAssignmentId() + ", employer_assignment_id = " + w.employerAssignmentId()
                + ", historical_context_status = 'RESOLVED' WHERE id = " + w.claimId());

        assertThat(stringScalar(url, "SELECT historical_context_status FROM claims WHERE id = "
                + w.claimId())).isEqualTo("RESOLVED");
    }

    @Test
    void resolvingWithAnAssignmentThatDoesNotCoverServiceDateIsRejected() throws SQLException {
        String url = freshDatabase("v219_invalid_transition_dates");
        migrateTo(url, "217");
        World w;
        try (Connection c = conn(url)) {
            // assignment starts AFTER service_date and is left uncorrected --
            // exactly the still-open case from the real data.
            w = unresolvedWorld(c, "E", "CURRENT_DATE - 30");
        }
        migrateTo(url, "219");

        final long claimId = w.claimId();
        final long policyId = w.policyId();
        final long policyAssignmentId = w.policyAssignmentId();
        final long employerAssignmentId = w.employerAssignmentId();
        assertThatThrownBy(() -> exec(url, "UPDATE claims SET policy_id = " + policyId
                + ", policy_assignment_id = " + policyAssignmentId + ", employer_assignment_id = "
                + employerAssignmentId + ", historical_context_status = 'RESOLVED' WHERE id = " + claimId))
                .as("resolving must not succeed while the assignment still doesn't cover service_date")
                .hasMessageContaining("does not cover service_date");
    }

    @Test
    void resolvingWithSomeoneElsesAssignmentIsRejected() throws SQLException {
        String url = freshDatabase("v219_invalid_transition_ownership");
        migrateTo(url, "217");
        World w;
        long strangersAssignmentOnTheSamePolicy;
        try (Connection c = conn(url)) {
            w = unresolvedWorld(c, "F", "CURRENT_DATE - 30");
            // Same policy as w, but a DIFFERENT member -- isolates the
            // member-ownership check from the policy-match check (which a
            // stranger on a different policy would trip first instead).
            long strangerMemberId = id(c, "INSERT INTO members (employer_id, full_name, benefit_policy_id, "
                    + "card_number, barcode, status, active) VALUES (" + w.employerId()
                    + ", 'V219 Stranger', " + w.policyId() + ", 'V219CF2', 'V219CF2', 'ACTIVE', true) "
                    + "RETURNING id");
            strangersAssignmentOnTheSamePolicy = id(c, "INSERT INTO member_policy_assignments (member_id, "
                    + "policy_id, assignment_start_date, assignment_end_date) VALUES (" + strangerMemberId
                    + ", " + w.policyId() + ", CURRENT_DATE - 400, NULL) RETURNING id");
        }
        migrateTo(url, "219");

        final long claimId = w.claimId();
        final long policyId = w.policyId();
        final long strangersAssignment = strangersAssignmentOnTheSamePolicy;
        final long employerAssignmentId = w.employerAssignmentId();
        assertThatThrownBy(() -> exec(url, "UPDATE claims SET policy_id = " + policyId
                + ", policy_assignment_id = " + strangersAssignment + ", employer_assignment_id = "
                + employerAssignmentId + ", historical_context_status = 'RESOLVED' WHERE id = " + claimId))
                .as("an assignment belonging to a different member must be rejected")
                .hasMessageContaining("does not belong to this claim's member");
    }

    @Test
    void aNewInsertMayNotStartAtLegacyUnresolved() throws SQLException {
        String url = freshDatabase("v219_reject_new_legacy");
        migrateTo(url, "219");

        long employerId;
        long policyId;
        long memberId;
        long providerId;
        long visitId;
        try (Connection c = conn(url)) {
            employerId = id(c, "INSERT INTO employers (code, name) VALUES ('V219N', 'V219N Co') RETURNING id");
            policyId = id(c, "INSERT INTO benefit_policies (name, policy_code, employer_id, annual_limit, "
                    + "default_coverage_percent, start_date, end_date, status, active) VALUES "
                    + "('V219NP', 'V219NPOL', " + employerId
                    + ", 10000, 80, CURRENT_DATE - 400, CURRENT_DATE + 400, 'ACTIVE', true) RETURNING id");
            memberId = id(c, "INSERT INTO members (employer_id, full_name, benefit_policy_id, card_number, "
                    + "barcode, status, active) VALUES (" + employerId + ", 'V219N Member', " + policyId
                    + ", 'V219NC', 'V219NC', 'ACTIVE', true) RETURNING id");
            providerId = id(c, "INSERT INTO providers (name, license_number, provider_type) VALUES "
                    + "('V219N Prov', 'V219NLIC', 'CLINIC') RETURNING id");
            visitId = id(c, "INSERT INTO visits (member_id, provider_id, visit_date) VALUES ("
                    + memberId + ", " + providerId + ", CURRENT_DATE) RETURNING id");
        }

        assertThatThrownBy(() -> exec(url, "INSERT INTO claims (claim_number, member_id, provider_id, "
                + "visit_id, service_date, requested_amount, status, claim_context_code, "
                + "historical_context_status) VALUES ('V219N-CLM', " + memberId + ", " + providerId + ", "
                + visitId + ", CURRENT_DATE, 100, 'DRAFT', 'OUTPATIENT', 'LEGACY_UNRESOLVED')"))
                .as("no new claim may ever be inserted already claiming LEGACY_UNRESOLVED")
                .hasMessageContaining("reserved for rows the V219 migration found already unresolved");
    }
}
