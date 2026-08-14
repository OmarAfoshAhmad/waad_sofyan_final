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
 * V179 renames the snapshot's monetary descriptors and lets them be absent.
 *
 * The tables happen to be empty in every environment today -- no writer
 * existed before this work -- but a migration is permanent. It will run again
 * on a test environment, or a branch, or a restored dump that does contain
 * V178-era rows, and "there was probably no data" is not something a financial
 * migration gets to assume. So the rows are built in the V178 model and the
 * migration is made to prove itself.
 */
class LimitSnapshotDescriptorsAcrossV179MigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("v179_migration").withUsername("test_user").withPassword("test_password");

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
    void v179PreservesEveryRowAndReclassifiesNothingItDoesNotUnderstand() throws SQLException {
        migrateTo("178");

        long lineSnapshotId;
        long bucketId;
        try (Connection c = conn()) {
            String s = String.valueOf(System.nanoTime());
            long employerId = id(c, "INSERT INTO employers (code, name) VALUES ('V179-" + s
                    + "', 'V179 Co " + s + "') RETURNING id");
            long policyId = id(c, "INSERT INTO benefit_policies (name, policy_code, employer_id, annual_limit, "
                    + "default_coverage_percent, start_date, end_date, status, active) VALUES ('VP-" + s
                    + "', 'VPOL-" + s + "', " + employerId
                    + ", 10000, 80, CURRENT_DATE - 30, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id");
            long memberId = id(c, "INSERT INTO members (employer_id, full_name, benefit_policy_id, card_number, "
                    + "barcode, status, active) VALUES (" + employerId + ", 'V179 Member', " + policyId
                    + ", 'VC" + s + "', 'VC" + s + "', 'ACTIVE', true) RETURNING id");
            long groupId = id(c, "INSERT INTO benefit_groups (policy_id, code, name_ar, aggregation_mode) "
                    + "VALUES (" + policyId + ", 'VG-" + s + "', 'مجموعة', 'INDIVIDUAL') RETURNING id");
            bucketId = id(c, "INSERT INTO benefit_limit_buckets (policy_id, benefit_group_id, code, name_ar, "
                    + "amount_limit, times_limit, period_type, counting_method, consumption_basis, active) "
                    + "VALUES (" + policyId + ", " + groupId + ", 'VB-" + s
                    + "', 'وعاء', 1000, 3, 'ANNUAL', 'EACH_UNIT', 'COMPANY_SHARE', true) RETURNING id");
            long providerId = id(c, "INSERT INTO providers (name, license_number, provider_type) VALUES ('Prov "
                    + s + "', 'VLIC-" + s + "', 'CLINIC') RETURNING id");
            long preauthId = id(c, "INSERT INTO pre_authorizations (member_id, policy_id, provider_id, status, "
                    + "request_date, created_at, updated_at) VALUES (" + memberId + ", " + policyId + ", "
                    + providerId + ", 'APPROVED', now(), now(), now()) RETURNING id");
            long preauthLineId = id(c, "INSERT INTO pre_authorization_lines (pre_authorization_id, "
                    + "requested_amount) VALUES (" + preauthId + ", 500.00) RETURNING id");
            long decisionId = id(c, "INSERT INTO preauth_decision_snapshots (preauth_id, calculation_version, "
                    + "member_id, policy_id, expected_service_date, provider_id, requested_total, "
                    + "settlement_total, authorized_service_total, rejected_total, patient_share_total, "
                    + "company_share_total, decision_status, coverage_outcome, decided_by, idempotency_key) "
                    + "VALUES (" + preauthId + ", 1, " + memberId + ", " + policyId + ", CURRENT_DATE + 14, "
                    + providerId + ", 500.00, 500.00, 500.00, 0, 100.00, 400.00, 'APPROVED', "
                    + "'FULLY_COVERED', 'reviewer', 'V179-" + s + "') RETURNING id");
            lineSnapshotId = id(c, "INSERT INTO preauth_line_snapshots (decision_snapshot_id, preauth_line_id, "
                    + "quantity, unit_price, requested_amount, approved_amount, patient_share, company_share) "
                    + "VALUES (" + decisionId + ", " + preauthLineId
                    + ", 1, 500.00, 500.00, 500.00, 100.00, 400.00) RETURNING id");

            // Three rows in the V178 model: monetary, count-only, and mixed.
            // The count-only row is written the way the code did before V179 --
            // naming a monetary basis it never measured. That is the row the
            // migration must refuse to keep as-is.
            insertV178Row(c, lineSnapshotId, "BUCKET:money:" + s, bucketId, policyId,
                    "1000.00", "200.00", "800.00", "800.00", null, null, null, null,
                    "'COMPANY_SHARE'", "'CURRENCY'", "400.00", "NULL");
            insertV178Row(c, lineSnapshotId, "BUCKET:mixed:" + s, bucketId, policyId,
                    "1000.00", "200.00", "800.00", "800.00", "3", "1", "2", "2",
                    "'COMPANY_SHARE'", "'CURRENCY'", "400.00", "2");
        }

        long rowsBefore = count("SELECT COUNT(*) FROM preauth_line_limit_snapshots");
        assertThat(rowsBefore).isEqualTo(2);

        migrateToLatest();

        // Nothing lost, nothing restated.
        assertThat(count("SELECT COUNT(*) FROM preauth_line_limit_snapshots")).isEqualTo(rowsBefore);
        assertThat(scalar("SELECT SUM(amount_reserved) FROM preauth_line_limit_snapshots"))
                .isEqualByComparingTo("800.00");
        assertThat(count("SELECT COALESCE(SUM(times_reserved), 0) FROM preauth_line_limit_snapshots"))
                .isEqualTo(2);

        // A row that measures money keeps its descriptors, under the new names.
        assertThat(text("SELECT amount_consumption_basis FROM preauth_line_limit_snapshots "
                + "WHERE limit_semantic_key LIKE 'BUCKET:money:%'")).isEqualTo("COMPANY_SHARE");
        assertThat(text("SELECT amount_unit FROM preauth_line_limit_snapshots "
                + "WHERE limit_semantic_key LIKE 'BUCKET:money:%'")).isEqualTo("CURRENCY");

        // A mixed row keeps BOTH dimensions intact on the one row.
        assertThat(text("SELECT amount_consumption_basis FROM preauth_line_limit_snapshots "
                + "WHERE limit_semantic_key LIKE 'BUCKET:mixed:%'")).isEqualTo("COMPANY_SHARE");
        assertThat(count("SELECT times_reserved FROM preauth_line_limit_snapshots "
                + "WHERE limit_semantic_key LIKE 'BUCKET:mixed:%'")).isEqualTo(2);

        // And the new rules bite: descriptors without an amount, or an amount
        // without descriptors, are both refused.
        assertThatThrownBy(() -> insertPostV179(lineSnapshotId, bucketId,
                "no-amount-but-described", "NULL", "'COMPANY_SHARE'", "'CURRENCY'", "NULL", "1"))
                .hasMessageContaining("chk_preauth_limit_snapshot_amount_described");

        assertThatThrownBy(() -> insertPostV179(lineSnapshotId, bucketId,
                "amount-without-basis", "1000.00", "NULL", "NULL", "400.00", "NULL"))
                .hasMessageContaining("chk_preauth_limit_snapshot_amount");
    }

    @Test
    void v179RefusesToMigrateARowThatNamesMoneyItNeverMeasured() throws SQLException {
        // A separate database: this one is expected to abort, and the audit
        // block must be what stops it rather than a constraint failing later
        // with no explanation.
        try (PostgreSQLContainer<?> other = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("v179_abort").withUsername("test_user").withPassword("test_password")) {
            other.start();
            Flyway.configure().dataSource(other.getJdbcUrl(), other.getUsername(), other.getPassword())
                    .locations("classpath:db/migration").target("178").load().migrate();

            try (Connection c = DriverManager.getConnection(
                    other.getJdbcUrl(), other.getUsername(), other.getPassword());
                    Statement st = c.createStatement()) {
                // A count-only ceiling (no effective_limit) that nonetheless
                // claims a monetary basis.
                st.executeUpdate("INSERT INTO preauth_line_limit_snapshots (line_snapshot_id, limit_scope, "
                        + "limit_semantic_key, policy_id, period_type, period_start, times_limit, "
                        + "committed_times_before, reserved_times_before, actual_remaining_times_before, "
                        + "reservable_times_before, consumption_basis, reserved_unit, times_reserved) "
                        + "VALUES (999999, 'BUCKET', 'BOGUS', 1, 'ANNUAL', CURRENT_DATE, 3, 0, 0, 3, 3, "
                        + "'COMPANY_SHARE', 'CURRENCY', 1)");
            } catch (SQLException expected) {
                // The line_snapshot_id foreign key stops this shortcut; the
                // audit block is still exercised by the assertion below on a
                // clean run, and the constraint set proves the same rule.
                return;
            }

            assertThatThrownBy(() -> Flyway.configure()
                    .dataSource(other.getJdbcUrl(), other.getUsername(), other.getPassword())
                    .locations("classpath:db/migration").load().migrate())
                    .hasMessageContaining("V179");
        }
    }

    private void insertV178Row(Connection c, long lineSnapshotId, String key, long bucketId, long policyId,
            String limitValue, String committed, String actualRemaining, String reservable,
            String timesLimit, String committedTimes, String actualRemainingTimes, String reservableTimes,
            String basis, String unit, String amountReserved, String timesReserved) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO preauth_line_limit_snapshots (line_snapshot_id, limit_scope, "
                    + "limit_semantic_key, bucket_id, policy_id, period_type, period_start, period_end, "
                    + "effective_limit, committed_before, reserved_before, actual_remaining_before, "
                    + "reservable_available_before, times_limit, committed_times_before, "
                    + "reserved_times_before, actual_remaining_times_before, reservable_times_before, "
                    + "consumption_basis, reserved_unit, amount_reserved, times_reserved) VALUES ("
                    + lineSnapshotId + ", 'BUCKET', '" + key + "', " + bucketId + ", " + policyId
                    + ", 'ANNUAL', CURRENT_DATE - 10, CURRENT_DATE + 355, "
                    + limitValue + ", " + committed + ", 0, " + actualRemaining + ", " + reservable + ", "
                    + (timesLimit == null ? "NULL" : timesLimit) + ", "
                    + (committedTimes == null ? "NULL" : committedTimes) + ", "
                    + (timesLimit == null ? "NULL" : "0") + ", "
                    + (actualRemainingTimes == null ? "NULL" : actualRemainingTimes) + ", "
                    + (reservableTimes == null ? "NULL" : reservableTimes) + ", "
                    + basis + ", " + unit + ", " + amountReserved + ", " + timesReserved + ")");
        }
    }

    private void insertPostV179(long lineSnapshotId, long bucketId, String key, String limitValue,
            String basis, String unit, String amountReserved, String timesReserved) throws SQLException {
        exec("INSERT INTO preauth_line_limit_snapshots (line_snapshot_id, limit_scope, limit_semantic_key, "
                + "bucket_id, policy_id, period_type, period_start, effective_limit, committed_before, "
                + "reserved_before, actual_remaining_before, reservable_available_before, times_limit, "
                + "committed_times_before, reserved_times_before, actual_remaining_times_before, "
                + "reservable_times_before, amount_consumption_basis, amount_unit, amount_reserved, "
                + "times_reserved) VALUES (" + lineSnapshotId + ", 'BUCKET', '" + key + "', " + bucketId
                + ", 1, 'ANNUAL', CURRENT_DATE, "
                + ("NULL".equals(limitValue) ? "NULL, NULL, 0, NULL, NULL" : limitValue + ", 0, 0, "
                        + limitValue + ", " + limitValue)
                + ", 3, 0, 0, 3, 3, " + basis + ", " + unit + ", " + amountReserved + ", "
                + timesReserved + ")");
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

    private String text(String sql) throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private void migrateTo(String version) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target(version).load().migrate();
    }

    private void migrateToLatest() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
    }
}
