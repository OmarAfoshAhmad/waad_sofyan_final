package com.waad.tba.modules.preauthorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * The last thing 3A owes: a snapshot row that carries BOTH ceilings a single
 * bucket can impose, written to real PostgreSQL and read back.
 *
 * A bucket is one commercial constraint that may cap money and occurrences at
 * once. Splitting that into two rows would suggest two independent limits and
 * complicate the release path; keeping it in one row means the two dimensions
 * must be provably independent in arithmetic while sharing a row -- which is
 * exactly what these constraints check.
 *
 * Everything here goes through raw JDBC on a separate connection. That is
 * stronger than clearing the Hibernate session: nothing read below can have
 * come from a persistence context or a second-level cache, because none is
 * involved.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class PreauthLimitSnapshotTwoDimensionsIntegrationTest extends PostgresIntegrationTestBase {

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Connection conn() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private record Fixture(long memberId, long policyId, long bucketId, long providerId,
            long preauthId, long preauthLineId) {}

    private Fixture fixture() throws Exception {
        String s = suffix();
        try (Connection c = conn()) {
            long employerId = id(c, "INSERT INTO employers (code, name) VALUES ('TD-" + s
                    + "', 'TwoDim Co " + s + "') RETURNING id");
            long policyId = id(c, "INSERT INTO benefit_policies (name, policy_code, employer_id, annual_limit, "
                    + "default_coverage_percent, start_date, end_date, status, active) VALUES ('TP-" + s
                    + "', 'TPOL-" + s + "', " + employerId
                    + ", 10000, 80, CURRENT_DATE - 30, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id");
            long memberId = id(c, "INSERT INTO members (employer_id, full_name, benefit_policy_id, card_number, "
                    + "barcode, status, active) VALUES (" + employerId + ", 'TwoDim Member', " + policyId
                    + ", 'TC" + s + "', 'TC" + s + "', 'ACTIVE', true) RETURNING id");
            long groupId = id(c, "INSERT INTO benefit_groups (policy_id, code, name_ar, aggregation_mode) "
                    + "VALUES (" + policyId + ", 'TG-" + s + "', 'مجموعة', 'INDIVIDUAL') RETURNING id");
            // One bucket, two ceilings: 1000 of money AND 3 occurrences.
            long bucketId = id(c, "INSERT INTO benefit_limit_buckets (policy_id, benefit_group_id, code, name_ar, "
                    + "amount_limit, times_limit, period_type, counting_method, consumption_basis, active) VALUES ("
                    + policyId + ", " + groupId + ", 'TB-" + s
                    + "', 'وعاء', 1000, 3, 'ANNUAL', 'EACH_UNIT', 'COMPANY_SHARE', true) RETURNING id");
            long providerId = id(c, "INSERT INTO providers (name, license_number, provider_type) VALUES ('Prov "
                    + s + "', 'TLIC-" + s + "', 'CLINIC') RETURNING id");
            long preauthId = id(c, "INSERT INTO pre_authorizations (member_id, policy_id, provider_id, status, "
                    + "request_date, created_at, updated_at) VALUES (" + memberId + ", " + policyId + ", "
                    + providerId + ", 'APPROVED', now(), now(), now()) RETURNING id");
            long preauthLineId = id(c, "INSERT INTO pre_authorization_lines (pre_authorization_id, "
                    + "requested_amount) VALUES (" + preauthId + ", 500.00) RETURNING id");
            return new Fixture(memberId, policyId, bucketId, providerId, preauthId, preauthLineId);
        }
    }

    private long id(Connection c, String sql) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void exec(String sql) throws Exception {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    private long execId(String sql) throws Exception {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql + " RETURNING id");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private long insertLineSnapshot(Fixture f) throws Exception {
        long decisionId = execId("INSERT INTO preauth_decision_snapshots (preauth_id, calculation_version, "
                + "member_id, policy_id, expected_service_date, provider_id, requested_total, settlement_total, "
                + "authorized_service_total, rejected_total, patient_share_total, company_share_total, "
                + "decision_status, coverage_outcome, decided_by, idempotency_key) VALUES ("
                + f.preauthId() + ", 1, " + f.memberId() + ", " + f.policyId() + ", CURRENT_DATE + 14, "
                + f.providerId() + ", 500.00, 500.00, 500.00, 0, 100.00, 400.00, 'APPROVED', "
                + "'FULLY_COVERED', 'reviewer', 'K-" + suffix() + "')");

        return execId("INSERT INTO preauth_line_snapshots (decision_snapshot_id, preauth_line_id, quantity, "
                + "unit_price, requested_amount, approved_amount, settlement_amount, patient_share, company_share) VALUES ("
                + decisionId + ", " + f.preauthLineId() + ", 2, 250.00, 500.00, 500.00, 500.00, 100.00, 400.00)");
    }

    /** Both ceilings on one row: 1000 of money with 200 spent, 3 visits with 1 spent. */
    private String twoDimensionRow(long lineSnapshotId, Fixture f, String amountReserved,
            String timesReserved, String daysReserved) {
        return "INSERT INTO preauth_line_limit_snapshots (line_snapshot_id, limit_scope, limit_semantic_key, "
                + "bucket_id, policy_id, period_type, period_start, period_end, "
                + "effective_limit, committed_before, reserved_before, actual_remaining_before, "
                + "reservable_available_before, "
                + "times_limit, committed_times_before, reserved_times_before, "
                + "actual_remaining_times_before, reservable_times_before, "
                + "amount_consumption_basis, amount_unit, amount_reserved, times_reserved, days_reserved) VALUES ("
                + lineSnapshotId + ", 'BUCKET', 'BUCKET:" + f.bucketId() + "', " + f.bucketId() + ", "
                + f.policyId() + ", 'ANNUAL', CURRENT_DATE - 10, CURRENT_DATE + 355, "
                + "1000.00, 200.00, 0, 800.00, 800.00, "
                + "3, 1, 0, 2, 2, "
                + "'COMPANY_SHARE', 'CURRENCY', " + amountReserved + ", " + timesReserved + ", "
                + daysReserved + ")";
    }

    // ── the closure case ────────────────────────────────────────────────

    @Test
    void oneRowCarriesBothCeilingsAndReadsBackUnchanged() throws Exception {
        Fixture f = fixture();
        long lineSnapshotId = insertLineSnapshot(f);

        exec(twoDimensionRow(lineSnapshotId, f, "400.00", "2", "0"));

        // Read on a fresh connection: no persistence context, no cache.
        try (Connection c = conn();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT amount_reserved, times_reserved, days_reserved, effective_limit, times_limit, "
                                + "reservable_available_before, reservable_times_before "
                                + "FROM preauth_line_limit_snapshots WHERE line_snapshot_id = ?")) {
            ps.setLong(1, lineSnapshotId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();

                // Both dimensions survived, each in its own units.
                assertThat(rs.getBigDecimal("amount_reserved")).isEqualByComparingTo("400.00");
                assertThat(rs.getInt("times_reserved")).isEqualTo(2);
                assertThat(rs.getBigDecimal("effective_limit")).isEqualByComparingTo("1000.00");
                assertThat(rs.getInt("times_limit")).isEqualTo(3);
                assertThat(rs.getBigDecimal("reservable_available_before")).isEqualByComparingTo("800.00");
                assertThat(rs.getInt("reservable_times_before")).isEqualTo(2);
                assertThat(rs.getInt("days_reserved")).isZero();

                // Exactly ONE row for this bucket. Two would imply two
                // independent limits where the domain has one bucket.
                assertThat(rs.next()).as("one row per line x limit scope").isFalse();
            }
        }
    }

    @Test
    void aSnapshotRowIsStillAppendOnly() throws Exception {
        Fixture f = fixture();
        long lineSnapshotId = insertLineSnapshot(f);
        exec(twoDimensionRow(lineSnapshotId, f, "400.00", "2", "0"));

        assertThatThrownBy(() -> exec("UPDATE preauth_line_limit_snapshots SET times_reserved = 3 "
                + "WHERE line_snapshot_id = " + lineSnapshotId))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> exec("DELETE FROM preauth_line_limit_snapshots "
                + "WHERE line_snapshot_id = " + lineSnapshotId))
                .hasMessageContaining("append-only");
    }

    // ── each dimension is bounded on its own ────────────────────────────

    @Test
    void holdingMoreMoneyThanWasAvailableIsRejectedIndependentlyOfTheOccurrences() throws Exception {
        Fixture f = fixture();
        long lineSnapshotId = insertLineSnapshot(f);

        // Occurrences are fine (2 of 2); the money is not (900 of 800).
        assertThatThrownBy(() -> exec(twoDimensionRow(lineSnapshotId, f, "900.00", "2", "0")))
                .hasMessageContaining("chk_preauth_limit_snapshot_amount_bound");
    }

    @Test
    void holdingMoreOccurrencesThanWereAvailableIsRejectedIndependentlyOfTheMoney() throws Exception {
        Fixture f = fixture();
        long lineSnapshotId = insertLineSnapshot(f);

        // The money is fine (400 of 800); the occurrences are not (3 of 2).
        assertThatThrownBy(() -> exec(twoDimensionRow(lineSnapshotId, f, "400.00", "3", "0")))
                .hasMessageContaining("chk_preauth_limit_snapshot_times_bound");
    }

    @Test
    void aDayReservationIsRejectedBecauseNoneCanBeHonestlyDerived() throws Exception {
        Fixture f = fixture();
        long lineSnapshotId = insertLineSnapshot(f);

        // A pre-authorization has one expected date and no admission or
        // discharge behind it. A row claiming a day was held would assert
        // something the system cannot know.
        assertThatThrownBy(() -> exec(twoDimensionRow(lineSnapshotId, f, "400.00", "2", "1")))
                .hasMessageContaining("chk_preauth_limit_snapshot_no_day_reservation");
    }

    @Test
    void theGeneralCeilingMayNotClaimAnOccurrenceLimit() throws Exception {
        Fixture f = fixture();
        long lineSnapshotId = insertLineSnapshot(f);

        assertThatThrownBy(() -> exec(
                "INSERT INTO preauth_line_limit_snapshots (line_snapshot_id, limit_scope, limit_semantic_key, "
                        + "policy_id, period_type, period_start, period_end, effective_limit, committed_before, "
                        + "reserved_before, actual_remaining_before, reservable_available_before, "
                        + "times_limit, committed_times_before, reserved_times_before, "
                        + "actual_remaining_times_before, reservable_times_before, "
                        + "amount_consumption_basis, amount_unit, amount_reserved, times_reserved) VALUES ("
                        + lineSnapshotId + ", 'POLICY_GENERAL', 'POLICY_GENERAL:" + f.policyId() + "', "
                        + f.policyId() + ", 'ANNUAL', CURRENT_DATE - 10, CURRENT_DATE + 355, "
                        + "10000.00, 0, 0, 10000.00, 10000.00, 5, 0, 0, 5, 5, "
                        + "'COMPANY_SHARE', 'CURRENCY', 400.00, 1)"))
                .hasMessageContaining("chk_preauth_limit_snapshot_general_is_monetary");
    }

    // ── an exhausted ceiling is still recorded ──────────────────────────

    @Test
    void anExhaustedCeilingIsRecordedWithZeroHoldsAndNoLedgerMovement() throws Exception {
        Fixture f = fixture();
        long lineSnapshotId = insertLineSnapshot(f);

        long ledgerBefore = ledgerRowCount();

        // Nothing left in either dimension. The snapshot still explains the
        // decision -- that is its whole purpose -- but there is no financial
        // or numerical trace to post, so the ledger stays untouched.
        exec("INSERT INTO preauth_line_limit_snapshots (line_snapshot_id, limit_scope, limit_semantic_key, "
                + "bucket_id, policy_id, period_type, period_start, period_end, effective_limit, "
                + "committed_before, reserved_before, actual_remaining_before, reservable_available_before, "
                + "times_limit, committed_times_before, reserved_times_before, actual_remaining_times_before, "
                + "reservable_times_before, amount_consumption_basis, amount_unit, amount_reserved, times_reserved) "
                + "VALUES (" + lineSnapshotId + ", 'BUCKET', 'BUCKET:" + f.bucketId() + "', " + f.bucketId()
                + ", " + f.policyId() + ", 'ANNUAL', CURRENT_DATE - 10, CURRENT_DATE + 355, "
                + "1000.00, 1000.00, 0, 0, 0, 3, 3, 0, 0, 0, 'COMPANY_SHARE', 'CURRENCY', 0, 0)");

        try (Connection c = conn();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT amount_reserved, times_reserved, reservable_available_before, "
                                + "reservable_times_before FROM preauth_line_limit_snapshots "
                                + "WHERE line_snapshot_id = ?")) {
            ps.setLong(1, lineSnapshotId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBigDecimal("reservable_available_before")).isEqualByComparingTo("0");
                assertThat(rs.getInt("reservable_times_before")).isZero();
                assertThat(rs.getBigDecimal("amount_reserved")).isEqualByComparingTo("0");
                assertThat(rs.getInt("times_reserved")).isZero();
            }
        }

        assertThat(ledgerRowCount())
                .as("a zero hold in both dimensions posts no ledger movement")
                .isEqualTo(ledgerBefore);
    }

    private long ledgerRowCount() throws Exception {
        try (Connection c = conn();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM benefit_bucket_consumptions WHERE status = 'RESERVED'");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
