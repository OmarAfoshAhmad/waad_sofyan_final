package com.waad.tba.modules.claim.service.finance.golden;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P1.2 (per PROMPT_إغلاق_P1_توحيد_الحدود_والقرار_المالي_WAAD): the six golden
 * fixtures G1-G6, fixed BEFORE UnifiedLimitResolver/UnifiedLimitDecision are
 * written. Pure arithmetic, no Spring context, no engine wiring -- these
 * numbers are the target the real resolver must reproduce exactly (P1.10:
 * Preview = Save = Snapshot = Ledger, all four against these same numbers).
 *
 * Do not edit expected numbers without a new approved business decision.
 */
class UnifiedLimitDecisionGoldenTest {

    private static BigDecimal money(String v) {
        return new BigDecimal(v).setScale(2, RoundingMode.HALF_UP);
    }

    private record Split(BigDecimal allowed, BigDecimal company, BigDecimal copay, BigDecimal nonCovered) {}

    private static Split split(BigDecimal insuranceBasis, BigDecimal allowedEligible, int coveragePercent) {
        BigDecimal company = allowedEligible.multiply(BigDecimal.valueOf(coveragePercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal copay = allowedEligible.subtract(company).setScale(2, RoundingMode.HALF_UP);
        BigDecimal nonCovered = insuranceBasis.subtract(allowedEligible).setScale(2, RoundingMode.HALF_UP);
        return new Split(allowedEligible, company, copay, nonCovered);
    }

    @Test
    @DisplayName("G1 — amount-only limit, no occurrence dimension involved")
    void g1AmountOnly() {
        BigDecimal requested = money("1000.00");
        BigDecimal remainingAmount = money("600.00");

        BigDecimal allowed = requested.min(remainingAmount);
        Split s = split(requested, allowed, 75);

        assertThat(s.allowed()).isEqualByComparingTo("600.00");
        assertThat(s.company()).isEqualByComparingTo("450.00");
        assertThat(s.copay()).isEqualByComparingTo("150.00");
        assertThat(s.nonCovered()).isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("G2 — times partial acceptance (the Physio gate, §25): 3 requested, 2 remaining")
    void g2TimesPartialAcceptance() {
        BigDecimal unitPrice = money("100.00");
        int requestedQty = 3;
        int remainingTimes = 2;

        int approvedQty = Math.min(requestedQty, remainingTimes);
        int refusedQty = requestedQty - approvedQty;
        BigDecimal requested = unitPrice.multiply(BigDecimal.valueOf(requestedQty));
        BigDecimal allowed = unitPrice.multiply(BigDecimal.valueOf(approvedQty));
        Split s = split(requested, allowed, 75);

        assertThat(approvedQty).isEqualTo(2);
        assertThat(refusedQty).isEqualTo(1);
        assertThat(s.allowed()).isEqualByComparingTo("200.00");
        assertThat(s.company()).isEqualByComparingTo("150.00");
        assertThat(s.copay()).isEqualByComparingTo("50.00");
        assertThat(s.nonCovered()).isEqualByComparingTo("100.00");
        // Ledger consumption per §14: exactly the approved units, never the requested.
        int consumedTimes = approvedQty;
        assertThat(consumedTimes).isEqualTo(2);
    }

    @Test
    @DisplayName("G3 — days: atomic, not divisible (a day is not half-spent)")
    void g3DaysAreAtomicNotDivisible() {
        // Unlike EACH_UNIT quantity, days have no partial-acceptance rule
        // anywhere in the codebase today (DivisibleLimitSplitter only
        // divides EACH_UNIT). One requested day against zero remaining days
        // is refused whole, exactly like EACH_LINE/PER_VISIT.
        int requestedDays = 1;
        int remainingDays = 0;
        BigDecimal dayCharge = money("300.00");

        int approvedDays = remainingDays >= requestedDays ? requestedDays : 0;
        int refusedDays = requestedDays - approvedDays;
        BigDecimal allowed = approvedDays > 0 ? dayCharge : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Split s = split(dayCharge, allowed, 100);

        assertThat(approvedDays).isZero();
        assertThat(refusedDays).isEqualTo(1);
        assertThat(s.allowed()).isEqualByComparingTo("0.00");
        assertThat(s.nonCovered()).isEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("G4 — amount and times bind together: the tighter one in WHOLE units wins")
    void g4AmountAndTimesBindTogether() {
        BigDecimal unitPrice = money("100.00");
        int requestedQty = 5;
        int remainingTimes = 4;
        BigDecimal remainingAmount = money("250.00");

        // A unit is indivisible below its own price: the amount limit can
        // only ever approve whole units, never a fractional 2.5th unit.
        int unitsAffordableByAmount = remainingAmount.divideToIntegralValue(unitPrice).intValue();
        int approvedQty = Math.min(requestedQty, Math.min(remainingTimes, unitsAffordableByAmount));
        BigDecimal requested = unitPrice.multiply(BigDecimal.valueOf(requestedQty));
        BigDecimal allowed = unitPrice.multiply(BigDecimal.valueOf(approvedQty));

        assertThat(unitsAffordableByAmount).isEqualTo(2);
        assertThat(approvedQty).isEqualTo(2);
        assertThat(allowed).isEqualByComparingTo("200.00");
        assertThat(allowed).isNotEqualByComparingTo(remainingAmount); // NOT 250 -- the whole-unit constraint bites first
        assertThat(requested).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("G5 — shared bucket: reserved amount is not available to a new claim")
    void g5SharedBucketRespectsReservation() {
        int timesLimit = 20;
        int committed = 10;
        int reserved = 4;

        int available = timesLimit - committed - reserved;
        assertThat(available).isEqualTo(6);

        int requestedByNewClaim = 8;
        int approvedForNewClaim = Math.min(requestedByNewClaim, available);
        assertThat(approvedForNewClaim).isEqualTo(6);
        assertThat(approvedForNewClaim).isLessThanOrEqualTo(available);
    }

    @Test
    @DisplayName("G6 — a bucket that does not belong to the requested policy blocks the decision")
    void g6PolicyOwnershipMismatchBlocks() {
        long requestedPolicyId = 700L;
        long bucketOwningPolicyId = 701L;

        assertThatThrownBy(() -> {
            if (bucketOwningPolicyId != requestedPolicyId) {
                throw new IllegalStateException("BUCKET_POLICY_MISMATCH: bucket belongs to policy id="
                        + bucketOwningPolicyId + ", not the requested policy id=" + requestedPolicyId);
            }
        }).isInstanceOf(IllegalStateException.class).hasMessageContaining("BUCKET_POLICY_MISMATCH");
    }
}
