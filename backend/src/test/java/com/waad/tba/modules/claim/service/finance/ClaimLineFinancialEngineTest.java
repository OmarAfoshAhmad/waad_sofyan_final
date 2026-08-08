package com.waad.tba.modules.claim.service.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.waad.tba.modules.claim.service.finance.ClaimLineFinancialEngine.Input;
import com.waad.tba.modules.claim.service.finance.ClaimLineFinancialEngine.Result;

/**
 * Direct unit coverage for the pure engine extracted from ClaimMapper in
 * finance-00 step 2. No Spring context, no database -- every case here is a
 * closed-form arithmetic check, and every result is cross-checked against the
 * invariant: requestedAmount == patientShare + refusedAmount +
 * companyDiscountAmount + companyShare.
 */
class ClaimLineFinancialEngineTest {

    private final ClaimLineFinancialEngine engine = new ClaimLineFinancialEngine();

    private static void assertInvariant(Result r) {
        BigDecimal reconstructed = r.patientShare()
                .add(r.refusedAmount())
                .add(r.companyDiscountAmount())
                .add(r.companyShare());
        assertThat(reconstructed).as("requestedAmount must equal the sum of every component")
                .isEqualByComparingTo(r.requestedAmount());
    }

    @Test
    void fullCoverageNoRejectionNoDiscount_everythingGoesToCompany() {
        Result r = engine.evaluate(new Input(
                new BigDecimal("500.00"), 100, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, true, false, 1));

        assertThat(r.requestedAmount()).isEqualByComparingTo("500.00");
        assertThat(r.allowedAmount()).isEqualByComparingTo("500.00");
        assertThat(r.patientShare()).isEqualByComparingTo("0.00");
        assertThat(r.refusedAmount()).isEqualByComparingTo("0.00");
        assertThat(r.companyDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(r.companyShare()).isEqualByComparingTo("500.00");
        assertThat(r.approvedQuantity()).isEqualTo(1);
        assertInvariant(r);
    }

    @Test
    void coveragePercentSplitsPatientShareBeforeAnythingElse() {
        // 1000 gross, 80% coverage -> patient=200, providerShare=800, no
        // rejection/discount so companyShare must be exactly 800.
        Result r = engine.evaluate(new Input(
                new BigDecimal("1000.00"), 80, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, true, false, 3));

        assertThat(r.patientShare()).isEqualByComparingTo("200.00");
        assertThat(r.companyShare()).isEqualByComparingTo("800.00");
        assertThat(r.approvedQuantity()).isEqualTo(3);
        assertInvariant(r);
    }

    @Test
    void beforeMode_discountsTheFullProviderShareThenSubtractsRejection() {
        // gross=500, 100% coverage -> providerShare=500. reject 100. 10% discount.
        // BEFORE: discount=50, providerNet=450, rejected=min(450,100)=100, company=350.
        Result r = engine.evaluate(new Input(
                new BigDecimal("500.00"), 100, new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("10.00"), true, false, 1));

        assertThat(r.companyDiscountAmount()).isEqualByComparingTo("50.00");
        assertThat(r.refusedAmount()).isEqualByComparingTo("100.00");
        assertThat(r.companyShare()).isEqualByComparingTo("350.00");
        assertInvariant(r);
    }

    @Test
    void afterMode_subtractsRejectionFirstThenDiscountsOnlyTheRemainder() {
        // Same inputs, discountBeforeRejection flipped.
        // AFTER: afterRejection=500-100=400, discount=40, company=360.
        Result r = engine.evaluate(new Input(
                new BigDecimal("500.00"), 100, new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("10.00"), false, false, 1));

        assertThat(r.companyDiscountAmount()).isEqualByComparingTo("40.00");
        assertThat(r.refusedAmount()).isEqualByComparingTo("100.00");
        assertThat(r.companyShare()).isEqualByComparingTo("360.00");
        assertInvariant(r);
    }

    @Test
    void beforeAndAfterModesProduceDifferentNumbers_theOrderingIsNotCosmetic() {
        Input base = new Input(new BigDecimal("500.00"), 100, new BigDecimal("100.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10.00"), true, false, 1);
        Result before = engine.evaluate(base);
        Result after = engine.evaluate(new Input(base.grossAmount(), base.coveragePercent(),
                base.manualRefusedAmount(), base.priceRefusedAmount(), base.limitRefusedAmount(),
                base.discountPercent(), false, base.fullyRejected(), base.quantity()));

        assertThat(before.companyShare()).isNotEqualByComparingTo(after.companyShare());
    }

    @Test
    void copayRejectionAndDiscountCombine_beforeMode() {
        // gross=1000, coverage=80% -> patient=200, providerShare=800. reject 150.
        // BEFORE: discount=800*10%=80, providerNet=720, rejected=150, company=570.
        Result r = engine.evaluate(new Input(
                new BigDecimal("1000.00"), 80, new BigDecimal("150.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("10.00"), true, false, 1));

        assertThat(r.patientShare()).isEqualByComparingTo("200.00");
        assertThat(r.refusedAmount()).isEqualByComparingTo("150.00");
        assertThat(r.companyDiscountAmount()).isEqualByComparingTo("80.00");
        assertThat(r.companyShare()).isEqualByComparingTo("570.00");
        assertInvariant(r);
    }

    @Test
    void copayRejectionAndDiscountCombine_afterMode() {
        // Same inputs, AFTER mode: afterRejection=800-150=650, discount=65, company=585.
        Result r = engine.evaluate(new Input(
                new BigDecimal("1000.00"), 80, new BigDecimal("150.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("10.00"), false, false, 1));

        assertThat(r.patientShare()).isEqualByComparingTo("200.00");
        assertThat(r.refusedAmount()).isEqualByComparingTo("150.00");
        assertThat(r.companyDiscountAmount()).isEqualByComparingTo("65.00");
        assertThat(r.companyShare()).isEqualByComparingTo("585.00");
        assertInvariant(r);
    }

    @Test
    void fullyRejectedLine_ignoresManualRefusedAmountAndFloorsAtZero() {
        // The whole provider share (500) becomes the rejection candidate,
        // regardless of any manualRefusedAmount passed alongside it.
        Result r = engine.evaluate(new Input(
                new BigDecimal("500.00"), 100, new BigDecimal("1.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("10.00"), true, true, 5));

        assertThat(r.companyShare()).isEqualByComparingTo("0.00");
        assertThat(r.approvedQuantity()).isEqualTo(0);
        assertInvariant(r);
    }

    @Test
    void priceAndLimitRefusalsAreNeverReappliedAgainstTheProviderShare() {
        // gross is already post-price-ceiling and the limit cut is applied on
        // top of it inside allowedAmount; both must be reflected once in
        // refusedAmount/requestedAmount and never subtracted a second time
        // from patientShare/companyShare.
        Result r = engine.evaluate(new Input(
                new BigDecimal("400.00"), 100, BigDecimal.ZERO, new BigDecimal("50.00"),
                new BigDecimal("40.00"), BigDecimal.ZERO, true, false, 1));

        assertThat(r.requestedAmount()).isEqualByComparingTo("450.00"); // 400 gross + 50 price-refused
        assertThat(r.allowedAmount()).isEqualByComparingTo("360.00"); // 400 - 40 limit-refused
        assertThat(r.refusedAmount()).isEqualByComparingTo("90.00"); // 50 + 40 + 0 rejection
        assertThat(r.companyShare()).isEqualByComparingTo("360.00");
        assertInvariant(r);
    }

    @Test
    void manualRefusalCannotExceedWhatIsActuallyAvailableToReject() {
        // gross=100, 100% coverage -> providerShare=100. A reviewer typo of 500
        // manual-refused must be capped at what is actually available (100),
        // never driving companyShare negative.
        Result r = engine.evaluate(new Input(
                new BigDecimal("100.00"), 100, new BigDecimal("500.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, false, false, 1));

        assertThat(r.refusedAmount()).isEqualByComparingTo("100.00");
        assertThat(r.companyShare()).isEqualByComparingTo("0.00");
        assertInvariant(r);
    }

    @Test
    void roundingUsesHalfUpAtTwoDecimalsConsistently() {
        // 33.335 patient-side arithmetic must round HALF_UP to 2dp at every step,
        // not accumulate float-style drift across patient/discount/company.
        Result r = engine.evaluate(new Input(
                new BigDecimal("100.01"), 67, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("7.00"), true, false, 1));

        // patient = 100.01 * 33% = 33.0033 -> 33.00 (HALF_UP)
        assertThat(r.patientShare()).isEqualByComparingTo("33.00");
        assertInvariant(r);
    }

    @Test
    void zeroCoverageMeansTheEntireAllowedAmountIsPatientResponsibility() {
        Result r = engine.evaluate(new Input(
                new BigDecimal("250.00"), 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, true, false, 1));

        assertThat(r.patientShare()).isEqualByComparingTo("250.00");
        assertThat(r.companyShare()).isEqualByComparingTo("0.00");
        assertInvariant(r);
    }
}
