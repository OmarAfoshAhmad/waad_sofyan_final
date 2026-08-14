package com.waad.tba.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
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
 * V180 gives the line snapshot the settlement/authorised split the head has
 * had since V176, and must backfill it on rows written before the column
 * existed.
 *
 * That backfill is an UPDATE against an append-only table whose trigger
 * refuses every UPDATE. V174 hit exactly this and it was invisible to every
 * test running on an empty table -- twice now, a migration that would have
 * failed on contact with data was caught only here. So the rows exist before
 * the migration runs, and the guard is proven to come back afterwards.
 *
 * The row under test carries a REFUSAL and a partial quantity, which is the
 * only situation where the two figures legitimately differ: a reviewer cut
 * the service, so less was authorised, while the shares still account for the
 * whole request.
 */
class LineSnapshotSettlementAcrossV180MigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("v180_migration").withUsername("test_user").withPassword("test_password");

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

    private long id(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void exec(String sql) throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    @Test
    void v180BackfillsSettlementRestoresTheGuardAndChangesNoExistingValue() throws SQLException {
        // Stop one migration BEFORE the one under test, with real rows in place.
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target("179").load().migrate();

        long lineSnapshotId;
        try (Connection c = conn()) {
            String s = String.valueOf(System.nanoTime());
            long employerId = id(c, "INSERT INTO employers (code, name) VALUES ('V180-" + s
                    + "', 'V180 Co " + s + "') RETURNING id");
            long policyId = id(c, "INSERT INTO benefit_policies (name, policy_code, employer_id, annual_limit, "
                    + "default_coverage_percent, start_date, end_date, status, active) VALUES ('V180P-" + s
                    + "', 'V180POL-" + s + "', " + employerId
                    + ", 10000, 80, CURRENT_DATE - 30, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id");
            long memberId = id(c, "INSERT INTO members (employer_id, full_name, benefit_policy_id, "
                    + "card_number, barcode, status, active) VALUES (" + employerId + ", 'V180 Member', "
                    + policyId + ", 'V180C" + s + "', 'V180C" + s + "', 'ACTIVE', true) RETURNING id");
            long providerId = id(c, "INSERT INTO providers (name, license_number, provider_type) "
                    + "VALUES ('Prov " + s + "', 'V180LIC-" + s + "', 'CLINIC') RETURNING id");
            long preauthId = id(c, "INSERT INTO pre_authorizations (member_id, policy_id, provider_id, "
                    + "status, request_date, created_at, updated_at) VALUES (" + memberId + ", " + policyId
                    + ", " + providerId + ", 'PARTIALLY_APPROVED', now(), now(), now()) RETURNING id");
            long preauthLineId = id(c, "INSERT INTO pre_authorization_lines (pre_authorization_id, "
                    + "requested_amount) VALUES (" + preauthId + ", 1000.00) RETURNING id");

            // A PARTIAL approval: 200 was refused, so 800 was authorised while
            // the shares still divide the full 1000.
            long decisionId = id(c, "INSERT INTO preauth_decision_snapshots (preauth_id, "
                    + "calculation_version, member_id, policy_id, expected_service_date, provider_id, "
                    + "requested_total, settlement_total, authorized_service_total, rejected_total, "
                    + "patient_share_total, company_share_total, decision_status, coverage_outcome, "
                    + "decided_by, idempotency_key) VALUES (" + preauthId + ", 1, " + memberId + ", "
                    + policyId + ", CURRENT_DATE + 14, " + providerId
                    + ", 1000.00, 1000.00, 800.00, 200.00, 200.00, 800.00, 'PARTIALLY_APPROVED', "
                    + "'PARTIALLY_COVERED', 'reviewer', 'V180-" + s + "') RETURNING id");

            // In the V178/V179 model the line had ONE approved_amount, and the
            // shares had to equal it -- so a refusal could only be recorded by
            // making them agree. That is the row V180 has to reinterpret.
            lineSnapshotId = id(c, "INSERT INTO preauth_line_snapshots (decision_snapshot_id, "
                    + "preauth_line_id, quantity, requested_quantity, approved_quantity, "
                    + "review_decision, rejection_reason, unit_price, requested_amount, "
                    + "rejected_amount, approved_amount, patient_share, company_share) VALUES ("
                    + decisionId + ", " + preauthLineId + ", 2, 4, 2, 'PARTIALLY_APPROVE', "
                    + "'الكمية تتجاوز المبرر الطبي', 250.00, 1000.00, 200.00, 800.00, 200.00, 600.00) "
                    + "RETURNING id");
        }

        long rowsBefore = count("SELECT COUNT(*) FROM preauth_line_snapshots");
        BigDecimal approvedBefore = scalar("SELECT approved_amount FROM preauth_line_snapshots WHERE id = "
                + lineSnapshotId);
        BigDecimal rejectedBefore = scalar("SELECT rejected_amount FROM preauth_line_snapshots WHERE id = "
                + lineSnapshotId);

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();

        // Nothing lost, nothing restated.
        assertThat(count("SELECT COUNT(*) FROM preauth_line_snapshots")).isEqualTo(rowsBefore);
        assertThat(scalar("SELECT approved_amount FROM preauth_line_snapshots WHERE id = " + lineSnapshotId))
                .as("the authorised value is untouched").isEqualByComparingTo(approvedBefore);
        assertThat(scalar("SELECT rejected_amount FROM preauth_line_snapshots WHERE id = " + lineSnapshotId))
                .isEqualByComparingTo(rejectedBefore);

        // The new column is populated from the only source available for a
        // pre-V180 row -- and the shares still reconcile against it.
        BigDecimal settlement = scalar("SELECT settlement_amount FROM preauth_line_snapshots WHERE id = "
                + lineSnapshotId);
        assertThat(settlement).isEqualByComparingTo("800.00");
        assertThat(scalar("SELECT patient_share + company_share FROM preauth_line_snapshots WHERE id = "
                + lineSnapshotId))
                .as("the shares account for the settlement").isEqualByComparingTo(settlement);

        // The two figures can now differ legitimately, which is the whole
        // point: a later row records 800 authorised against a 1000 settlement.
        assertThat(scalar("SELECT requested_amount FROM preauth_line_snapshots WHERE id = "
                + lineSnapshotId)).isEqualByComparingTo("1000.00");

        // The append-only guard must be back on. A migration that leaves a
        // financial record editable has undone the thing it was protecting.
        assertThatThrownBy(() -> exec("UPDATE preauth_line_snapshots SET settlement_amount = 1.00 "
                + "WHERE id = " + lineSnapshotId))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> exec("DELETE FROM preauth_line_snapshots WHERE id = " + lineSnapshotId))
                .hasMessageContaining("append-only");

        assertThat(count("SELECT COUNT(*) FROM pg_trigger WHERE tgname = "
                + "'trg_preauth_line_snapshot_no_update' AND tgenabled <> 'D'"))
                .as("the UPDATE guard is enabled, not merely present").isEqualTo(1);
    }

    private long count(String sql) throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private BigDecimal scalar(String sql) throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            BigDecimal value = rs.getBigDecimal(1);
            return value == null ? BigDecimal.ZERO : value;
        }
    }
}
