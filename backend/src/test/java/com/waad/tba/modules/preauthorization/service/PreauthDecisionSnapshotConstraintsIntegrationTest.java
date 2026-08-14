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
 * The decision snapshot's rules, proven before any service writes one.
 *
 * These tables exist so a conversion in September can be settled on March's
 * basis. That only holds if the rows cannot drift: if a snapshot can be edited,
 * or can record shares that do not add up, or can claim a hold larger than the
 * balance that was available, then trusting it at conversion is worse than
 * re-reading the live configuration -- it would be wrong AND look authoritative.
 *
 * Inserted through raw SQL deliberately: these are database rules, and a
 * JPA-mediated insert could satisfy them by accident.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class PreauthDecisionSnapshotConstraintsIntegrationTest extends PostgresIntegrationTestBase {

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
            long employerId = id(c, "INSERT INTO employers (code, name) VALUES ('SN-" + s
                    + "', 'Snapshot Co " + s + "') RETURNING id");
            long policyId = id(c, "INSERT INTO benefit_policies (name, policy_code, employer_id, annual_limit, "
                    + "default_coverage_percent, start_date, end_date, status, active) VALUES ('SP-" + s
                    + "', 'SPOL-" + s + "', " + employerId
                    + ", 10000, 80, CURRENT_DATE - 30, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id");
            long memberId = id(c, "INSERT INTO members (employer_id, full_name, benefit_policy_id, card_number, "
                    + "barcode, status, active) VALUES (" + employerId + ", 'Snapshot Member', " + policyId
                    + ", 'SC" + s + "', 'SC" + s + "', 'ACTIVE', true) RETURNING id");
            long groupId = id(c, "INSERT INTO benefit_groups (policy_id, code, name_ar, aggregation_mode) "
                    + "VALUES (" + policyId + ", 'SG-" + s + "', 'مجموعة', 'INDIVIDUAL') RETURNING id");
            long bucketId = id(c, "INSERT INTO benefit_limit_buckets (policy_id, benefit_group_id, code, name_ar, "
                    + "amount_limit, period_type, counting_method, consumption_basis, active) VALUES (" + policyId
                    + ", " + groupId + ", 'SB-" + s
                    + "', 'وعاء', 5000, 'ANNUAL', 'EACH_LINE', 'ELIGIBLE_AMOUNT', true) RETURNING id");
            long providerId = id(c, "INSERT INTO providers (name, license_number, provider_type) VALUES ('Prov "
                    + s + "', 'SLIC-" + s + "', 'CLINIC') RETURNING id");
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

    /** A decision snapshot varying only what a test is testing. */
    private String decisionSql(Fixture f, String status, String requested, String approved,
            String patient, String company, String key) {
        return "INSERT INTO preauth_decision_snapshots (preauth_id, calculation_version, member_id, policy_id, "
                + "expected_service_date, provider_id, requested_total, approved_total, rejected_total, "
                + "patient_share_total, company_share_total, decision_status, decided_by, idempotency_key) VALUES ("
                + f.preauthId() + ", 1, " + f.memberId() + ", " + f.policyId()
                + ", CURRENT_DATE + 14, " + f.providerId() + ", " + requested + ", " + approved
                + ", 0, " + patient + ", " + company + ", '" + status + "', 'reviewer', '" + key + "')";
    }

    private long insertDecision(Fixture f) throws Exception {
        return execId(decisionSql(f, "APPROVED", "500.00", "500.00", "100.00", "400.00", "K-" + suffix()));
    }

    private long insertLine(Fixture f, long decisionId) throws Exception {
        return execId("INSERT INTO preauth_line_snapshots (decision_snapshot_id, preauth_line_id, quantity, "
                + "unit_price, requested_amount, approved_amount, patient_share, company_share) VALUES ("
                + decisionId + ", " + f.preauthLineId() + ", 1, 500.00, 500.00, 500.00, 100.00, 400.00)");
    }

    private String limitSql(long lineSnapshotId, Fixture f, String scope, Long bucketId, String limit,
            String committed, String reserved, String actualRemaining, String reservable, String reservedAmount) {
        return "INSERT INTO preauth_line_limit_snapshots (line_snapshot_id, limit_scope, limit_semantic_key, "
                + "bucket_id, policy_id, period_type, period_start, period_end, effective_limit, "
                + "committed_before, reserved_before, actual_remaining_before, reservable_available_before, "
                + "reserved_amount) VALUES (" + lineSnapshotId + ", '" + scope + "', '" + scope + ":"
                + suffix() + "', " + (bucketId == null ? "NULL" : bucketId) + ", " + f.policyId()
                + ", 'ANNUAL', CURRENT_DATE - 10, CURRENT_DATE + 355, " + limit + ", " + committed + ", "
                + reserved + ", " + actualRemaining + ", " + reservable + ", " + reservedAmount + ")";
    }

    // ── the happy path, so the rejections below mean something ──────────

    @Test
    void aCompleteDecisionSnapshotIsAccepted() throws Exception {
        Fixture f = fixture();
        long decisionId = insertDecision(f);
        long lineId = insertLine(f, decisionId);

        // A bucket limit and the general ceiling, recorded separately -- the
        // split that stops one line's several scopes being summed into one
        // figure.
        exec(limitSql(lineId, f, "BUCKET", f.bucketId(), "5000.00", "1000.00", "0", "4000.00", "4000.00", "500.00"));
        exec(limitSql(lineId, f, "POLICY_GENERAL", null, "10000.00", "2000.00", "0", "8000.00", "8000.00", "500.00"));
    }

    // ── the basis cannot be incoherent ──────────────────────────────────

    @Test
    void sharesThatDoNotAccountForTheApprovedTotalAreRejected() throws Exception {
        Fixture f = fixture();
        assertThatThrownBy(() -> exec(decisionSql(f, "APPROVED", "500.00", "500.00",
                "100.00", "300.00", "K-" + suffix())))
                .hasMessageContaining("chk_preauth_snapshot_shares");
    }

    // The three status/total rules overlap deliberately -- each states a
    // separate thing, and any of them alone would reject these rows. Postgres
    // does not fix which constraint reports first, so these assert that a
    // snapshot rule refused the row, not which one won the race.
    @Test
    void approvingMoreThanWasRequestedIsRejected() throws Exception {
        Fixture f = fixture();
        assertThatThrownBy(() -> exec(decisionSql(f, "APPROVED", "500.00", "600.00",
                "100.00", "500.00", "K-" + suffix())))
                .hasMessageContaining("chk_preauth_snapshot_");
    }

    @Test
    void aPartialApprovalThatApprovedEverythingIsRejected() throws Exception {
        Fixture f = fixture();
        assertThatThrownBy(() -> exec(decisionSql(f, "PARTIALLY_APPROVED", "500.00", "500.00",
                "100.00", "400.00", "K-" + suffix())))
                .hasMessageContaining("chk_preauth_snapshot_partial");
    }

    @Test
    void aRejectedDecisionMayNotProduceASnapshot() throws Exception {
        Fixture f = fixture();
        // Only a decision that grants something can hold a member's limit.
        assertThatThrownBy(() -> exec(decisionSql(f, "REJECTED", "500.00", "0", "0", "0", "K-" + suffix())))
                .hasMessageContaining("chk_preauth_snapshot_");
    }

    @Test
    void aDiscountWithoutTheTermsThatSetItIsRejected() throws Exception {
        Fixture f = fixture();
        assertThatThrownBy(() -> exec(
                "INSERT INTO preauth_decision_snapshots (preauth_id, calculation_version, member_id, policy_id, "
                        + "expected_service_date, provider_id, discount_percent, requested_total, approved_total, "
                        + "patient_share_total, company_share_total, decision_status, decided_by, idempotency_key) "
                        + "VALUES (" + f.preauthId() + ", 1, " + f.memberId() + ", " + f.policyId()
                        + ", CURRENT_DATE + 14, " + f.providerId()
                        + ", 15.00, 500.00, 500.00, 100.00, 400.00, 'APPROVED', 'reviewer', 'K-" + suffix() + "')"))
                .hasMessageContaining("chk_preauth_snapshot_contract_shape");
    }

    @Test
    void twoSnapshotsForTheSameApprovalVersionAreRejected() throws Exception {
        Fixture f = fixture();
        insertDecision(f);
        assertThatThrownBy(() -> insertDecision(f))
                .hasMessageContaining("uq_preauth_snapshot");
    }

    // ── a line must belong to the decision it describes ─────────────────

    @Test
    void aLineFromAnotherPreAuthorizationIsRejected() throws Exception {
        Fixture f = fixture();
        Fixture other = fixture();
        long decisionId = insertDecision(f);

        assertThatThrownBy(() -> exec("INSERT INTO preauth_line_snapshots (decision_snapshot_id, preauth_line_id, "
                + "quantity, unit_price, requested_amount, approved_amount, patient_share, company_share) VALUES ("
                + decisionId + ", " + other.preauthLineId() + ", 1, 500.00, 500.00, 500.00, 100.00, 400.00)"))
                .hasMessageContaining("but this snapshot describes");
    }

    @Test
    void lineSharesMustAccountForTheApprovedAmount() throws Exception {
        Fixture f = fixture();
        long decisionId = insertDecision(f);

        assertThatThrownBy(() -> exec("INSERT INTO preauth_line_snapshots (decision_snapshot_id, preauth_line_id, "
                + "quantity, unit_price, requested_amount, approved_amount, patient_share, company_share) VALUES ("
                + decisionId + ", " + f.preauthLineId() + ", 1, 500.00, 500.00, 500.00, 100.00, 100.00)"))
                .hasMessageContaining("chk_preauth_line_snapshot_shares");
    }

    // ── the balances must be arithmetically true ────────────────────────

    @Test
    void aRestatedActualRemainingIsRejected() throws Exception {
        Fixture f = fixture();
        long lineId = insertLine(f, insertDecision(f));
        // 5000 - 1000 is 4000, not 4500. A snapshot that misstates the balance
        // it decided against is worse than no snapshot.
        assertThatThrownBy(() -> exec(limitSql(lineId, f, "BUCKET", f.bucketId(),
                "5000.00", "1000.00", "0", "4500.00", "4500.00", "100.00")))
                .hasMessageContaining("chk_preauth_limit_snapshot_actual_remaining");
    }

    @Test
    void aReservableAvailableThatIgnoresExistingHoldsIsRejected() throws Exception {
        Fixture f = fixture();
        long lineId = insertLine(f, insertDecision(f));
        // 4000 actual remaining minus a 1000 existing hold leaves 3000 that a
        // NEW decision may take -- not 4000. Getting this wrong is exactly how
        // the same limit gets spent twice.
        assertThatThrownBy(() -> exec(limitSql(lineId, f, "BUCKET", f.bucketId(),
                "5000.00", "1000.00", "1000.00", "4000.00", "4000.00", "100.00")))
                .hasMessageContaining("chk_preauth_limit_snapshot_reservable");
    }

    @Test
    void holdingMoreThanWasAvailableIsRejected() throws Exception {
        Fixture f = fixture();
        long lineId = insertLine(f, insertDecision(f));
        assertThatThrownBy(() -> exec(limitSql(lineId, f, "BUCKET", f.bucketId(),
                "5000.00", "4900.00", "0", "100.00", "100.00", "500.00")))
                .hasMessageContaining("chk_preauth_limit_snapshot_within_available");
    }

    @Test
    void theGeneralCeilingMayNotCarryABucketAndABucketScopeMustNameOne() throws Exception {
        Fixture f = fixture();
        long lineId = insertLine(f, insertDecision(f));

        assertThatThrownBy(() -> exec(limitSql(lineId, f, "POLICY_GENERAL", f.bucketId(),
                "5000.00", "0", "0", "5000.00", "5000.00", "100.00")))
                .hasMessageContaining("chk_preauth_limit_snapshot_bucket");

        assertThatThrownBy(() -> exec(limitSql(lineId, f, "BUCKET", null,
                "5000.00", "0", "0", "5000.00", "5000.00", "100.00")))
                .hasMessageContaining("chk_preauth_limit_snapshot_bucket");
    }

    // ── append-only ─────────────────────────────────────────────────────

    @Test
    void noSnapshotRowMayBeEditedOrDeleted() throws Exception {
        Fixture f = fixture();
        long decisionId = insertDecision(f);
        long lineId = insertLine(f, decisionId);
        exec(limitSql(lineId, f, "BUCKET", f.bucketId(), "5000.00", "0", "0", "5000.00", "5000.00", "500.00"));

        // Conversion trusts these rows precisely because nothing between
        // approval and conversion can have altered them.
        assertThatThrownBy(() -> exec(
                "UPDATE preauth_decision_snapshots SET approved_total = 1.00 WHERE id = " + decisionId))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> exec(
                "DELETE FROM preauth_decision_snapshots WHERE id = " + decisionId))
                .hasMessageContaining("append-only");

        assertThatThrownBy(() -> exec(
                "UPDATE preauth_line_snapshots SET approved_amount = 1.00 WHERE id = " + lineId))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> exec(
                "DELETE FROM preauth_line_snapshots WHERE id = " + lineId))
                .hasMessageContaining("append-only");

        assertThatThrownBy(() -> exec(
                "UPDATE preauth_line_limit_snapshots SET reserved_amount = 1.00 "
                        + "WHERE line_snapshot_id = " + lineId))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> exec(
                "DELETE FROM preauth_line_limit_snapshots WHERE line_snapshot_id = " + lineId))
                .hasMessageContaining("append-only");
    }

    @Test
    void aCorrectedDecisionIsANewVersionRatherThanAnEdit() throws Exception {
        Fixture f = fixture();
        insertDecision(f);

        // The way a decision is revised: another version alongside the first,
        // leaving the original readable.
        exec("INSERT INTO preauth_decision_snapshots (preauth_id, calculation_version, member_id, policy_id, "
                + "expected_service_date, provider_id, requested_total, approved_total, rejected_total, "
                + "patient_share_total, company_share_total, decision_status, decided_by, idempotency_key) VALUES ("
                + f.preauthId() + ", 2, " + f.memberId() + ", " + f.policyId() + ", CURRENT_DATE + 14, "
                + f.providerId() + ", 500.00, 400.00, 100.00, 80.00, 320.00, 'PARTIALLY_APPROVED', "
                + "'reviewer', 'K-" + suffix() + "')");

        try (Connection c = conn();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM preauth_decision_snapshots WHERE preauth_id = ?")) {
            ps.setLong(1, f.preauthId());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(2);
            }
        }
    }
}
