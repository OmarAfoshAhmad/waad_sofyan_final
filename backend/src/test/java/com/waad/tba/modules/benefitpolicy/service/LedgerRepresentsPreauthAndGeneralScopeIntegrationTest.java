package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * Structural closure for the ledger's ability to REPRESENT a
 * pre-authorization hold and a general-ceiling movement (V174). Nothing in
 * production writes a reservation yet -- the approval service is the next
 * step, and is the first component allowed to. What these tests pin is that
 * the model can now store and read the new shapes, that the database refuses
 * every malformed combination, and that existing balances are untouched.
 *
 * Rows are inserted through raw SQL on purpose: the point is to exercise the
 * DATABASE constraints, which a JPA-mediated insert could mask.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class LedgerRepresentsPreauthAndGeneralScopeIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private BenefitBucketConsumptionRepository consumptionRepository;

    /** One fixed period, so an assertion can never drift from the row it reads. */
    private static final LocalDate PERIOD_START = LocalDate.now().minusDays(10);
    private static final LocalDate PERIOD_END = LocalDate.now().plusDays(355);

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private Connection conn() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** Minimal member + policy + bucket, created via SQL to stay independent of entity wiring. */
    private record Fixture(long memberId, long policyId, long bucketId, long preauthId, long preauthLineId,
            long claimId, long claimLineId) {}

    private Fixture fixture() throws Exception {
        String s = suffix();
        try (Connection c = conn()) {
            long employerId = insert(c, "INSERT INTO employers (code, name) VALUES ('LG-" + s
                    + "', 'Ledger Co " + s + "') RETURNING id");
            long policyId = insert(c, "INSERT INTO benefit_policies (name, policy_code, employer_id, "
                    + "annual_limit, default_coverage_percent, start_date, end_date, status, active) VALUES ('P-" + s
                    + "', 'POL-" + s + "', " + employerId
                    + ", 10000, 80, CURRENT_DATE - 30, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id");
            long memberId = insert(c, "INSERT INTO members (employer_id, full_name, benefit_policy_id, "
                    + "card_number, barcode, status, active) VALUES (" + employerId + ", 'Ledger Member', "
                    + policyId + ", 'LC" + s + "', 'LC" + s + "', 'ACTIVE', true) RETURNING id");
            long groupId = insert(c, "INSERT INTO benefit_groups (policy_id, code, name_ar, "
                    + "aggregation_mode) VALUES (" + policyId + ", 'G-" + s + "', 'مجموعة', 'INDIVIDUAL') RETURNING id");
            long bucketId = insert(c, "INSERT INTO benefit_limit_buckets (policy_id, benefit_group_id, code, "
                    + "name_ar, amount_limit, period_type, counting_method, consumption_basis, active) VALUES ("
                    + policyId + ", " + groupId + ", 'B-" + s
                    + "', 'وعاء', 5000, 'ANNUAL', 'EACH_LINE', 'ELIGIBLE_AMOUNT', true) RETURNING id");
            long preauthId = insert(c, "INSERT INTO pre_authorizations (member_id, policy_id, status, "
                    + "request_date, created_at, updated_at) VALUES (" + memberId + ", " + policyId
                    + ", 'APPROVED', now(), now(), now()) RETURNING id");
            long preauthLineId = insert(c, "INSERT INTO pre_authorization_lines (pre_authorization_id, "
                    + "requested_amount) VALUES (" + preauthId + ", 100.00) RETURNING id");
            long providerId = insert(c, "INSERT INTO providers (name, license_number, provider_type) "
                    + "VALUES ('Provider " + s + "', 'LIC-" + s + "', 'CLINIC') RETURNING id");
            long visitId = insert(c, "INSERT INTO visits (member_id, provider_id, visit_date) VALUES ("
                    + memberId + ", " + providerId + ", CURRENT_DATE - 5) RETURNING id");
            long claimId = insert(c, "INSERT INTO claims (claim_number, member_id, provider_id, visit_id, "
                    + "service_date, requested_amount, status) VALUES ('CLM-" + s + "', " + memberId
                    + ", " + providerId + ", " + visitId + ", CURRENT_DATE - 5, 100.00, 'APPROVED') RETURNING id");
            long claimLineId = insert(c, "INSERT INTO claim_lines (claim_id, service_code, quantity, "
                    + "unit_price, total_price) VALUES (" + claimId + ", 'SVC-" + s
                    + "', 1, 100.00, 100.00) RETURNING id");
            return new Fixture(memberId, policyId, bucketId, preauthId, preauthLineId, claimId, claimLineId);
        }
    }

    private long insert(Connection c, String sql) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Builds an INSERT with every column the ledger needs, so each test varies only what it is testing. */
    private String insertSql(Fixture f, String sourceType, String scope, String status,
            Long bucketId, Long claimId, Long claimLineId, Long preauthId, Long preauthLineId,
            String amount, Long reversalOf, String reversalReason, String key) {
        return "INSERT INTO benefit_bucket_consumptions "
                + "(source_type, limit_scope, status, policy_id, member_id, bucket_id, claim_id, claim_line_id, "
                + " preauth_id, preauth_line_id, period_start, period_end, approved_amount, times_consumed, "
                + " calculation_version, idempotency_key, reversal_of_id, reversal_reason, created_at) VALUES ("
                + "'" + sourceType + "', '" + scope + "', '" + status + "', " + f.policyId() + ", " + f.memberId()
                + ", " + (bucketId == null ? "NULL" : bucketId)
                + ", " + (claimId == null ? "NULL" : claimId)
                + ", " + (claimLineId == null ? "NULL" : claimLineId)
                + ", " + (preauthId == null ? "NULL" : preauthId)
                + ", " + (preauthLineId == null ? "NULL" : preauthLineId)
                + ", DATE '" + PERIOD_START + "', DATE '" + PERIOD_END + "', " + amount + ", 0, 1, '" + key + "', "
                + (reversalOf == null ? "NULL" : reversalOf) + ", "
                + (reversalReason == null ? "NULL" : "'" + reversalReason + "'") + ", now())";
    }

    private void exec(String sql) throws Exception {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    private long execReturningId(String sql) throws Exception {
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql + " RETURNING id");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    // 1. Legacy rows carry the backfilled classification.
    @Test
    void existingRowsWereClassifiedAsClaimBucketByTheMigration() {
        Long misclassified = jdbc.queryForObject(
                "SELECT COUNT(*) FROM benefit_bucket_consumptions "
                        + "WHERE source_type IS NULL OR limit_scope IS NULL", Long.class);
        assertThat(misclassified).isZero();
    }

    // 2. A pre-authorization hold against a bucket.
    @Test
    void aPreauthBucketReservationIsAccepted() throws Exception {
        Fixture f = fixture();
        exec(insertSql(f, "PREAUTH", "BUCKET", "RESERVED", f.bucketId(), null, null,
                f.preauthId(), f.preauthLineId(), "100.00", null, null, "K-" + suffix()));
    }

    // 3. A pre-authorization hold against the general ceiling, with no bucket.
    @Test
    void aPreauthGeneralCeilingReservationWithNoBucketIsAccepted() throws Exception {
        Fixture f = fixture();
        exec(insertSql(f, "PREAUTH", "POLICY_GENERAL", "RESERVED", null, null, null,
                f.preauthId(), f.preauthLineId(), "100.00", null, null, "K-" + suffix()));
    }

    // 4. A general-ceiling row must not carry a bucket.
    @Test
    void aGeneralScopeRowCarryingABucketIsRejected() throws Exception {
        Fixture f = fixture();
        assertThatThrownBy(() -> exec(insertSql(f, "PREAUTH", "POLICY_GENERAL", "RESERVED", f.bucketId(), null, null,
                f.preauthId(), f.preauthLineId(), "100.00", null, null, "K-" + suffix())))
                .hasMessageContaining("chk_bucket_consumption_scope_bucket");
    }

    // 5. A bucket row must name its bucket.
    @Test
    void aBucketScopeRowWithoutABucketIsRejected() throws Exception {
        Fixture f = fixture();
        assertThatThrownBy(() -> exec(insertSql(f, "PREAUTH", "BUCKET", "RESERVED", null, null, null,
                f.preauthId(), f.preauthLineId(), "100.00", null, null, "K-" + suffix())))
                .hasMessageContaining("chk_bucket_consumption_scope_bucket");
    }

    // 6. One source only.
    @Test
    void aRowCarryingBothAClaimAndAPreauthIsRejected() throws Exception {
        Fixture f = fixture();
        assertThatThrownBy(() -> exec(insertSql(f, "CLAIM", "BUCKET", "COMMITTED", f.bucketId(),
                f.claimId(), f.claimLineId(), f.preauthId(), f.preauthLineId(), "100.00", null, null,
                "K-" + suffix())))
                .hasMessageContaining("chk_bucket_consumption_source_shape");
    }

    // 7. A head without its line is unattributable.
    @Test
    void aPreauthHeadWithoutItsLineIsRejected() throws Exception {
        Fixture f = fixture();
        assertThatThrownBy(() -> exec(insertSql(f, "PREAUTH", "BUCKET", "RESERVED", f.bucketId(), null, null,
                f.preauthId(), null, "100.00", null, null, "K-" + suffix())))
                .hasMessageContaining("chk_bucket_consumption_source_shape");
    }

    // 7b. A line belonging to a DIFFERENT head passes both foreign keys and is
    //     still meaningless -- caught by the ownership trigger.
    @Test
    void aLineBelongingToAnotherPreauthIsRejected() throws Exception {
        Fixture f = fixture();
        Fixture other = fixture();
        assertThatThrownBy(() -> exec(insertSql(f, "PREAUTH", "BUCKET", "RESERVED", f.bucketId(), null, null,
                f.preauthId(), other.preauthLineId(), "100.00", null, null, "K-" + suffix())))
                .hasMessageContaining("belongs to pre-authorization");
    }

    // 8. A compensating movement must name what it compensates.
    @Test
    void aReversedRowWithoutAnOriginalIsRejected() throws Exception {
        Fixture f = fixture();
        assertThatThrownBy(() -> exec(insertSql(f, "PREAUTH", "BUCKET", "REVERSED", f.bucketId(), null, null,
                f.preauthId(), f.preauthLineId(), "100.00", null, "PREAUTH_RELEASE", "K-" + suffix())))
                .hasMessageContaining("must name the movement it compensates");
    }

    // 9. Reversing a reversal would make "net" recursive.
    @Test
    void reversingACompensatingMovementIsRejected() throws Exception {
        Fixture f = fixture();
        long original = execReturningId(insertSql(f, "PREAUTH", "BUCKET", "RESERVED", f.bucketId(), null, null,
                f.preauthId(), f.preauthLineId(), "100.00", null, null, "K-" + suffix()));
        long release = execReturningId(insertSql(f, "PREAUTH", "BUCKET", "REVERSED", f.bucketId(), null, null,
                f.preauthId(), f.preauthLineId(), "100.00", original, "PREAUTH_RELEASE", "K-" + suffix()));

        assertThatThrownBy(() -> exec(insertSql(f, "PREAUTH", "BUCKET", "REVERSED", f.bucketId(), null, null,
                f.preauthId(), f.preauthLineId(), "10.00", release, "PREAUTH_RELEASE", "K-" + suffix())))
                .hasMessageContaining("cannot itself be reversed");
    }

    // 10. Compensations may never exceed the original.
    @Test
    void compensationsBeyondTheOriginalAmountAreRejected() throws Exception {
        Fixture f = fixture();
        long original = execReturningId(insertSql(f, "PREAUTH", "BUCKET", "RESERVED", f.bucketId(), null, null,
                f.preauthId(), f.preauthLineId(), "100.00", null, null, "K-" + suffix()));
        // Partial release is legitimate...
        exec(insertSql(f, "PREAUTH", "BUCKET", "REVERSED", f.bucketId(), null, null,
                f.preauthId(), f.preauthLineId(), "60.00", original, "PREAUTH_RELEASE", "K-" + suffix()));
        // ...but the total may not exceed what was held.
        assertThatThrownBy(() -> exec(insertSql(f, "PREAUTH", "BUCKET", "REVERSED", f.bucketId(), null, null,
                f.preauthId(), f.preauthLineId(), "50.00", original, "PREAUTH_RELEASE", "K-" + suffix())))
                .hasMessageContaining("would exceed the original amount");
    }

    // 11. The general ceiling's reservations are read from their OWN rows.
    //     Exercised through the PRODUCTION query, not a copy of it -- a
    //     restatement of the SQL here would pass while the code that actually
    //     runs reads something else.
    @Test
    void generalScopeReservedIsReadFromItsOwnRowsAndNettedByReleases() throws Exception {
        Fixture f = fixture();
        long held = execReturningId(insertSql(f, "PREAUTH", "POLICY_GENERAL", "RESERVED", null, null, null,
                f.preauthId(), f.preauthLineId(), "300.00", null, null, "K-" + suffix()));
        // A bucket reservation for the same member must NOT leak into the
        // general figure -- deriving one from the other is what would double
        // count a line mapped to several buckets.
        exec(insertSql(f, "PREAUTH", "BUCKET", "RESERVED", f.bucketId(), null, null,
                f.preauthId(), f.preauthLineId(), "999.00", null, null, "K-" + suffix()));

        assertThat(consumptionRepository.sumGeneralScopeReserved(
                f.memberId(), f.policyId(), PERIOD_START, PERIOD_END))
                .isEqualByComparingTo("300.00");

        // Releasing part of it nets down, without editing the original.
        exec(insertSql(f, "PREAUTH", "POLICY_GENERAL", "REVERSED", null, null, null,
                f.preauthId(), f.preauthLineId(), "120.00", held, "PREAUTH_RELEASE", "K-" + suffix()));

        assertThat(consumptionRepository.sumGeneralScopeReserved(
                f.memberId(), f.policyId(), PERIOD_START, PERIOD_END))
                .isEqualByComparingTo("180.00");
    }

    // 11b. A hold belongs to ONE period. Reading an open-ended period must not
    //      sweep up bounded ones: that would charge a member for money held
    //      against a different year and silently shrink what they may spend.
    @Test
    void aHoldInOnePeriodDoesNotCountAgainstAnother() throws Exception {
        Fixture f = fixture();
        exec(insertSql(f, "PREAUTH", "POLICY_GENERAL", "RESERVED", null, null, null,
                f.preauthId(), f.preauthLineId(), "300.00", null, null, "K-" + suffix()));

        // A different period sees nothing.
        assertThat(consumptionRepository.sumGeneralScopeReserved(
                f.memberId(), f.policyId(), PERIOD_START.minusYears(1), PERIOD_END.minusYears(1)))
                .isEqualByComparingTo("0");

        // An open-ended read matches open-ended rows only, not this bounded one.
        assertThat(consumptionRepository.sumGeneralScopeReserved(
                f.memberId(), f.policyId(), PERIOD_START, null))
                .isEqualByComparingTo("0");
    }

    // 13. The source/entry-type matrix, enforced by the database rather than
    //     by whoever writes the next service. PREAUTH+COMMITTED is the one
    //     that matters most: the claim-scoped reads inner-join through
    //     claim_id, so such a row would be silently invisible to them.
    @Test
    void aPreauthMayNotPostACommittedMovement() throws Exception {
        Fixture f = fixture();
        assertThatThrownBy(() -> exec(insertSql(f, "PREAUTH", "BUCKET", "COMMITTED", f.bucketId(), null, null,
                f.preauthId(), f.preauthLineId(), "100.00", null, null, "K-" + suffix())))
                .hasMessageContaining("chk_bucket_consumption_entry_type_by_source");
    }

    @Test
    void aClaimMayNotPostAReservation() throws Exception {
        Fixture f = fixture();
        assertThatThrownBy(() -> exec(insertSql(f, "CLAIM", "BUCKET", "RESERVED", f.bucketId(),
                f.claimId(), f.claimLineId(), null, null, "100.00", null, null, "K-" + suffix())))
                .hasMessageContaining("chk_bucket_consumption_entry_type_by_source");
    }

    @Test
    void onlyAPreauthMayHoldLimit() throws Exception {
        Fixture f = fixture();
        // Neither an opening balance nor a manual adjustment is a hold: both
        // describe money already spent, not money set aside.
        for (String source : new String[] {"OPENING_IMPORT", "ADJUSTMENT"}) {
            assertThatThrownBy(() -> exec(insertSql(f, source, "BUCKET", "RESERVED", f.bucketId(), null, null,
                    null, null, "100.00", null, null, "K-" + suffix())))
                    .as(source + " must not be able to reserve")
                    .hasMessageContaining("chk_bucket_consumption_entry_type_by_source");
        }
    }

    @Test
    void anOpeningBalanceAndAnAdjustmentMayPostCommittedConsumption() throws Exception {
        Fixture f = fixture();
        for (String source : new String[] {"OPENING_IMPORT", "ADJUSTMENT"}) {
            exec(insertSql(f, source, "BUCKET", "COMMITTED", f.bucketId(), null, null,
                    null, null, "100.00", null, null, "K-" + suffix()));
        }
    }

    // 12. A compensating movement must match its original's scope and source.
    @Test
    void aCompensatingMovementMustMatchTheOriginalsScopeAndSource() throws Exception {
        Fixture f = fixture();
        long original = execReturningId(insertSql(f, "PREAUTH", "POLICY_GENERAL", "RESERVED", null, null, null,
                f.preauthId(), f.preauthLineId(), "100.00", null, null, "K-" + suffix()));

        assertThatThrownBy(() -> exec(insertSql(f, "PREAUTH", "BUCKET", "REVERSED", f.bucketId(), null, null,
                f.preauthId(), f.preauthLineId(), "100.00", original, "PREAUTH_RELEASE", "K-" + suffix())))
                .hasMessageContaining("same member, policy, scope, source and period");
    }
}
