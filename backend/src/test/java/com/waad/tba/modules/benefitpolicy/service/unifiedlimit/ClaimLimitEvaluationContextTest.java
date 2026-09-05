package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.providercontract.enums.EncounterType;

/**
 * P1.5.1a: proves {@link ClaimLimitEvaluationContext} makes
 * {@link UnifiedLimitResolver} -- still pure, single-line, stateless --
 * behave correctly across MULTIPLE lines of one claim/batch. Every test
 * simulates a real "line 1, then line 2" sequence: resolve line 1, record
 * its consumption, adjust the base snapshots for line 2, resolve line 2.
 *
 * Isolated skeleton: no Spring, no repository, no production call site
 * references this class or {@link ClaimLimitEvaluationContext} yet.
 */
class ClaimLimitEvaluationContextTest {

    private static final Long POLICY_ID = 700L;
    private static final Long MEMBER_ID = 500L;
    private static final LocalDate DATE = LocalDate.of(2026, 3, 1);
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 12, 31);

    private static UnifiedLimitInput lineInput(Long ruleId, int requestedQuantity, int requestedDays,
            CountingMethod method, BigDecimal unitPrice, BigDecimal eligibleAmount) {
        return new UnifiedLimitInput(POLICY_ID, ruleId, MEMBER_ID, DATE, EncounterType.OUTPATIENT,
                requestedQuantity, requestedDays, method, unitPrice, eligibleAmount,
                null, ReservationEvaluationMode.NORMAL, null, null);
    }

    @Test
    @DisplayName("BG1 — two lines, same TIMES bucket, remaining=3: line1 gets 2, line2 gets only 1 (not another 2)")
    void bg1TwoLinesSameTimesBucket() {
        BucketLimitSnapshot base = new BucketLimitSnapshot(931L, POLICY_ID, LimitAxisType.TIMES,
                BigDecimal.valueOf(20), BigDecimal.valueOf(17), BigDecimal.ZERO, BigDecimal.valueOf(3),
                PERIOD_START, PERIOD_END);
        ClaimLimitEvaluationContext ctx = new ClaimLimitEvaluationContext();

        List<BucketLimitSnapshot> forLine1 = ctx.adjustForNextLine(List.of(base));
        UnifiedLimitInput in1 = lineInput(900L, 2, 0, CountingMethod.EACH_UNIT,
                new BigDecimal("100.00"), new BigDecimal("200.00"));
        UnifiedLimitDecision d1 = UnifiedLimitResolver.resolve(in1, forLine1);
        assertThat(d1.approvedQuantity()).isEqualTo(2);
        ctx.recordLineConsumption(forLine1, d1, DATE);

        List<BucketLimitSnapshot> forLine2 = ctx.adjustForNextLine(List.of(base));
        assertThat(forLine2.get(0).remaining()).isEqualByComparingTo("1"); // 3 - 2 pending from line1
        UnifiedLimitInput in2 = lineInput(900L, 2, 0, CountingMethod.EACH_UNIT,
                new BigDecimal("100.00"), new BigDecimal("200.00"));
        UnifiedLimitDecision d2 = UnifiedLimitResolver.resolve(in2, forLine2);

        assertThat(d2.approvedQuantity()).isEqualTo(1);
        assertThat(d2.refusedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("BG2 — two lines, same AMOUNT bucket, remaining=500: line1=300 approved, line2=300 sees only 200 left")
    void bg2TwoLinesSameAmountBucket() {
        BucketLimitSnapshot base = new BucketLimitSnapshot(932L, POLICY_ID, LimitAxisType.AMOUNT,
                BigDecimal.valueOf(1000), BigDecimal.valueOf(500), BigDecimal.ZERO,
                new BigDecimal("500.00"), PERIOD_START, PERIOD_END);
        ClaimLimitEvaluationContext ctx = new ClaimLimitEvaluationContext();

        List<BucketLimitSnapshot> forLine1 = ctx.adjustForNextLine(List.of(base));
        UnifiedLimitInput in1 = lineInput(901L, 1, 0, CountingMethod.EACH_LINE,
                null, new BigDecimal("300.00"));
        UnifiedLimitDecision d1 = UnifiedLimitResolver.resolve(in1, forLine1);
        assertThat(d1.bindingAvailableAmount()).isEqualByComparingTo("300.00");
        ctx.recordLineConsumption(forLine1, d1, DATE);

        List<BucketLimitSnapshot> forLine2 = ctx.adjustForNextLine(List.of(base));
        assertThat(forLine2.get(0).remaining()).isEqualByComparingTo("200.00"); // 500 - 300
        UnifiedLimitInput in2 = lineInput(901L, 1, 0, CountingMethod.EACH_LINE,
                null, new BigDecimal("300.00"));
        UnifiedLimitDecision d2 = UnifiedLimitResolver.resolve(in2, forLine2);

        // 300 requested but only 200 left -- NOT another full 300.
        assertThat(d2.bindingAvailableAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("BG3 — two different rules resolving to the SAME shared parent bucket: line2 sees line1's consumption")
    void bg3SharedParentBucketAcrossDifferentRules() {
        Long sharedParentBucketId = 933L;
        BucketLimitSnapshot sharedParent = new BucketLimitSnapshot(sharedParentBucketId, POLICY_ID,
                LimitAxisType.AMOUNT, BigDecimal.valueOf(1000), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("1000.00"), PERIOD_START, PERIOD_END);
        ClaimLimitEvaluationContext ctx = new ClaimLimitEvaluationContext();

        // Line 1: category A's rule resolves to the shared parent (its own
        // child bucket has no ceiling of its own, so only the parent binds).
        List<BucketLimitSnapshot> forLine1 = ctx.adjustForNextLine(List.of(sharedParent));
        UnifiedLimitInput in1 = lineInput(902L, 1, 0, CountingMethod.EACH_LINE, null, new BigDecimal("700.00"));
        UnifiedLimitDecision d1 = UnifiedLimitResolver.resolve(in1, forLine1);
        assertThat(d1.bindingAvailableAmount()).isEqualByComparingTo("700.00");
        ctx.recordLineConsumption(forLine1, d1, DATE);

        // Line 2: a DIFFERENT category/rule, but its chain also walks up to
        // the SAME shared parent bucket.
        List<BucketLimitSnapshot> forLine2 = ctx.adjustForNextLine(List.of(sharedParent));
        assertThat(forLine2.get(0).remaining()).isEqualByComparingTo("300.00"); // 1000 - 700
        UnifiedLimitInput in2 = lineInput(903L, 1, 0, CountingMethod.EACH_LINE, null, new BigDecimal("700.00"));
        UnifiedLimitDecision d2 = UnifiedLimitResolver.resolve(in2, forLine2);

        // Only 300 of the shared ceiling is left for line2, even though line2
        // belongs to a completely different rule than line1.
        assertThat(d2.bindingAvailableAmount()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("BG4 — two lines, same bucket + period + service date: only the first spends the day")
    void bg4TwoLinesSameServiceDaySpendOnlyOneDay() {
        BucketLimitSnapshot base = new BucketLimitSnapshot(934L, POLICY_ID, LimitAxisType.DAYS,
                BigDecimal.valueOf(10), BigDecimal.valueOf(9), BigDecimal.ZERO, BigDecimal.ONE,
                PERIOD_START, PERIOD_END);
        ClaimLimitEvaluationContext ctx = new ClaimLimitEvaluationContext();

        // Line 1: the day has not been consumed yet (neither by the DB nor by
        // this batch) -- requestedDays=1, exactly like today's
        // `!serviceDayAlreadyUsed && !addedDay`.
        boolean alreadyBeforeLine1 = ctx.dayAlreadyConsumedThisBatch(934L, PERIOD_START, PERIOD_END, DATE);
        assertThat(alreadyBeforeLine1).isFalse();
        List<BucketLimitSnapshot> forLine1 = ctx.adjustForNextLine(List.of(base));
        UnifiedLimitInput in1 = lineInput(904L, 1, 1, CountingMethod.PER_DAY, null, new BigDecimal("300.00"));
        UnifiedLimitDecision d1 = UnifiedLimitResolver.resolve(in1, forLine1);
        assertThat(d1.approvedDays()).isEqualTo(1);
        ctx.recordLineConsumption(forLine1, d1, DATE);

        // Line 2: same bucket, same period, same service date -- the day was
        // already spent by line1 THIS batch, so line2 must ask for 0 days.
        boolean alreadyBeforeLine2 = ctx.dayAlreadyConsumedThisBatch(934L, PERIOD_START, PERIOD_END, DATE);
        assertThat(alreadyBeforeLine2).isTrue();
        List<BucketLimitSnapshot> forLine2 = ctx.adjustForNextLine(List.of(base));
        UnifiedLimitInput in2 = lineInput(905L, 1, 0, CountingMethod.PER_DAY, null, new BigDecimal("150.00"));
        UnifiedLimitDecision d2 = UnifiedLimitResolver.resolve(in2, forLine2);

        // Not blocked by the day limit -- the day was already accounted for.
        assertThat(d2.refusedDays()).isZero();
        assertThat(d2.bindingConstraintType()).isNotEqualTo(BindingConstraintType.DAYS);
    }

    @Test
    @DisplayName("BG4b — a DIFFERENT bucket on the same service date is NOT affected by another bucket's day consumption")
    void bg4bDifferentBucketIsIndependent() {
        ClaimLimitEvaluationContext ctx = new ClaimLimitEvaluationContext();
        BucketLimitSnapshot bucketA = new BucketLimitSnapshot(934L, POLICY_ID, LimitAxisType.DAYS,
                BigDecimal.valueOf(10), BigDecimal.valueOf(9), BigDecimal.ZERO, BigDecimal.ONE,
                PERIOD_START, PERIOD_END);
        UnifiedLimitDecision d1 = UnifiedLimitResolver.resolve(
                lineInput(904L, 1, 1, CountingMethod.PER_DAY, null, new BigDecimal("300.00")),
                ctx.adjustForNextLine(List.of(bucketA)));
        ctx.recordLineConsumption(List.of(bucketA), d1, DATE);

        // A DIFFERENT bucket (935L), same period, same service date: line1's
        // consumption of bucket 934L must not leak into it.
        boolean bucketBAlreadyConsumed = ctx.dayAlreadyConsumedThisBatch(935L, PERIOD_START, PERIOD_END, DATE);
        assertThat(bucketBAlreadyConsumed).isFalse();
    }

    @Test
    @DisplayName("BG4c — the SAME bucket on a DIFFERENT service date is NOT affected by another date's day consumption")
    void bg4cDifferentServiceDateIsIndependent() {
        ClaimLimitEvaluationContext ctx = new ClaimLimitEvaluationContext();
        BucketLimitSnapshot bucket = new BucketLimitSnapshot(934L, POLICY_ID, LimitAxisType.DAYS,
                BigDecimal.valueOf(10), BigDecimal.valueOf(9), BigDecimal.ZERO, BigDecimal.ONE,
                PERIOD_START, PERIOD_END);
        UnifiedLimitDecision d1 = UnifiedLimitResolver.resolve(
                lineInput(904L, 1, 1, CountingMethod.PER_DAY, null, new BigDecimal("300.00")),
                ctx.adjustForNextLine(List.of(bucket)));
        ctx.recordLineConsumption(List.of(bucket), d1, DATE);

        // The SAME bucket, but a different service date (e.g. a batch mixing
        // dates): line1's consumption on DATE must not block DATE.plusDays(1).
        boolean otherDateAlreadyConsumed =
                ctx.dayAlreadyConsumedThisBatch(934L, PERIOD_START, PERIOD_END, DATE.plusDays(1));
        assertThat(otherDateAlreadyConsumed).isFalse();
    }

    @Test
    @DisplayName("BG5 — a DB-level RESERVED amount and this batch's own pending usage subtract independently, never doubled or dropped")
    void bg5ReservedAndBatchPendingBothApply() {
        // configured=1000, committed=0, DB-level activeReserved=500 (from
        // another decision entirely) -> base remaining is already 500.
        BucketLimitSnapshot base = new BucketLimitSnapshot(935L, POLICY_ID, LimitAxisType.AMOUNT,
                BigDecimal.valueOf(1000), BigDecimal.ZERO, new BigDecimal("500.00"),
                new BigDecimal("500.00"), PERIOD_START, PERIOD_END);
        ClaimLimitEvaluationContext ctx = new ClaimLimitEvaluationContext();

        List<BucketLimitSnapshot> forLine1 = ctx.adjustForNextLine(List.of(base));
        assertThat(forLine1.get(0).remaining()).isEqualByComparingTo("500.00"); // unaffected, no pending yet
        UnifiedLimitInput in1 = lineInput(906L, 1, 0, CountingMethod.EACH_LINE, null, new BigDecimal("300.00"));
        UnifiedLimitDecision d1 = UnifiedLimitResolver.resolve(in1, forLine1);
        assertThat(d1.bindingAvailableAmount()).isEqualByComparingTo("300.00");
        ctx.recordLineConsumption(forLine1, d1, DATE);

        List<BucketLimitSnapshot> forLine2 = ctx.adjustForNextLine(List.of(base));
        // 500 (already net of the DB's own RESERVED 500) - 300 (this batch's line1) = 200.
        assertThat(forLine2.get(0).remaining()).isEqualByComparingTo("200.00");
        UnifiedLimitInput in2 = lineInput(907L, 1, 0, CountingMethod.EACH_LINE, null, new BigDecimal("300.00"));
        UnifiedLimitDecision d2 = UnifiedLimitResolver.resolve(in2, forLine2);

        assertThat(d2.bindingAvailableAmount()).isEqualByComparingTo("200.00");
    }
}
