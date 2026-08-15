package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * A hold protects limit FROM other decisions, but it was placed FOR this one.
 *
 * The ordinary balance read subtracts every reservation without asking whose
 * it is. A claim converting its own approval would therefore find the money
 * it was promised already spoken for -- by itself -- and could be refused for
 * insufficient balance while the reservation sitting in its way is its own.
 *
 *     availableForClaim = reservableAvailable + ownActiveReservation
 *     availableForClaim <= actualRemaining
 *
 * The ceiling is the half that is easy to lose: the own reservation is added
 * BACK, never stacked on top of what the member actually has left.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class OwnActiveReservationReadIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private LimitBalanceReader balanceReader;
    @Autowired private EffectiveLimitResolver effectiveLimitResolver;
    @Autowired private JdbcTemplate jdbc;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record World(long memberId, long policyId, long bucketId, long ruleId, long preauthId) {}

    private World world(String amountLimit, String alreadyCommitted) {
        String s = suffix();
        Long employerId = jdbc.queryForObject("INSERT INTO employers (code, name) VALUES ('OR-" + s
                + "', 'Own Co " + s + "') RETURNING id", Long.class);
        Long policyId = jdbc.queryForObject("INSERT INTO benefit_policies (name, policy_code, employer_id, "
                + "annual_limit, default_coverage_percent, start_date, end_date, status, active) VALUES "
                + "('ORP-" + s + "', 'ORPOL-" + s + "', " + employerId
                + ", 1000000, 80, CURRENT_DATE - 60, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id",
                Long.class);
        Long memberId = jdbc.queryForObject("INSERT INTO members (employer_id, full_name, "
                + "benefit_policy_id, card_number, barcode, status, active) VALUES (" + employerId
                + ", 'Own Member', " + policyId + ", 'OR" + s + "', 'OR" + s
                + "', 'ACTIVE', true) RETURNING id", Long.class);
        jdbc.update("INSERT INTO member_policy_assignments (member_id, policy_id, assignment_start_date, "
                + "assignment_source) VALUES (?, ?, CURRENT_DATE - 60, 'MANUAL')", memberId, policyId);
        jdbc.update("INSERT INTO member_employer_assignments (member_id, employer_id, assignment_start_date, "
                + "assignment_reason, assignment_source) VALUES (?, ?, CURRENT_DATE - 60, "
                + "'test enrollment', 'MANUAL')", memberId, employerId);

        Long categoryId = jdbc.queryForObject("INSERT INTO medical_categories (code, name, active) "
                + "VALUES ('ORCAT-" + s + "', 'Own Category', true) RETURNING id", Long.class);
        Long ruleId = jdbc.queryForObject("INSERT INTO benefit_policy_rules (benefit_policy_id, "
                + "medical_category_id, encounter_type, coverage_percent, active, deleted) VALUES ("
                + policyId + ", " + categoryId + ", 'OUTPATIENT', 80, true, false) RETURNING id", Long.class);
        Long groupId = jdbc.queryForObject("INSERT INTO benefit_groups (policy_id, code, name_ar, "
                + "context_type, aggregation_mode) VALUES (" + policyId + ", 'ORG-" + s
                + "', 'مجموعة', 'OUTPATIENT', 'INDIVIDUAL') RETURNING id", Long.class);
        Long bucketId = jdbc.queryForObject("INSERT INTO benefit_limit_buckets (policy_id, "
                + "benefit_group_id, code, name_ar, amount_limit, period_type, counting_method, "
                + "consumption_basis, benefit_scope_type, context_type, active) VALUES (" + policyId
                + ", " + groupId + ", 'ORB-" + s + "', 'وعاء', " + amountLimit
                + ", 'ANNUAL', 'EACH_LINE', 'COMPANY_SHARE', 'CATEGORY', 'OUTPATIENT', true) RETURNING id",
                Long.class);
        jdbc.update("INSERT INTO benefit_rule_buckets (rule_id, bucket_id) VALUES (?, ?)", ruleId, bucketId);

        Long preauthId = jdbc.queryForObject("INSERT INTO pre_authorizations (member_id, policy_id, "
                + "status, request_date, created_at, updated_at) VALUES (" + memberId + ", " + policyId
                + ", 'APPROVED', now(), now(), now()) RETURNING id", Long.class);

        if (alreadyCommitted != null) {
            jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, bucket_id, "
                    + "period_start, period_end, approved_amount, times_consumed, calculation_version, "
                    + "idempotency_key, status, source_type, limit_scope, created_at) VALUES (?, ?, ?, "
                    + "DATE_TRUNC('year', CURRENT_DATE)::date, "
                    + "(DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date, "
                    + alreadyCommitted + ", 0, 1, ?, 'COMMITTED', 'OPENING_IMPORT', 'BUCKET', now())",
                    policyId, memberId, bucketId, "ORC-" + s);
        }
        return new World(memberId, policyId, bucketId, ruleId, preauthId);
    }

    /** Places a hold owned by the given pre-authorization. */
    private long hold(World w, long preauthId, String amount) {
        String s = suffix();
        Long lineId = jdbc.queryForObject("INSERT INTO pre_authorization_lines (pre_authorization_id, "
                + "requested_amount) VALUES (" + preauthId + ", " + amount + ") RETURNING id", Long.class);
        return jdbc.queryForObject("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, "
                + "bucket_id, preauth_id, preauth_line_id, period_start, period_end, approved_amount, "
                + "times_consumed, calculation_version, idempotency_key, status, source_type, limit_scope, "
                + "created_at) VALUES (" + w.policyId() + ", " + w.memberId() + ", " + w.bucketId() + ", "
                + preauthId + ", " + lineId + ", DATE_TRUNC('year', CURRENT_DATE)::date, "
                + "(DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date, " + amount
                + ", 0, 1, 'ORH-" + s + "', 'RESERVED', 'PREAUTH', 'BUCKET', now()) RETURNING id",
                Long.class);
    }

    private LimitBalanceReader.LimitBalance bucketBalance(LimitBalanceReader.BalanceSet set, long bucketId) {
        return set.limits().stream()
                .filter(b -> bucketId == (b.limit().definition().bucketId() == null
                        ? -1L : b.limit().definition().bucketId()))
                .findFirst().orElseThrow();
    }

    private LimitBalanceReader.PreauthorizedClaimBalance ownBucket(
            LimitBalanceReader.PreauthorizedClaimBalanceSet set, long bucketId) {
        return set.limits().stream()
                .filter(b -> bucketId == (b.balance().limit().definition().bucketId() == null
                        ? -1L : b.balance().limit().definition().bucketId()))
                .findFirst().orElseThrow();
    }

    private LimitBalanceReader.PreauthorizedClaimBalance ownGeneral(
            LimitBalanceReader.PreauthorizedClaimBalanceSet set) {
        return set.limits().stream()
                .filter(b -> b.balance().limit().definition().bucketId() == null)
                .findFirst().orElseThrow();
    }

    private List<EffectiveLimitResolver.EffectiveLimit> limitsFor(World w) {
        return effectiveLimitResolver.resolve(w.policyId(), w.ruleId(), w.memberId(),
                LocalDate.now(), EncounterType.OUTPATIENT);
    }

    // ── the core semantic ───────────────────────────────────────────────

    @Test
    void aClaimSeesItsOwnHoldAsAvailableAgain() {
        World w = world("1000", null);
        hold(w, w.preauthId(), "600.00");

        var ordinary = balanceReader.read(w.memberId(), limitsFor(w), null);
        var forClaim = balanceReader.readForPreauthorizedClaim(
                w.memberId(), limitsFor(w), null, w.preauthId());

        // The ordinary read protects the hold from everyone.
        assertThat(bucketBalance(ordinary, w.bucketId()).reservableAvailable())
                .isEqualByComparingTo("400.00");

        // The owner gets it back: 400 free plus its own 600.
        var own = ownBucket(forClaim, w.bucketId());
        assertThat(own.ownActiveReservation()).isEqualByComparingTo("600.00");
        assertThat(own.availableForThisClaim()).isEqualByComparingTo("1000.00");

        // And the ordinary figure keeps its ordinary meaning even here: a name
        // that widened to include the owner's hold would lie in every report
        // and audit that reads it.
        assertThat(own.balance().reservableAvailable()).isEqualByComparingTo("400.00");
    }

    @Test
    void theOwnHoldIsAddedBackButNeverAboveWhatTheMemberActuallyHasLeft() {
        // 1000 limit with 300 already consumed: 700 actually remain.
        World w = world("1000", "300.00");
        hold(w, w.preauthId(), "600.00");

        var balance = ownBucket(balanceReader.readForPreauthorizedClaim(
                w.memberId(), limitsFor(w), null, w.preauthId()), w.bucketId());

        // 100 free + 600 own = 700, which is exactly what is left. The own
        // hold is returned, not stacked on top of the member's real balance.
        assertThat(balance.balance().actualRemaining()).isEqualByComparingTo("700.00");
        assertThat(balance.availableForThisClaim())
                .as("never more than the member actually has")
                .isEqualByComparingTo("700.00");
    }

    @Test
    void anotherApprovalsHoldIsNotReturned() {
        World w = world("1000", null);
        Long otherPreauth = jdbc.queryForObject("INSERT INTO pre_authorizations (member_id, policy_id, "
                + "status, request_date, created_at, updated_at) VALUES (" + w.memberId() + ", "
                + w.policyId() + ", 'APPROVED', now(), now(), now()) RETURNING id", Long.class);

        hold(w, w.preauthId(), "300.00");
        hold(w, otherPreauth, "500.00");

        var forClaim = balanceReader.readForPreauthorizedClaim(
                w.memberId(), limitsFor(w), null, w.preauthId());

        // 200 free + its own 300 = 500. The neighbour's 500 stays protected --
        // returning it would let one approval spend another's promised limit.
        assertThat(ownBucket(forClaim, w.bucketId()).availableForThisClaim())
                .isEqualByComparingTo("500.00");
    }

    @Test
    void onlyWhatIsStillOutstandingIsReturned() {
        World w = world("1000", null);
        long holdId = hold(w, w.preauthId(), "600.00");

        // Half of it was already released.
        String s = suffix();
        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, bucket_id, preauth_id, "
                + "preauth_line_id, period_start, period_end, approved_amount, times_consumed, "
                + "calculation_version, idempotency_key, status, source_type, limit_scope, reversal_of_id, "
                + "reversal_reason, created_at) SELECT policy_id, member_id, bucket_id, preauth_id, "
                + "preauth_line_id, period_start, period_end, 300.00, 0, 1, 'ORR-" + s + "', 'REVERSED', "
                + "source_type, limit_scope, id, 'PREAUTH_RELEASE', now() "
                + "FROM benefit_bucket_consumptions WHERE id = ?", holdId);

        var forClaim = balanceReader.readForPreauthorizedClaim(
                w.memberId(), limitsFor(w), null, w.preauthId());

        // 700 free (1000 - the 300 still held) plus the 300 still outstanding.
        // Returning the original 600 would hand back money already given back.
        assertThat(ownBucket(forClaim, w.bucketId()).ownActiveReservation())
                .isEqualByComparingTo("300.00");
        assertThat(ownBucket(forClaim, w.bucketId()).availableForThisClaim())
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void anotherMembersHoldIsNeverReturned() {
        World w = world("1000", null);
        World other = world("1000", null);
        hold(other, other.preauthId(), "600.00");

        var forClaim = balanceReader.readForPreauthorizedClaim(
                w.memberId(), limitsFor(w), null, w.preauthId());

        // Nothing is held against THIS member, so nothing comes back.
        assertThat(ownBucket(forClaim, w.bucketId()).ownActiveReservation())
                .isEqualByComparingTo("0");
        assertThat(ownBucket(forClaim, w.bucketId()).availableForThisClaim())
                .isEqualByComparingTo("1000.00");
    }

    @Test
    void theOrdinaryReadIsUnchangedForEveryOtherCaller() {
        World w = world("1000", null);
        hold(w, w.preauthId(), "600.00");

        var ordinary = balanceReader.read(w.memberId(), limitsFor(w), null);
        var balance = bucketBalance(ordinary, w.bucketId());

        // The whole reason this is a separate entry point: changing read()
        // would alter what "available" means for every path in the system to
        // serve one.
        assertThat(balance.reserved()).isEqualByComparingTo("600.00");
        assertThat(balance.reservableAvailable()).isEqualByComparingTo("400.00");
        assertThat(balance.actualRemaining()).isEqualByComparingTo("1000.00");
    }

    // ── the dimensions ownership must isolate ───────────────────────────

    @Test
    void theGeneralCeilingReturnsItsOwnHoldAndNotTheBucketsOne() {
        World w = world("1000", null);
        // One hold on the bucket, another on the general ceiling. They are
        // separate ceilings measuring the same decision, and neither may be
        // returned in place of the other.
        hold(w, w.preauthId(), "400.00");
        holdGeneral(w, w.preauthId(), "250.00");

        var forClaim = balanceReader.readForPreauthorizedClaim(
                w.memberId(), limitsFor(w), null, w.preauthId());

        assertThat(ownBucket(forClaim, w.bucketId()).ownActiveReservation())
                .isEqualByComparingTo("400.00");
        assertThat(ownGeneral(forClaim).ownActiveReservation())
                .isEqualByComparingTo("250.00");
    }

    @Test
    void aHoldInAnotherPeriodIsNotReturned() {
        World w = world("1000", null);
        holdInPeriod(w, w.preauthId(), "600.00",
                "DATE_TRUNC('year', CURRENT_DATE - INTERVAL '1 year')::date",
                "(DATE_TRUNC('year', CURRENT_DATE) - INTERVAL '1 day')::date");

        var forClaim = balanceReader.readForPreauthorizedClaim(
                w.memberId(), limitsFor(w), null, w.preauthId());

        // Last year's hold belongs to last year's ceiling. Returning it here
        // would hand this year's claim money from a period it cannot spend.
        assertThat(ownBucket(forClaim, w.bucketId()).ownActiveReservation())
                .isEqualByComparingTo("0");
    }

    @Test
    void anOpenEndedHoldDoesNotMatchABoundedPeriod() {
        World w = world("1000", null);
        holdInPeriod(w, w.preauthId(), "600.00",
                "DATE_TRUNC('year', CURRENT_DATE)::date", "NULL");

        var forClaim = balanceReader.readForPreauthorizedClaim(
                w.memberId(), limitsFor(w), null, w.preauthId());

        // A null upper bound is a different period, not "any period".
        assertThat(ownBucket(forClaim, w.bucketId()).ownActiveReservation())
                .isEqualByComparingTo("0");
    }

    @Test
    void anApprovalGetsItsOwnHeldVisitBackToo() {
        World w = world("1000", null);
        jdbc.update("UPDATE benefit_limit_buckets SET times_limit = 1, counting_method = 'PER_VISIT' "
                + "WHERE id = ?", w.bucketId());
        holdTimes(w, w.preauthId(), "100.00", 1);

        var forClaim = balanceReader.readForPreauthorizedClaim(
                w.memberId(), limitsFor(w), null, w.preauthId());
        var own = ownBucket(forClaim, w.bucketId());

        // Without this, an approval that took the last visit would block the
        // very claim it was granted for.
        assertThat(own.balance().reservableTimes()).as("protected from everyone else").isZero();
        assertThat(own.ownReservedTimes()).isEqualTo(1);
        assertThat(own.availableTimesForThisClaim()).isEqualTo(1);
        assertThat(own.availableTimesForThisClaim())
                .as("never above what actually remains")
                .isLessThanOrEqualTo(own.balance().actualRemainingTimes());
    }

    private void holdGeneral(World w, long preauthId, String amount) {
        String s = suffix();
        Long lineId = jdbc.queryForObject("INSERT INTO pre_authorization_lines (pre_authorization_id, "
                + "requested_amount) VALUES (" + preauthId + ", " + amount + ") RETURNING id", Long.class);
        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, preauth_id, "
                + "preauth_line_id, period_start, period_end, approved_amount, times_consumed, "
                + "calculation_version, idempotency_key, status, source_type, limit_scope, created_at) "
                + "VALUES (" + w.policyId() + ", " + w.memberId() + ", " + preauthId + ", " + lineId
                + ", DATE_TRUNC('year', CURRENT_DATE)::date, "
                + "(DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date, " + amount
                + ", 0, 1, 'ORG-" + s + "', 'RESERVED', 'PREAUTH', 'POLICY_GENERAL', now())");
    }

    private void holdInPeriod(World w, long preauthId, String amount, String start, String end) {
        String s = suffix();
        Long lineId = jdbc.queryForObject("INSERT INTO pre_authorization_lines (pre_authorization_id, "
                + "requested_amount) VALUES (" + preauthId + ", " + amount + ") RETURNING id", Long.class);
        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, bucket_id, preauth_id, "
                + "preauth_line_id, period_start, period_end, approved_amount, times_consumed, "
                + "calculation_version, idempotency_key, status, source_type, limit_scope, created_at) "
                + "VALUES (" + w.policyId() + ", " + w.memberId() + ", " + w.bucketId() + ", " + preauthId
                + ", " + lineId + ", " + start + ", " + end + ", " + amount
                + ", 0, 1, 'ORP-" + s + "', 'RESERVED', 'PREAUTH', 'BUCKET', now())");
    }

    private void holdTimes(World w, long preauthId, String amount, int times) {
        String s = suffix();
        Long lineId = jdbc.queryForObject("INSERT INTO pre_authorization_lines (pre_authorization_id, "
                + "requested_amount) VALUES (" + preauthId + ", " + amount + ") RETURNING id", Long.class);
        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, bucket_id, preauth_id, "
                + "preauth_line_id, period_start, period_end, approved_amount, times_consumed, "
                + "calculation_version, idempotency_key, status, source_type, limit_scope, created_at) "
                + "VALUES (" + w.policyId() + ", " + w.memberId() + ", " + w.bucketId() + ", " + preauthId
                + ", " + lineId + ", DATE_TRUNC('year', CURRENT_DATE)::date, "
                + "(DATE_TRUNC('year', CURRENT_DATE) + INTERVAL '1 year - 1 day')::date, " + amount
                + ", " + times + ", 1, 'ORT-" + s + "', 'RESERVED', 'PREAUTH', 'BUCKET', now())");
    }
}
