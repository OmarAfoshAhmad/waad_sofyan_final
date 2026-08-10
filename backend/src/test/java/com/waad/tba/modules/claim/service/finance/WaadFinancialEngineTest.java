package com.waad.tba.modules.claim.service.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.waad.tba.modules.claim.service.finance.WaadFinancialEngine.Input;
import com.waad.tba.modules.claim.service.finance.WaadFinancialEngine.LimitMode;
import com.waad.tba.modules.claim.service.finance.WaadFinancialEngine.Result;

/**
 * Matrix coverage for WaadFinancialEngine against
 * backend/docs/design/FINANCIAL_CONSTITUTION.md, extended for the
 * finance-02.1 contract-hardening review: complete input validation,
 * explicit LimitMode.UNLIMITED, no approvedQuantity, insideLimit/
 * bindingAvailableLimit/bindingRemainingLimit naming, and an explicit
 * invariant check inside evaluate() rather than "correct by construction".
 * See WaadFinConstitutionGoldenTest for the single reference scenario (S14).
 */
class WaadFinancialEngineTest {

    private final WaadFinancialEngine engine = new WaadFinancialEngine();

    private static Input limited(BigDecimal requestedAmount, BigDecimal contractualPrice,
            BigDecimal bindingAvailableLimit, int coveragePercent, BigDecimal discountPercent,
            BigDecimal rejectedAmount, boolean fullyRejected, int quantity) {
        return new Input(requestedAmount, contractualPrice, LimitMode.LIMITED, bindingAvailableLimit,
                coveragePercent, discountPercent, rejectedAmount, fullyRejected, quantity);
    }

    private static Input unlimited(BigDecimal requestedAmount, BigDecimal contractualPrice,
            int coveragePercent, BigDecimal discountPercent, BigDecimal rejectedAmount,
            boolean fullyRejected, int quantity) {
        return new Input(requestedAmount, contractualPrice, LimitMode.UNLIMITED, null,
                coveragePercent, discountPercent, rejectedAmount, fullyRejected, quantity);
    }

    private static void assertInvariant(Result r) {
        // S13
        BigDecimal reconstructed = r.contractualPriceExcess()
                .add(r.patientCoverageShare())
                .add(r.patientLimitExcess())
                .add(r.providerContractDiscount())
                .add(r.providerRejectedAmount())
                .add(r.insurerFinalPayment());
        assertThat(reconstructed).as("S13 invariant").isEqualByComparingTo(r.requestedAmount());
    }

    // ── S37 "Full limit" ────────────────────────────────────────────────────

    @Test
    void fullLimit_serviceFitsEntirelyWithinTheLimit() {
        Result r = engine.evaluate(limited(
                new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                80, BigDecimal.ZERO, BigDecimal.ZERO, false, 1));

        assertThat(r.patientCoverageShare()).isEqualByComparingTo("200.00");
        assertThat(r.patientLimitExcess()).isEqualByComparingTo("0.00");
        assertThat(r.insurerGrossShare()).isEqualByComparingTo("800.00");
        assertThat(r.limitConsumption()).isEqualByComparingTo("1000.00");
        assertThat(r.bindingRemainingLimit()).isEqualByComparingTo("0.00");
        assertInvariant(r);
    }

    // ── S37 "Partial remaining limit" ───────────────────────────────────────

    @Test
    void partialRemainingLimit_excessFallsEntirelyOnThePatient() {
        Result r = engine.evaluate(limited(
                new BigDecimal("400.00"), new BigDecimal("400.00"), new BigDecimal("200.00"),
                80, BigDecimal.ZERO, BigDecimal.ZERO, false, 1));

        assertThat(r.insideLimit()).isEqualByComparingTo("200.00");
        assertThat(r.patientCoverageShare()).isEqualByComparingTo("40.00");
        assertThat(r.patientLimitExcess()).isEqualByComparingTo("200.00");
        assertThat(r.patientTotalResponsibility()).isEqualByComparingTo("240.00");
        assertThat(r.insurerGrossShare()).isEqualByComparingTo("160.00");
        assertThat(r.bindingRemainingLimit()).isEqualByComparingTo("0.00");
        assertInvariant(r);
    }

