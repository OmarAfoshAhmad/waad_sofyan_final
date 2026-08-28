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
 * V182 gives the occurrence dimension the ceiling the amount has had since
 * V174: a compensating movement may not return more visits than the original
 * took.
 *
 * Run against a database that already holds committed movements AND real
 * compensating rows before the migration applies. Three migrations in this
 * project (V174, V180, V181) failed on contact with data while passing every
 * empty-table test, so a migration touching the ledger is not trusted until
 * it has been run over rows.
 */
class OccurrenceReversalCeilingAcrossV182MigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("v182_migration").withUsername("test_user").withPassword("test_password");

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

    private BigDecimal scalar(String sql) throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            BigDecimal v = rs.getBigDecimal(1);
            return v == null ? BigDecimal.ZERO : v;
        }
    }

    private long count(String sql) throws SQLException {
        try (Connection c = conn(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private long originalId;
    private long bucketId;

    /** Committed movement plus a genuine partial release, in the pre-V182 model. */
    private void seedAtV181() throws SQLException {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").target("181").load().migrate();

        try (Connection c = conn()) {
            String s = String.valueOf(System.nanoTime());
            long employerId = id(c, "INSERT INTO employers (code, name) VALUES ('V182-" + s
                    + "', 'V182 Co " + s + "') RETURNING id");
            long policyId = id(c, "INSERT INTO benefit_policies (name, policy_code, employer_id, "
                    + "annual_limit, default_coverage_percent, start_date, end_date, status, active) VALUES "
                    + "('V182P-" + s + "', 'V182POL-" + s + "', " + employerId
                    + ", 100000, 80, CURRENT_DATE - 30, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id");
            long memberId = id(c, "INSERT INTO members (employer_id, full_name, benefit_policy_id, "
                    + "card_number, barcode, status, active) VALUES (" + employerId + ", 'V182 Member', "
                    + policyId + ", 'V182C" + s + "', 'V182C" + s + "', 'ACTIVE', true) RETURNING id");
            long groupId = id(c, "INSERT INTO benefit_groups (policy_id, code, name_ar, aggregation_mode) "
                    + "VALUES (" + policyId + ", 'V182G-" + s + "', 'مجموعة', 'INDIVIDUAL') RETURNING id");
            bucketId = id(c, "INSERT INTO benefit_limit_buckets (policy_id, benefit_group_id, code, "
                    + "name_ar, amount_limit, times_limit, period_type, counting_method, consumption_basis, "
                    + "active) VALUES (" + policyId + ", " + groupId + ", 'V182B-" + s
                    + "', 'وعاء', 10000, 10, 'ANNUAL', 'EACH_LINE', 'COMPANY_SHARE', true) RETURNING id");
            long providerId = id(c, "INSERT INTO providers (name, license_number, provider_type) VALUES "
                    + "('Prov " + s + "', 'V182LIC-" + s + "', 'CLINIC') RETURNING id");
            long visitId = id(c, "INSERT INTO visits (member_id, provider_id, visit_date) VALUES ("
                    + memberId + ", " + providerId + ", CURRENT_DATE) RETURNING id");
            long claimId = id(c, "INSERT INTO claims (claim_number, member_id, provider_id, visit_id, "
                    + "service_date, requested_amount, status) VALUES ('V182CLM-" + s + "', " + memberId
                    + ", " + providerId + ", " + visitId + ", CURRENT_DATE, 500.00, 'APPROVED') RETURNING id");
            long lineId = id(c, "INSERT INTO claim_lines (claim_id, service_code, quantity, unit_price, "
                    + "total_price) VALUES (" + claimId + ", 'V182S-" + s
                    + "', 1, 500.00, 500.00) RETURNING id");

            originalId = id(c, "INSERT INTO benefit_bucket_consumptions (policy_id, member_id, bucket_id, "
                    + "claim_id, claim_line_id, period_start, period_end, approved_amount, times_consumed, "
                    + "calculation_version, idempotency_key, status, source_type, limit_scope, created_at) "
                    + "VALUES (" + policyId + ", " + memberId + ", " + bucketId + ", " + claimId + ", "
                    + lineId + ", DATE_TRUNC('year', CURRENT_DATE)::date, "
                    + "(DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date, "
                    + "500.00, 4, 1, 'V182K-" + s + "', 'COMMITTED', 'CLAIM', 'BUCKET', now()) RETURNING id");

            // A legitimate partial release already on the books before V182.
            id(c, "INSERT INTO benefit_bucket_consumptions (policy_id, member_id, bucket_id, claim_id, "
                    + "claim_line_id, period_start, period_end, approved_amount, times_consumed, "
                    + "calculation_version, idempotency_key, status, source_type, limit_scope, "
                    + "reversal_of_id, reversal_reason, created_at) VALUES (" + policyId + ", " + memberId
                    + ", " + bucketId + ", " + claimId + ", " + lineId
                    + ", DATE_TRUNC('year', CURRENT_DATE)::date, "
                    + "(DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date, "
                    + "200.00, 1, 1, 'V182R-" + s + "', 'REVERSED', 'CLAIM', 'BUCKET', " + originalId
                    + ", 'CLAIM_REVERSAL', now()) RETURNING id");
        }
    }

    @Test
    void v182AppliesOverRealRowsWithoutMovingABalanceAndThenEnforcesTheCeiling() throws SQLException {
        seedAtV181();

        long rowsBefore = count("SELECT COUNT(*) FROM benefit_bucket_consumptions");
        BigDecimal amountBefore = scalar("SELECT SUM(approved_amount) FROM benefit_bucket_consumptions");
        long timesBefore = count("SELECT SUM(times_consumed) FROM benefit_bucket_consumptions");

        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();

        // Nothing moved. The migration replaces a trigger function; a balance
        // that shifted would mean it had rewritten history.
        assertThat(count("SELECT COUNT(*) FROM benefit_bucket_consumptions")).isEqualTo(rowsBefore);
        assertThat(scalar("SELECT SUM(approved_amount) FROM benefit_bucket_consumptions"))
                .isEqualByComparingTo(amountBefore);
        assertThat(count("SELECT SUM(times_consumed) FROM benefit_bucket_consumptions"))
                .isEqualTo(timesBefore);

        // The append-only guards must still be armed afterwards.
        assertThat(count("SELECT COUNT(*) FROM pg_trigger WHERE tgname IN "
                + "('trg_no_update_bucket_consumptions', 'trg_no_delete_bucket_consumptions', "
                + " 'trg_validate_bucket_consumption_reversal') AND tgenabled <> 'D'"))
                .as("immutability and validation triggers stay enabled").isEqualTo(3L);

        // 4 taken, 1 already returned. Two more is legitimate.
        exec(partialRelease("100.00", 2, "V182OK"));
        assertThat(count("SELECT SUM(times_consumed) FROM benefit_bucket_consumptions "
                + "WHERE status = 'REVERSED' AND reversal_of_id = " + originalId)).isEqualTo(3L);

        // A fourth would exceed the original's 4. This is the rule V182 adds.
        assertThatThrownBy(() -> exec(partialRelease("0.00", 2, "V182BAD")))
                .hasMessageContaining("would exceed the original count");

        // And the amount ceiling from V174 is untouched by the replacement.
        assertThatThrownBy(() -> exec(partialRelease("9000.00", 0, "V182BADAMT")))
                .hasMessageContaining("would exceed the original amount");
    }

    private String partialRelease(String amount, int times, String key) {
        return "INSERT INTO benefit_bucket_consumptions (policy_id, member_id, bucket_id, claim_id, "
                + "claim_line_id, period_start, period_end, approved_amount, times_consumed, "
                + "calculation_version, idempotency_key, status, source_type, limit_scope, "
                + "reversal_of_id, reversal_reason, created_at) SELECT policy_id, member_id, bucket_id, "
                + "claim_id, claim_line_id, period_start, period_end, " + amount + ", " + times
                + ", 1, '" + key + "-" + System.nanoTime() + "', 'REVERSED', source_type, limit_scope, "
                + "id, 'CLAIM_REVERSAL', now() FROM benefit_bucket_consumptions WHERE id = " + originalId;
    }
}
