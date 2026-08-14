package com.waad.tba.support;

import static org.assertj.core.api.Assertions.assertThat;

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
 * V174 must be able to represent new things without changing what the ledger
 * already says. This is the test that proves it, and it is the only one that
 * can: every other test in this change runs against a schema where V174 has
 * already been applied, so none of them can see a balance move.
 *
 * The rows here are built in the PRE-V174 model -- migrated only as far as
 * V173, inserted with the columns that existed then -- and every figure the
 * system derives from them is captured. V174 is then applied and the same
 * figures are recomputed with the same SQL.
 *
 * Compared literally: no rounding, no clamping at zero, no tolerance. A
 * migration that shifts a member's remaining limit by a single unit is a
 * migration that lost money, and a comparison that tolerates it is worthless.
 */
class LedgerBalanceParityAcrossV174MigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ledger_parity").withUsername("test_user").withPassword("test_password");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    /** Everything the ledger derives for one member, in one shot. */
    private record Balances(
            BigDecimal netCommitted,
            BigDecimal netReversedTotal,
            BigDecimal actualRemaining,
            BigDecimal reservableAvailable,
            long timesConsumed,
            long rowCount) {}

    private static final BigDecimal BUCKET_LIMIT = new BigDecimal("5000.00");

    @Test
    void v174ChangesNoExistingBalance() throws SQLException {
        migrateTo("173");

        long bucketId;
        long memberId;
        Balances before;

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            long[] ids = insertLegacyLedgerRows(conn);
            memberId = ids[0];
            bucketId = ids[1];
            before = readBalances(conn, memberId, bucketId);
        }

        // The figures must be non-trivial: a parity test over zeroes proves
        // nothing, and would pass even if V174 deleted every row.
        assertThat(before.rowCount()).isGreaterThanOrEqualTo(4);
        assertThat(before.netCommitted()).isGreaterThan(BigDecimal.ZERO);
        assertThat(before.netReversedTotal()).isGreaterThan(BigDecimal.ZERO);
        assertThat(before.timesConsumed()).isPositive();

        migrateToLatest();

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Balances after = readBalances(conn, memberId, bucketId);

            // isEqualByComparingTo, not isEqualTo: 100.00 and 100.000 are the
            // same amount of money, and a scale change is not a balance change.
            assertThat(after.netCommitted()).isEqualByComparingTo(before.netCommitted());
            assertThat(after.netReversedTotal()).isEqualByComparingTo(before.netReversedTotal());
            assertThat(after.actualRemaining()).isEqualByComparingTo(before.actualRemaining());
            assertThat(after.reservableAvailable()).isEqualByComparingTo(before.reservableAvailable());
            assertThat(after.timesConsumed()).isEqualTo(before.timesConsumed());
            assertThat(after.rowCount()).isEqualTo(before.rowCount());

            // And the backfill must have classified every one of them, rather
            // than leaving rows that only appear correct because they are
            // excluded from the reads above.
            try (Statement st = conn.createStatement();
                    ResultSet rs = st.executeQuery(
                            "SELECT COUNT(*) FROM benefit_bucket_consumptions "
                                    + "WHERE limit_scope <> 'BUCKET' OR source_type <> 'CLAIM'")) {
                rs.next();
                assertThat(rs.getLong(1))
                        .as("every pre-existing row is a claim-sourced bucket movement")
                        .isZero();
            }
        }
    }

    /**
     * Reads with the SAME SQL on both sides of the migration, so a difference
     * can only come from the data. Net semantics (V173): an original's value is
     * its amount minus the sum of its compensating movements.
     */
    private Balances readBalances(Connection conn, long memberId, long bucketId) throws SQLException {
        String netJoin = """
                  from benefit_bucket_consumptions c
                  left join (
                        select reversal_of_id, sum(approved_amount) as reversed_amount
                          from benefit_bucket_consumptions
                         where status = 'REVERSED' and reversal_of_id is not null
                         group by reversal_of_id
                  ) r on r.reversal_of_id = c.id
                 where c.member_id = %d and c.bucket_id = %d
                """.formatted(memberId, bucketId);

        BigDecimal netCommitted = scalar(conn,
                "select coalesce(sum(c.approved_amount - coalesce(r.reversed_amount, 0)), 0) "
                        + netJoin + " and c.status = 'COMMITTED'");
        BigDecimal netReserved = scalar(conn,
                "select coalesce(sum(c.approved_amount - coalesce(r.reversed_amount, 0)), 0) "
                        + netJoin + " and c.status = 'RESERVED'");
        BigDecimal reversedTotal = scalar(conn,
                "select coalesce(sum(c.approved_amount), 0) "
                        + netJoin + " and c.status = 'REVERSED'");
        BigDecimal times = scalar(conn,
                "select coalesce(sum(c.times_consumed), 0) " + netJoin + " and c.status = 'COMMITTED'");
        BigDecimal rows = scalar(conn, "select count(*) " + netJoin);

        // The same two-figure split the application uses: what is left, and
        // what may still be held against it.
        BigDecimal actualRemaining = BUCKET_LIMIT.subtract(netCommitted);
        BigDecimal reservableAvailable = actualRemaining.subtract(netReserved);

        return new Balances(netCommitted, reversedTotal, actualRemaining, reservableAvailable,
                times.longValue(), rows.longValue());
    }

    private BigDecimal scalar(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            BigDecimal value = rs.getBigDecimal(1);
            return value == null ? BigDecimal.ZERO : value;
        }
    }

    /**
     * Builds rows exactly as the pre-V174 schema demanded: claim_id,
     * claim_line_id and bucket_id all NOT NULL, and no limit_scope column to
     * populate. Includes a partially compensated original, so the "net"
     * arithmetic is genuinely exercised rather than a single clean total.
     *
     * @return {memberId, bucketId}
     */
    private long[] insertLegacyLedgerRows(Connection conn) throws SQLException {
        String s = String.valueOf(System.nanoTime());

        long employerId = id(conn, "INSERT INTO employers (code, name) VALUES ('PAR-" + s
                + "', 'Parity Co " + s + "') RETURNING id");
        long policyId = id(conn, "INSERT INTO benefit_policies (name, policy_code, employer_id, annual_limit, "
                + "default_coverage_percent, start_date, end_date, status, active) VALUES ('PP-" + s
                + "', 'PPOL-" + s + "', " + employerId
                + ", 10000, 80, CURRENT_DATE - 30, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id");
        long memberId = id(conn, "INSERT INTO members (employer_id, full_name, benefit_policy_id, card_number, "
                + "barcode, status, active) VALUES (" + employerId + ", 'Parity Member', " + policyId
                + ", 'PC" + s + "', 'PC" + s + "', 'ACTIVE', true) RETURNING id");
        long groupId = id(conn, "INSERT INTO benefit_groups (policy_id, code, name_ar, aggregation_mode) VALUES ("
                + policyId + ", 'PG-" + s + "', 'مجموعة', 'INDIVIDUAL') RETURNING id");
        long bucketId = id(conn, "INSERT INTO benefit_limit_buckets (policy_id, benefit_group_id, code, name_ar, "
                + "amount_limit, period_type, counting_method, consumption_basis, active) VALUES (" + policyId
                + ", " + groupId + ", 'PB-" + s + "', 'وعاء', " + BUCKET_LIMIT
                + ", 'ANNUAL', 'EACH_LINE', 'ELIGIBLE_AMOUNT', true) RETURNING id");
        long providerId = id(conn, "INSERT INTO providers (name, license_number, provider_type) VALUES ('Prov "
                + s + "', 'PLIC-" + s + "', 'CLINIC') RETURNING id");
        long visitId = id(conn, "INSERT INTO visits (member_id, provider_id, visit_date) VALUES (" + memberId
                + ", " + providerId + ", CURRENT_DATE - 5) RETURNING id");
        long claimId = id(conn, "INSERT INTO claims (claim_number, member_id, provider_id, visit_id, "
                + "service_date, requested_amount, status) VALUES ('PCLM-" + s + "', " + memberId + ", "
                + providerId + ", " + visitId + ", CURRENT_DATE - 5, 900.00, 'APPROVED') RETURNING id");

        long firstLine = id(conn, "INSERT INTO claim_lines (claim_id, service_code, quantity, unit_price, "
                + "total_price) VALUES (" + claimId + ", 'PS1-" + s + "', 1, 400.00, 400.00) RETURNING id");
        long secondLine = id(conn, "INSERT INTO claim_lines (claim_id, service_code, quantity, unit_price, "
                + "total_price) VALUES (" + claimId + ", 'PS2-" + s + "', 1, 500.00, 500.00) RETURNING id");

        // Two committed originals...
        long committedA = insertLegacyConsumption(conn, policyId, memberId, bucketId, claimId, firstLine,
                "400.00", 1, "COMMITTED", null, "legacy-a-" + s);
        insertLegacyConsumption(conn, policyId, memberId, bucketId, claimId, secondLine,
                "500.00", 2, "COMMITTED", null, "legacy-b-" + s);

        // ...one of them PARTIALLY compensated, which only the net model can
        // express correctly. A migration that mishandled reversals would show
        // up here and nowhere else.
        insertLegacyConsumption(conn, policyId, memberId, bucketId, claimId, firstLine,
                "150.00", 0, "REVERSED", committedA, "legacy-rev-" + s);
        insertLegacyConsumption(conn, policyId, memberId, bucketId, claimId, firstLine,
                "50.00", 0, "REVERSED", committedA, "legacy-rev2-" + s);

        return new long[] {memberId, bucketId};
    }

    private long insertLegacyConsumption(Connection conn, long policyId, long memberId, long bucketId,
            long claimId, long claimLineId, String amount, int times, String status,
            Long reversalOf, String key) throws SQLException {
        return id(conn, "INSERT INTO benefit_bucket_consumptions "
                + "(policy_id, member_id, bucket_id, claim_id, claim_line_id, period_start, period_end, "
                + " approved_amount, times_consumed, calculation_version, idempotency_key, status, "
                + " reversal_of_id, reversal_reason, source_type, created_at) VALUES ("
                + policyId + ", " + memberId + ", " + bucketId + ", " + claimId + ", " + claimLineId
                + ", CURRENT_DATE - 10, CURRENT_DATE + 355, " + amount + ", " + times + ", 1, '" + key
                + "', '" + status + "', " + (reversalOf == null ? "NULL" : reversalOf) + ", "
                + (reversalOf == null ? "NULL" : "'CLAIM_REVERSAL'") + ", 'CLAIM', now()) RETURNING id");
    }

    private long id(Connection conn, String sql) throws SQLException {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private void migrateToLatest() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }
}