    // ── S37 "Provider discount" ─────────────────────────────────────────────

    @Test
    void providerDiscount_appliesToInsurerGrossShareOnly() {
        Result r = engine.evaluate(limited(
                new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                80, new BigDecimal("10.00"), BigDecimal.ZERO, false, 1));

        assertThat(r.patientCoverageShare()).isEqualByComparingTo("200.00");
        assertThat(r.insurerGrossShare()).isEqualByComparingTo("800.00");
        assertThat(r.providerContractDiscount()).isEqualByComparingTo("80.00");
        assertThat(r.providerNetBeforeRejection()).isEqualByComparingTo("800.00");
        assertThat(r.insurerFinalPayment()).isEqualByComparingTo("720.00");
        assertThat(r.limitConsumption()).isEqualByComparingTo("1000.00");
        assertInvariant(r);
    }

    @Test
    void s9_discountNeverReducesPatientLimitExcess_patientBenefitsFromContractualPriceOnly() {
        Result withDiscount = engine.evaluate(limited(
                new BigDecimal("1200.00"), new BigDecimal("1000.00"), BigDecimal.ZERO,
                80, new BigDecimal("10.00"), BigDecimal.ZERO, false, 1));
        Result withoutDiscount = engine.evaluate(limited(
                new BigDecimal("1200.00"), new BigDecimal("1000.00"), BigDecimal.ZERO,
                80, BigDecimal.ZERO, BigDecimal.ZERO, false, 1));

        assertThat(withDiscount.patientLimitExcess()).isEqualByComparingTo("1000.00");
        assertThat(withDiscount.patientLimitExcess())
                .as("S9: the discount rate must never change patientLimitExcess")
                .isEqualByComparingTo(withoutDiscount.patientLimitExcess());
        assertThat(withDiscount.contractualPriceExcess()).isEqualByComparingTo("200.00");
        assertInvariant(withDiscount);
    }

    // ── S37 "Provider rejection" ────────────────────────────────────────────

    @Test
    void providerRejection_isSubtractedBeforeDiscountAndNeverTouchesThePatient() {
        Result r = engine.evaluate(limited(
                new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                80, new BigDecimal("10.00"), new BigDecimal("100.00"), false, 1));

        assertThat(r.providerNetBeforeRejection()).isEqualByComparingTo("700.00");
        assertThat(r.providerContractDiscount()).isEqualByComparingTo("70.00");
        assertThat(r.insurerFinalPayment()).isEqualByComparingTo("630.00");
        assertThat(r.patientCoverageShare()).isEqualByComparingTo("200.00");
        assertThat(r.patientLimitExcess()).isEqualByComparingTo("0.00");
        assertThat(r.limitConsumption()).isEqualByComparingTo("1000.00");
        assertInvariant(r);
    }

    @Test
    void settlementRegression_refusalThenTenPercentDiscountProducesExpectedProviderNet() {
        Result r = engine.evaluate(unlimited(
                new BigDecimal("155.00"), new BigDecimal("155.00"),
                75, new BigDecimal("10.00"), new BigDecimal("10.00"), false, 1));

        assertThat(r.insurerGrossShare()).isEqualByComparingTo("116.25");
        assertThat(r.providerRejectedAmount()).isEqualByComparingTo("10.00");
        assertThat(r.providerNetBeforeRejection()).isEqualByComparingTo("106.25");
        assertThat(r.providerContractDiscount()).isEqualByComparingTo("10.63");
        assertThat(r.insurerFinalPayment()).isEqualByComparingTo("95.62");
        assertInvariant(r);
    }

