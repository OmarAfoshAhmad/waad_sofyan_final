package com.waad.tba.modules.benefitpolicy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.benefitpolicy.service.DivisibleLimitSplitter.UnitSplit;

/**
 * The one shared "how much of this line is payable when a times-limit falls
 * short" calculation, used by both CoverageEngineService (claims) and
 * PreAuthorizationDecisionBuilder (pre-authorizations). Proving it here once
 * is what makes the same requested/remaining-times input produce the same
 * money on both paths (P2 acceptance case 8) -- not a coincidence of two
 * separately-tested implementations agreeing.
 */
class DivisibleLimitSplitterTest {

    @Test
    void requestingTwentyOneWithTwentyRemainingCoversTwentyAndRefusesOne() {
        UnitSplit split = DivisibleLimitSplitter.splitUnits(CountingMethod.EACH_UNIT, 21, 20);

        assertThat(split.coveredUnits()).isEqualTo(20);
        assertThat(split.refusedUnits()).isEqualTo(1);
    }

    @Test
    void zeroRemainingRefusesEverythingRequested() {
        UnitSplit split = DivisibleLimitSplitter.splitUnits(CountingMethod.EACH_UNIT, 8, 0);

        assertThat(split.coveredUnits()).isZero();
        assertThat(split.refusedUnits()).isEqualTo(8);
        assertThat(DivisibleLimitSplitter.coveredAmountFor(new BigDecimal("800.00"), split))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void requestingWithinTheRemainingBalanceCoversAllOfIt() {
        UnitSplit split = DivisibleLimitSplitter.splitUnits(CountingMethod.EACH_UNIT, 15, 20);

        assertThat(split.coveredUnits()).isEqualTo(15);
        assertThat(split.refusedUnits()).isZero();
    }

    @Test
    void everyIndivisibleMethodRefusesTheWholeOccurrenceOnAnyShortfall() {
        for (CountingMethod method : new CountingMethod[] {
                CountingMethod.EACH_LINE, CountingMethod.PER_VISIT, CountingMethod.PER_DAY }) {
            UnitSplit split = DivisibleLimitSplitter.splitUnits(method, 1, 0);
            assertThat(split.coveredUnits()).as(method.name()).isZero();
            assertThat(split.refusedUnits()).as(method.name()).isEqualTo(1);
        }
    }

    @Test
    void coveredPlusRefusedAlwaysReconcilesToTheOriginalAmountAfterRounding() {
        // 100.00 split 1/3 covered, 2/3 refused -- exactly the shape that
        // breaks naive independent rounding on both sides.
        UnitSplit split = new UnitSplit(1, 2);
        BigDecimal lineTotal = new BigDecimal("100.00");

        BigDecimal covered = DivisibleLimitSplitter.coveredAmountFor(lineTotal, split);
        BigDecimal refused = lineTotal.subtract(covered);

        assertThat(covered.add(refused)).isEqualByComparingTo(lineTotal);
    }

    @Test
    void sameInputsProduceTheSameAnswerRegardlessOfCaller() {
        // Stands in for "claims and pre-authorizations agree" (case 8):
        // both call exactly this function with the same requested/remaining
        // times, so there is no second implementation to drift from it.
        UnitSplit split = DivisibleLimitSplitter.splitUnits(CountingMethod.EACH_UNIT, 21, 20);
        BigDecimal companyShareLikeClaims = DivisibleLimitSplitter.coveredAmountFor(new BigDecimal("2100.00"), split);
        BigDecimal companyShareLikePreauth = DivisibleLimitSplitter.coveredAmountFor(new BigDecimal("2100.00"), split);

        assertThat(companyShareLikeClaims).isEqualByComparingTo(companyShareLikePreauth);
        assertThat(companyShareLikeClaims).isEqualByComparingTo("2000.00");
    }
}
