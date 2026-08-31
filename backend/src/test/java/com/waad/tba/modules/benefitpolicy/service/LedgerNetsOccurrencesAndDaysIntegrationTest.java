package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
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
 * The ledger nets money correctly and used to net nothing else.
 *
 * A compensating row releases an original; the original itself is never
 * edited. Every amount query applied that netting. Every OCCURRENCE and DAY
 * query ignored it, so the same reversed claim gave the member their money
 * back while keeping their visits and their days consumed -- permanently.
 *
 * The day queries were worse than one-directional. Callers skip the whole day
 * check when a day "already exists", so a reversed day both blocked a
 * legitimate new day AND let a claim onto the reversed day itself, past the
 * ceiling.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class LedgerNetsOccurrencesAndDaysIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired private BenefitBucketConsumptionRepository consumptionRepository;
    @Autowired private JdbcTemplate jdbc;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private record World(long memberId, long policyId, long bucketId, long providerId) {}

    private LocalDate periodStart() {
        return LocalDate.now().withDayOfYear(1);
    }

    private LocalDate periodEnd() {
        return periodStart().plusYears(1).minusDays(1);
    }

    private World world(int timesLimit) {
        String s = suffix();
        Long employerId = jdbc.queryForObject("INSERT INTO employers (code, name) VALUES ('NT-" + s
                + "', 'Net Co " + s + "') RETURNING id", Long.class);
        Long policyId = jdbc.queryForObject("INSERT INTO benefit_policies (name, policy_code, employer_id, "
                + "annual_limit, default_coverage_percent, start_date, end_date, status, active) VALUES "
                + "('NTP-" + s + "', 'NTPOL-" + s + "', " + employerId
                + ", 1000000, 100, CURRENT_DATE - 60, CURRENT_DATE + 365, 'ACTIVE', true) RETURNING id",
                Long.class);
        Long memberId = jdbc.queryForObject("INSERT INTO members (employer_id, full_name, "
                + "benefit_policy_id, card_number, barcode, status, active) VALUES (" + employerId
                + ", 'Net Member', " + policyId + ", 'NT" + s + "', 'NT" + s
                + "', 'ACTIVE', true) RETURNING id", Long.class);
        jdbc.update("INSERT INTO member_policy_assignments (member_id, policy_id, "
                + "assignment_start_date, assignment_source) VALUES (?, ?, CURRENT_DATE - 60, 'MANUAL')",
                memberId, policyId);
        Long groupId = jdbc.queryForObject("INSERT INTO benefit_groups (policy_id, code, name_ar, "
                + "aggregation_mode) VALUES (" + policyId + ", 'NTG-" + s
                + "', 'مجموعة', 'INDIVIDUAL') RETURNING id", Long.class);
        Long bucketId = jdbc.queryForObject("INSERT INTO benefit_limit_buckets (policy_id, "
                + "benefit_group_id, code, name_ar, amount_limit, times_limit, days_limit, period_type, "
                + "counting_method, consumption_basis, active) VALUES (" + policyId + ", " + groupId
                + ", 'NTB-" + s + "', 'وعاء', 1000000, " + timesLimit
                + ", 5, 'ANNUAL', 'EACH_LINE', 'COMPANY_SHARE', true) RETURNING id", Long.class);
        Long providerId = jdbc.queryForObject("INSERT INTO providers (name, license_number, provider_type) "
                + "VALUES ('Prov " + s + "', 'NTLIC-" + s + "', 'CLINIC') RETURNING id", Long.class);
        return new World(memberId, policyId, bucketId, providerId);
    }

    /** A committed movement tied to a real claim on the given service date. */
    private long commit(World w, LocalDate serviceDate, String amount, int times) {
        String s = suffix();
        Long visitId = jdbc.queryForObject("INSERT INTO visits (member_id, provider_id, visit_date) VALUES ("
                + w.memberId() + ", " + w.providerId() + ", DATE '" + serviceDate + "') RETURNING id",
                Long.class);
        Long claimId = jdbc.queryForObject("INSERT INTO claims (claim_number, member_id, provider_id, "
                + "visit_id, service_date, requested_amount, status, claim_context_code) VALUES ('NTC-" + s + "', "
                + w.memberId() + ", " + w.providerId() + ", " + visitId + ", DATE '" + serviceDate
                + "', " + amount + ", 'APPROVED', 'OUTPATIENT') RETURNING id", Long.class);
        Long lineId = jdbc.queryForObject("INSERT INTO claim_lines (claim_id, service_code, quantity, "
                + "unit_price, total_price) VALUES (" + claimId + ", 'NTS-" + s + "', 1, " + amount
                + ", " + amount + ") RETURNING id", Long.class);

        return jdbc.queryForObject("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, "
                + "bucket_id, claim_id, claim_line_id, period_start, period_end, approved_amount, "
                + "times_consumed, calculation_version, idempotency_key, status, source_type, limit_scope, "
                + "created_at) VALUES (" + w.policyId() + ", " + w.memberId() + ", " + w.bucketId() + ", "
                + claimId + ", " + lineId + ", DATE '" + periodStart() + "', DATE '" + periodEnd() + "', "
                + amount + ", " + times + ", 1, 'NTK-" + s
                + "', 'COMMITTED', 'CLAIM', 'BUCKET', now()) RETURNING id", Long.class);
    }

    /** A compensating movement releasing part or all of an original. */
    private void release(long originalId, String amount, int times) {
        String s = suffix();
        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, bucket_id, claim_id, "
                + "claim_line_id, period_start, period_end, approved_amount, times_consumed, "
                + "calculation_version, idempotency_key, status, source_type, limit_scope, reversal_of_id, "
                + "reversal_reason, created_at) SELECT policy_id, member_id, bucket_id, claim_id, "
                + "claim_line_id, period_start, period_end, " + amount + ", " + times
                + ", 1, 'NTR-" + s + "', 'REVERSED', source_type, limit_scope, id, 'CLAIM_REVERSAL', now() "
                + "FROM benefit_bucket_consumptions WHERE id = ?", originalId);
    }

    /**
     * A pre-authorization release. Separate from the claim helper because the
     * database enforces the source shape: a PREAUTH movement carries its
     * approval, never a claim, and its reason must come from the PREAUTH
     * family.
     */
    private void releaseHold(long originalId, String amount, int times) {
        String s = suffix();
        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, bucket_id, preauth_id, "
                + "preauth_line_id, period_start, period_end, approved_amount, times_consumed, "
                + "calculation_version, idempotency_key, status, source_type, limit_scope, reversal_of_id, "
                // Carried over from the original, as the production writer
                // does: a release filed under a different enrolment period
                // would hand the money back to a period that never held it.
                + "reversal_reason, member_policy_assignment_id, created_at) "
                + "SELECT policy_id, member_id, bucket_id, preauth_id, "
                + "preauth_line_id, period_start, period_end, " + amount + ", " + times
                + ", 1, 'NTPR-" + s + "', 'REVERSED', source_type, limit_scope, id, 'PREAUTH_RELEASE', "
                + "member_policy_assignment_id, now() FROM benefit_bucket_consumptions WHERE id = ?",
                originalId);
    }

    // ── occurrences ─────────────────────────────────────────────────────

    @Test
    void aReversedClaimGivesBackItsVisitsAndNotOnlyItsMoney() {
        World w = world(2);
        long original = commit(w, LocalDate.now(), "500.00", 2);
        release(original, "500.00", 2);

        // The asymmetry was the tell: the SAME reversed claim returned its
        // money while keeping its visits, so the bucket reported full amount
        // available and zero visits available at the same time.
        assertThat(consumptionRepository.sumCommittedAmount(w.memberId(), w.bucketId(),
                periodStart(), periodEnd(), null)).isEqualByComparingTo("0.00");
        assertThat(consumptionRepository.sumCommittedTimes(w.memberId(), w.bucketId(),
                periodStart(), periodEnd(), null))
                .as("the visits come back too").isZero();
    }

    @Test
    void aPartiallyReleasedHoldStillCountsWhatIsOutstanding() {
        World w = world(5);
        String s = suffix();
        Long preauthId = jdbc.queryForObject("INSERT INTO pre_authorizations (member_id, policy_id, "
                + "status, request_date, created_at, updated_at) VALUES (" + w.memberId() + ", "
                + w.policyId() + ", 'APPROVED', now(), now(), now()) RETURNING id", Long.class);
        Long lineId = jdbc.queryForObject("INSERT INTO pre_authorization_lines (pre_authorization_id, "
                + "requested_amount) VALUES (" + preauthId + ", 300.00) RETURNING id", Long.class);
        Long holdId = jdbc.queryForObject("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, "
                + "bucket_id, preauth_id, preauth_line_id, period_start, period_end, approved_amount, "
                + "times_consumed, calculation_version, idempotency_key, status, source_type, limit_scope, "
                + "member_policy_assignment_id, created_at) VALUES (" + w.policyId() + ", " + w.memberId()
                + ", " + w.bucketId() + ", "
                + preauthId + ", " + lineId + ", DATE '" + periodStart() + "', DATE '" + periodEnd()
                + "', 300.00, 3, 1, 'NTH-" + s + "', 'RESERVED', 'PREAUTH', 'BUCKET', "
                + "(SELECT id FROM member_policy_assignments WHERE member_id = " + w.memberId()
                + " ORDER BY id LIMIT 1), now()) RETURNING id",
                Long.class);

        releaseHold(holdId, "100.00", 1);

        // 3 held, 1 given back. Treating "a release exists" as "fully
        // released" reported 0 held, so a second approval could hold 5 more
        // against a limit of 5 -- 7 visits promised in total.
        assertThat(consumptionRepository.sumReservedTimes(w.memberId(), w.bucketId(),
                periodStart(), periodEnd()))
                .as("two occurrences are still held").isEqualTo(2);
    }

    @Test
    void theDatabaseRefusesToGiveBackMoreVisitsThanWereTaken() {
        World w = world(5);
        long original = commit(w, LocalDate.now(), "500.00", 2);

        // The amount had this ceiling since V174; occurrences did not, and the
        // application code was found releasing gross rather than outstanding.
        assertThatThrownBy(() -> release(original, "0.00", 3))
                .hasMessageContaining("would exceed the original count");
    }

    // ── days ────────────────────────────────────────────────────────────

    @Test
    void aFullyReversedDayIsFreeAgain() {
        World w = world(50);
        LocalDate day = LocalDate.now().minusDays(3);
        long original = commit(w, day, "500.00", 1);
        release(original, "500.00", 1);

        assertThat(consumptionRepository.countCommittedServiceDays(w.memberId(), w.bucketId(),
                periodStart(), periodEnd(), null))
                .as("a day whose only claim was reversed is not a used day").isZero();
        assertThat(consumptionRepository.existsCommittedForServiceDay(w.memberId(), w.bucketId(),
                day, null))
                .as("and callers must not skip the day check for it").isFalse();
    }

    @Test
    void aDayWithAnotherLiveClaimStaysUsedAfterOneIsReversed() {
        World w = world(50);
        LocalDate day = LocalDate.now().minusDays(2);
        long first = commit(w, day, "300.00", 1);
        commit(w, day, "200.00", 1);
        release(first, "300.00", 1);

        // Netting must not overshoot in the other direction: the day is still
        // consumed because the second claim is untouched.
        assertThat(consumptionRepository.countCommittedServiceDays(w.memberId(), w.bucketId(),
                periodStart(), periodEnd(), null)).isEqualTo(1L);
        assertThat(consumptionRepository.existsCommittedForServiceDay(w.memberId(), w.bucketId(),
                day, null)).isTrue();
    }

    @Test
    void aPartiallyReversedDayIsStillUsed() {
        World w = world(50);
        LocalDate day = LocalDate.now().minusDays(1);
        long original = commit(w, day, "500.00", 1);
        release(original, "200.00", 0);

        // 300 of the day's consumption is still outstanding, so the day is
        // used. Only a day with nothing left outstanding is free.
        assertThat(consumptionRepository.countCommittedServiceDays(w.memberId(), w.bucketId(),
                periodStart(), periodEnd(), null)).isEqualTo(1L);
    }

    @Test
    void distinctLiveDaysAreCountedOnce() {
        World w = world(50);
        commit(w, LocalDate.now().minusDays(5), "100.00", 1);
        commit(w, LocalDate.now().minusDays(5), "100.00", 1);
        commit(w, LocalDate.now().minusDays(6), "100.00", 1);

        // Two service dates, three movements.
        assertThat(consumptionRepository.countCommittedServiceDays(w.memberId(), w.bucketId(),
                periodStart(), periodEnd(), null)).isEqualTo(2L);
    }

    // ── the day rule across BOTH dimensions and the outer filters ───────

    @Test
    void aDayWhoseMoneyCameBackButKeepsItsVisitsIsStillUsed() {
        World w = world(50);
        LocalDate day = LocalDate.now().minusDays(7);
        long original = commit(w, day, "500.00", 2);
        // Money fully returned; the visits were not.
        release(original, "500.00", 0);

        // Judging the day on money alone would free a day the member still
        // occupies. The two dimensions are released independently, so the day
        // survives while EITHER is outstanding.
        assertThat(consumptionRepository.countCommittedServiceDays(w.memberId(), w.bucketId(),
                periodStart(), periodEnd(), null))
                .as("visits still stand on this day").isEqualTo(1L);
        assertThat(consumptionRepository.existsCommittedForServiceDay(w.memberId(), w.bucketId(),
                day, null)).isTrue();
    }

    @Test
    void aDayWhoseVisitsCameBackButKeepsItsMoneyIsStillUsed() {
        World w = world(50);
        LocalDate day = LocalDate.now().minusDays(8);
        long original = commit(w, day, "500.00", 2);
        // The mirror case: visits returned, money not.
        release(original, "0.00", 2);

        assertThat(consumptionRepository.countCommittedServiceDays(w.memberId(), w.bucketId(),
                periodStart(), periodEnd(), null))
                .as("money still stands on this day").isEqualTo(1L);
    }

    @Test
    void aDayIsFreeOnlyWhenBothDimensionsAreFullyReturned() {
        World w = world(50);
        LocalDate day = LocalDate.now().minusDays(9);
        long original = commit(w, day, "500.00", 2);
        release(original, "500.00", 2);

        assertThat(consumptionRepository.countCommittedServiceDays(w.memberId(), w.bucketId(),
                periodStart(), periodEnd(), null)).isZero();
        assertThat(consumptionRepository.existsCommittedForServiceDay(w.memberId(), w.bucketId(),
                day, null)).isFalse();
    }

    @Test
    void theExcludedClaimDoesNotHoldItsOwnDayOpen() {
        World w = world(50);
        LocalDate day = LocalDate.now().minusDays(10);
        long original = commit(w, day, "500.00", 1);
        Long claimId = jdbc.queryForObject(
                "SELECT claim_id FROM benefit_bucket_consumptions WHERE id = ?", Long.class, original);

        // Re-adjudicating a claim must not let it count against itself. The
        // outer query excluded it; the inner one did not, so the day stayed
        // open on the strength of the very claim being recalculated.
        assertThat(consumptionRepository.countCommittedServiceDays(w.memberId(), w.bucketId(),
                periodStart(), periodEnd(), claimId))
                .as("its own day is not consumed from its own point of view").isZero();
        assertThat(consumptionRepository.existsCommittedForServiceDay(w.memberId(), w.bucketId(),
                day, claimId)).isFalse();
    }

    @Test
    void aDayInAnotherPeriodDoesNotHoldThisPeriodsDayOpen() {
        World w = world(50);
        LocalDate day = LocalDate.now().minusDays(11);
        String s = suffix();

        // Same member, same bucket, same service date -- but a different
        // period. Without the period bound on the inner query this row would
        // keep the day alive in a year it does not belong to.
        Long visitId = jdbc.queryForObject("INSERT INTO visits (member_id, provider_id, visit_date) VALUES ("
                + w.memberId() + ", " + w.providerId() + ", DATE '" + day + "') RETURNING id", Long.class);
        Long claimId = jdbc.queryForObject("INSERT INTO claims (claim_number, member_id, provider_id, "
                + "visit_id, service_date, requested_amount, status, claim_context_code) VALUES ('NTX-" + s + "', "
                + w.memberId() + ", " + w.providerId() + ", " + visitId + ", DATE '" + day
                + "', 100.00, 'APPROVED', 'OUTPATIENT') RETURNING id", Long.class);
        Long lineId = jdbc.queryForObject("INSERT INTO claim_lines (claim_id, service_code, quantity, "
                + "unit_price, total_price) VALUES (" + claimId + ", 'NTXS-" + s
                + "', 1, 100.00, 100.00) RETURNING id", Long.class);
        jdbc.update("INSERT INTO benefit_bucket_consumptions (policy_id, member_id, bucket_id, claim_id, "
                + "claim_line_id, period_start, period_end, approved_amount, times_consumed, "
                + "calculation_version, idempotency_key, status, source_type, limit_scope, created_at) "
                + "VALUES (" + w.policyId() + ", " + w.memberId() + ", " + w.bucketId() + ", " + claimId
                + ", " + lineId + ", DATE '" + periodStart().minusYears(1) + "', DATE '"
                + periodEnd().minusYears(1) + "', 100.00, 1, 1, 'NTXK-" + s
                + "', 'COMMITTED', 'CLAIM', 'BUCKET', now())");

        // Nothing was committed in THIS period.
        assertThat(consumptionRepository.countCommittedServiceDays(w.memberId(), w.bucketId(),
                periodStart(), periodEnd(), null)).isZero();
    }
}