    @Test
    void settlementTiming_contractControlsApprovedBeforeDiscountAndProviderNet() {
        Input before = new Input(new BigDecimal("155.00"), new BigDecimal("155.00"),
                LimitMode.UNLIMITED, null, 75, new BigDecimal("10.00"), true,
                new BigDecimal("20.00"), false, 1);
        Input after = new Input(new BigDecimal("155.00"), new BigDecimal("155.00"),
                LimitMode.UNLIMITED, null, 75, new BigDecimal("10.00"), false,
                new BigDecimal("20.00"), false, 1);

        Result beforeResult = engine.evaluate(before);
        assertThat(beforeResult.providerNetBeforeRejection()).isEqualByComparingTo("116.25");
        assertThat(beforeResult.providerContractDiscount()).isEqualByComparingTo("11.63");
        assertThat(beforeResult.providerRejectedAmount()).isEqualByComparingTo("20.00");
        assertThat(beforeResult.insurerFinalPayment()).isEqualByComparingTo("84.62");
        assertInvariant(beforeResult);

        Result afterResult = engine.evaluate(after);
        assertThat(afterResult.providerNetBeforeRejection()).isEqualByComparingTo("96.25");
        assertThat(afterResult.providerContractDiscount()).isEqualByComparingTo("9.63");
        assertThat(afterResult.providerRejectedAmount()).isEqualByComparingTo("20.00");
        assertThat(afterResult.insurerFinalPayment()).isEqualByComparingTo("86.62");
        assertInvariant(afterResult);
    }

    @Test
    void s11_rejectionExceedingNetBeforeRejectionFailsClosed() {
        assertThatThrownBy(() -> engine.evaluate(limited(
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"),
                100, BigDecimal.ZERO, new BigDecimal("150.00"), false, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fullyRejected_usesProviderNetBeforeRejectionRegardlessOfProviderRejectedAmount() {
        Result r = engine.evaluate(limited(
                new BigDecimal("500.00"), new BigDecimal("500.00"), new BigDecimal("500.00"),
                100, new BigDecimal("10.00"), new BigDecimal("1.00"), true, 3));

        assertThat(r.providerNetBeforeRejection()).isEqualByComparingTo("0.00");
        assertThat(r.providerRejectedAmount()).isEqualByComparingTo("500.00");
        assertThat(r.providerContractDiscount()).isEqualByComparingTo("0.00");
        assertThat(r.insurerFinalPayment()).isEqualByComparingTo("0.00");
        assertInvariant(r);
    }

    @Test
    void rejectionEqualsNetExactly_insurerPaysZeroWithoutChangingServiceQuantity() {
        // The engine no longer tracks quantity outcomes at all (finance-02.1);
        // this proves a full-value rejection produces a clean zero payment
        // and the invariant still balances, without any approvedQuantity
        // side effect to reason about.
        Result r = engine.evaluate(limited(
                new BigDecimal("300.00"), new BigDecimal("300.00"), new BigDecimal("300.00"),
                100, BigDecimal.ZERO, new BigDecimal("300.00"), false, 5));

        assertThat(r.insurerFinalPayment()).isEqualByComparingTo("0.00");
        assertThat(r.providerRejectedAmount()).isEqualByComparingTo("300.00");
        assertInvariant(r);
    }

    // ── S37 "Contract price excess" ─────────────────────────────────────────

    @Test
    void contractPriceExcess_neverEntersTheInsuranceCalculation() {
        Result r = engine.evaluate(limited(
                new BigDecimal("1200.00"), new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                100, BigDecimal.ZERO, BigDecimal.ZERO, false, 1));

        assertThat(r.contractualPriceExcess()).isEqualByComparingTo("200.00");
        assertThat(r.settlementBase()).isEqualByComparingTo("1000.00");
        assertInvariant(r);
    }

    @Test
    void providerRequestsLessThanContractualPrice_settlementBaseIsTheLowerRequestedAmount() {
        Result r = engine.evaluate(limited(
                new BigDecimal("800.00"), new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                100, BigDecimal.ZERO, BigDecimal.ZERO, false, 1));

        assertThat(r.contractualPriceExcess()).isEqualByComparingTo("0.00");
        assertThat(r.settlementBase()).isEqualByComparingTo("800.00");
        assertThat(r.insurerFinalPayment()).isEqualByComparingTo("800.00");
        assertInvariant(r);
    }

    // ── LimitMode.UNLIMITED (finance-02.1) ───────────────────────────────────

    @Test
    void unlimited_noExcessAndNoPhantomBalance() {
        Result r = engine.evaluate(unlimited(
                new BigDecimal("5000.00"), new BigDecimal("5000.00"),
                80, new BigDecimal("10.00"), BigDecimal.ZERO, false, 1));

        assertThat(r.limitMode()).isEqualTo(LimitMode.UNLIMITED);
        assertThat(r.insideLimit()).isEqualByComparingTo(r.settlementBase());
        assertThat(r.patientLimitExcess()).isEqualByComparingTo("0.00");
        assertThat(r.limitConsumption()).isNull();
        assertThat(r.bindingAvailableLimit()).isNull();
        assertThat(r.bindingRemainingLimit()).isNull();
        assertInvariant(r);
    }

    // ── S5 no-negative-balance guarantee ────────────────────────────────────

    @Test
    void zeroAvailableLimit_wholeSettlementBaseBecomesPatientLimitExcessAndNothingGoesNegative() {
        Result r = engine.evaluate(limited(
                new BigDecimal("400.00"), new BigDecimal("400.00"), BigDecimal.ZERO,
                80, BigDecimal.ZERO, BigDecimal.ZERO, false, 1));

        assertThat(r.insideLimit()).isEqualByComparingTo("0.00");
        assertThat(r.patientLimitExcess()).isEqualByComparingTo("400.00");
        assertThat(r.patientCoverageShare()).isEqualByComparingTo("0.00");
        assertThat(r.insurerGrossShare()).isEqualByComparingTo("0.00");
        assertThat(r.insurerFinalPayment()).isEqualByComparingTo("0.00");
        assertThat(r.limitConsumption()).isEqualByComparingTo("0.00");
        assertThat(r.bindingRemainingLimit()).isEqualByComparingTo("0.00");
        assertInvariant(r);
    }

    @Test
    void s28_concurrentClaimsNeverJointlyExceedTheLimit_secondCallSeesTheReducedRemainder() {
        BigDecimal limit = new BigDecimal("500.00");

        Result a = engine.evaluate(limited(
                new BigDecimal("400.00"), new BigDecimal("400.00"), limit,
                100, BigDecimal.ZERO, BigDecimal.ZERO, false, 1));
        assertThat(a.limitConsumption()).isEqualByComparingTo("400.00");
        assertThat(a.bindingRemainingLimit()).isEqualByComparingTo("100.00");

        Result b = engine.evaluate(limited(
                new BigDecimal("400.00"), new BigDecimal("400.00"), a.bindingRemainingLimit(),
                100, BigDecimal.ZERO, BigDecimal.ZERO, false, 1));
        assertThat(b.insideLimit()).isEqualByComparingTo("100.00");
        assertThat(b.patientLimitExcess()).isEqualByComparingTo("300.00");
        assertThat(b.bindingRemainingLimit()).isEqualByComparingTo("0.00");

        assertThat(a.limitConsumption().add(b.limitConsumption()))
                .as("combined consumption must never exceed the original limit")
                .isEqualByComparingTo("500.00");
        assertInvariant(a);
        assertInvariant(b);
    }

    // ── finance-02.1: complete input validation, fail closed ─────────────────

    @Test
    void nullInputFailsClosed() {
        assertThatThrownBy(() -> engine.evaluate(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeRequestedAmountFailsClosed() {
        assertThatThrownBy(() -> engine.evaluate(limited(
                new BigDecimal("-1.00"), new BigDecimal("100.00"), new BigDecimal("100.00"),
                100, BigDecimal.ZERO, BigDecimal.ZERO, false, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroRequestedAmountFailsClosed() {
        assertThatThrownBy(() -> engine.evaluate(limited(
                BigDecimal.ZERO, new BigDecimal("100.00"), new BigDecimal("100.00"),
                100, BigDecimal.ZERO, BigDecimal.ZERO, false, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingContractualPriceFailsClosedWithCONTRACT_PRICE_NOT_FOUND() {
        assertThatThrownBy(() -> engine.evaluate(limited(
                new BigDecimal("100.00"), null, new BigDecimal("100.00"),
                100, BigDecimal.ZERO, BigDecimal.ZERO, false, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CONTRACT_PRICE_NOT_FOUND");
    }

    @Test
    void zeroOrNegativeContractualPriceFailsClosed() {
        assertThatThrownBy(() -> engine.evaluate(limited(
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"),
                100, BigDecimal.ZERO, BigDecimal.ZERO, false, 1)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> engine.evaluate(limited(
                new BigDecimal("100.00"), new BigDecimal("-50.00"), new BigDecimal("100.00"),
                100, BigDecimal.ZERO, BigDecimal.ZERO, false, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeDiscountPercentFailsClosed() {
        assertThatThrownBy(() -> engine.evaluate(limited(
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"),
                100, new BigDecimal("-1.00"), BigDecimal.ZERO, false, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void discountPercentAbove100FailsClosed() {
        assertThatThrownBy(() -> engine.evaluate(limited(
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"),
                100, new BigDecimal("101.00"), BigDecimal.ZERO, false, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeRejectedAmountFailsClosed_neverSilentlyCorrected() {
        assertThatThrownBy(() -> engine.evaluate(limited(
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"),
                100, BigDecimal.ZERO, new BigDecimal("-1.00"), false, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroOrNegativeQuantityFailsClosed() {
        assertThatThrownBy(() -> engine.evaluate(limited(
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"),
                100, BigDecimal.ZERO, BigDecimal.ZERO, false, 0)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> engine.evaluate(limited(
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"),
                100, BigDecimal.ZERO, BigDecimal.ZERO, false, -1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeBindingAvailableLimitFailsClosedWhenLimited() {
        assertThatThrownBy(() -> engine.evaluate(limited(
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("-1.00"),
                100, BigDecimal.ZERO, BigDecimal.ZERO, false, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingBindingAvailableLimitFailsClosedWhenLimited() {
        assertThatThrownBy(() -> engine.evaluate(new Input(
                new BigDecimal("100.00"), new BigDecimal("100.00"), LimitMode.LIMITED, null,
                100, BigDecimal.ZERO, BigDecimal.ZERO, false, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── S2 / S24 fail-closed guards ──────────────────────────────────────────

    @Test
    void s2_zeroCoveragePercentFailsClosed_noSuchThingAsZeroCoverage() {
        assertThatThrownBy(() -> engine.evaluate(limited(
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"),
                0, BigDecimal.ZERO, BigDecimal.ZERO, false, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void s2_coveragePercentAbove100FailsClosed() {
        assertThatThrownBy(() -> engine.evaluate(limited(
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"),
                101, BigDecimal.ZERO, BigDecimal.ZERO, false, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Result formatting ────────────────────────────────────────────────────

    @Test
    void everyMonetaryResultFieldHasExactlyTwoDecimalPlaces() {
        Result r = engine.evaluate(limited(
                new BigDecimal("1200"), new BigDecimal("1000"), new BigDecimal("600"),
                80, new BigDecimal("10"), new BigDecimal("50"), false, 1));

        assertThat(r.requestedAmount().scale()).isEqualTo(2);
        assertThat(r.contractualPrice().scale()).isEqualTo(2);
        assertThat(r.contractualPriceExcess().scale()).isEqualTo(2);
        assertThat(r.settlementBase().scale()).isEqualTo(2);
        assertThat(r.bindingAvailableLimit().scale()).isEqualTo(2);
        assertThat(r.insideLimit().scale()).isEqualTo(2);
        assertThat(r.patientLimitExcess().scale()).isEqualTo(2);
        assertThat(r.limitConsumption().scale()).isEqualTo(2);
        assertThat(r.bindingRemainingLimit().scale()).isEqualTo(2);
        assertThat(r.patientCoverageShare().scale()).isEqualTo(2);
        assertThat(r.patientTotalResponsibility().scale()).isEqualTo(2);
        assertThat(r.insurerGrossShare().scale()).isEqualTo(2);
        assertThat(r.providerContractDiscount().scale()).isEqualTo(2);
        assertThat(r.providerNetBeforeRejection().scale()).isEqualTo(2);
        assertThat(r.providerRejectedAmount().scale()).isEqualTo(2);
        assertThat(r.insurerFinalPayment().scale()).isEqualTo(2);
    }
}
