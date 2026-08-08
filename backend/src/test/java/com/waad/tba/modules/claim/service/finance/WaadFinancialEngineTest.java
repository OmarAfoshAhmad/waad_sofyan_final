package com.waad.tba.modules.claim.service.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.waad.tba.modules.claim.service.finance.WaadFinancialEngine.Input;
import com.waad.tba.modules.claim.service.finance.WaadFinancialEngine.Result;

/**
 * Matrix coverage for WaadFinancialEngine against
 * backend/docs/design/FINANCIAL_CONSTITUTION.md. Every test cites the
 * section it proves. See WaadFinConstitutionGoldenTest for the single
 * reference scenario (S14); this file covers the surrounding rules the
 * golden scenario alone does not exercise.
 */
class WaadFinancialEngineTest {

    private final WaadFinancialEngine engine = new WaadFinancialEngine();

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
        // Service 1000, limit 1000, 80/20 -> patient 200, gross 800,
        // limitConsumption 1000.
        Result r = engine.evaluate(new Input(
                new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                80, BigDecimal.ZERO, BigDecimal.ZERO, false, 1));

        assertThat(r.patientCoverageShare()).isEqualByComparingTo("200.00");
        assertThat(r.patientLimitExcess()).isEqualByComparingTo("0.00");
        assertThat(r.insurerGrossShare()).isEqualByComparingTo("800.00");
        assertThat(r.limitConsumption()).isEqualByComparingTo("1000.00");
        assertThat(r.remainingLimit()).isEqualByComparingTo("0.00");
        assertInvariant(r);
    }

    // ── S37 "Partial remaining limit" ───────────────────────────────────────

    @Test
    void partialRemainingLimit_excessFallsEntirelyOnThePatient() {
        // Service 400, limit 200, 80/20 -> covered 200, patient coverage 40,
        // limit excess 200, patient total 240, gross 160, remaining 0.
        Result r = engine.evaluate(new Input(
                new BigDecimal("400.00"), new BigDecimal("400.00"), new BigDecimal("200.00"),
                80, BigDecimal.ZERO, BigDecimal.ZERO, false, 1));

        assertThat(r.limitCoveredBase()).isEqualByComparingTo("200.00");
        assertThat(r.patientCoverageShare()).isEqualByComparingTo("40.00");
        assertThat(r.patientLimitExcess()).isEqualByComparingTo("200.00");
        assertThat(r.patientTotalResponsibility()).isEqualByComparingTo("240.00");
        assertThat(r.insurerGrossShare()).isEqualByComparingTo("160.00");
        assertThat(r.remainingLimit()).isEqualByComparingTo("0.00");
        assertInvariant(r);
    }

    // ── S37 "Provider discount" ─────────────────────────────────────────────

    @Test
    void providerDiscount_appliesToInsurerGrossShareOnly() {
        // 1000, 80/20, 10% discount -> patient 200, gross 800, discount 80,
        // net 720, limitConsumption 1000 (limit is ample here).
        Result r = engine.evaluate(new Input(
                new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                80, new BigDecimal("10.00"), BigDecimal.ZERO, false, 1));

        assertThat(r.patientCoverageShare()).isEqualByComparingTo("200.00");
        assertThat(r.insurerGrossShare()).isEqualByComparingTo("800.00");
        assertThat(r.providerContractDiscount()).isEqualByComparingTo("80.00");
        assertThat(r.providerNetBeforeRejection()).isEqualByComparingTo("720.00");
        assertThat(r.insurerFinalPayment()).isEqualByComparingTo("720.00");
        assertThat(r.limitConsumption()).isEqualByComparingTo("1000.00");
        assertInvariant(r);
    }

    @Test
    void s9_discountNeverReducesPatientLimitExcess_patientBenefitsFromContractualPriceOnly() {
        // requested=1200, contract=1000, limit=0 -> the ENTIRE settlementBase
        // (1000) is patientLimitExcess, and a 10% provider discount must have
        // zero effect on what the patient owes. The patient never pays the
        // provider's raw requested price (1200), only the contractual one
        // (1000) -- but gets no benefit from the insurer's extra discount.
        Result withDiscount = engine.evaluate(new Input(
                new BigDecimal("1200.00"), new BigDecimal("1000.00"), BigDecimal.ZERO,
                80, new BigDecimal("10.00"), BigDecimal.ZERO, false, 1));
        Result withoutDiscount = engine.evaluate(new Input(
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
    void providerRejection_subtractsFromNetAfterDiscountAndNeverTouchesThePatient() {
        // Previous net 720 (1000, 80/20, 10% discount), rejection 100 ->
        // insurerFinalPayment 620, patient remains 200, limitConsumption stays 1000.
        Result r = engine.evaluate(new Input(
                new BigDecimal("1000.00"), new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                80, new BigDecimal("10.00"), new BigDecimal("100.00"), false, 1));

        assertThat(r.providerNetBeforeRejection()).isEqualByComparingTo("720.00");
        assertThat(r.insurerFinalPayment()).isEqualByComparingTo("620.00");
        assertThat(r.patientCoverageShare()).isEqualByComparingTo("200.00");
        assertThat(r.patientLimitExcess()).isEqualByComparingTo("0.00");
        assertThat(r.limitConsumption()).isEqualByComparingTo("1000.00");
        assertInvariant(r);
    }

    @Test
    void s11_rejectionExceedingNetBeforeRejectionFailsClosed() {
        // Previous net = 100 (100% coverage, no discount, ample limit).
        // A rejection of 150 must fail closed, not clamp silently.
        assertThatThrownBy(() -> engine.evaluate(new Input(
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"),
                100, BigDecimal.ZERO, new BigDecimal("150.00"), false, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fullyRejected_usesProviderNetBeforeRejectionRegardlessOfProviderRejectedAmount() {
        Result r = engine.evaluate(new Input(
                new BigDecimal("500.00"), new BigDecimal("500.00"), new BigDecimal("500.00"),
                100, new BigDecimal("10.00"), new BigDecimal("1.00"), true, 3));

        // net before rejection = 450 (10% discount on 500); fully rejected ->
        // insurerFinalPayment = 0, providerRejectedAmount reported = 450, not 1.
        assertThat(r.providerNetBeforeRejection()).isEqualByComparingTo("450.00");
        assertThat(r.providerRejectedAmount()).isEqualByComparingTo("450.00");
        assertThat(r.insurerFinalPayment()).isEqualByComparingTo("0.00");
        assertThat(r.approvedQuantity()).isEqualTo(0);
        assertInvariant(r);
    }

    // ── S37 "Contract price excess" ─────────────────────────────────────────

    @Test
    void contractPriceExcess_neverEntersTheInsuranceCalculation() {
        Result r = engine.evaluate(new Input(
                new BigDecimal("1200.00"), new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                100, BigDecimal.ZERO, BigDecimal.ZERO, false, 1));

        assertThat(r.contractualPriceExcess()).isEqualByComparingTo("200.00");
        assertThat(r.settlementBase()).isEqualByComparingTo("1000.00");
        assertInvariant(r);
    }

    @Test
    void providerRequestsLessThanContractualPrice_settlementBaseIsTheLowerRequestedAmount() {
        // S12/scenario 6: never pay or consume more than what was actually
        // requested, even if the contract would have allowed more.
        Result r = engine.evaluate(new Input(
                new BigDecimal("800.00"), new BigDecimal("1000.00"), new BigDecimal("1000.00"),
                100, BigDecimal.ZERO, BigDecimal.ZERO, false, 1));

        assertThat(r.contractualPriceExcess()).isEqualByComparingTo("0.00");
        assertThat(r.settlementBase()).isEqualByComparingTo("800.00");
        assertThat(r.insurerFinalPayment()).isEqualByComparingTo("800.00");
        assertInvariant(r);
    }

    // ── S2 / S24 fail-closed guards ──────────────────────────────────────────

    @Test
    void s2_zeroCoveragePercentFailsClosed_noSuchThingAsZeroCoverage() {
        assertThatThrownBy(() -> engine.evaluate(new Input(
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"),
                0, BigDecimal.ZERO, BigDecimal.ZERO, false, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void s2_coveragePercentAbove100FailsClosed() {
        assertThatThrownBy(() -> engine.evaluate(new Input(
                new BigDecimal("100.00"), new BigDecimal("100.00"), new BigDecimal("100.00"),
                101, BigDecimal.ZERO, BigDecimal.ZERO, false, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── S5 no-negative-balance guarantee ────────────────────────────────────

    @Test
    void zeroAvailableLimit_wholeSettlementBaseBecomesPatientLimitExcessAndNothingGoesNegative() {
        Result r = engine.evaluate(new Input(
                new BigDecimal("400.00"), new BigDecimal("400.00"), BigDecimal.ZERO,
                80, BigDecimal.ZERO, BigDecimal.ZERO, false, 1));

        assertThat(r.limitCoveredBase()).isEqualByComparingTo("0.00");
        assertThat(r.patientLimitExcess()).isEqualByComparingTo("400.00");
        assertThat(r.patientCoverageShare()).isEqualByComparingTo("0.00");
        assertThat(r.insurerGrossShare()).isEqualByComparingTo("0.00");
        assertThat(r.insurerFinalPayment()).isEqualByComparingTo("0.00");
        assertThat(r.limitConsumption()).isEqualByComparingTo("0.00");
        assertThat(r.remainingLimit()).isEqualByComparingTo("0.00");
        assertInvariant(r);
    }

    @Test
    void s28_concurrentClaimsNeverJointlyExceedTheLimit_secondCallSeesTheReducedRemainder() {
        // The engine itself is stateless -- this test proves the CALLER'S
        // pattern (feed remainingLimit from claim A into claim B's
        // availableLimit) produces the constitution's exact concurrency
        // example: limit 500, A=400 and B=400 must not both fit.
        BigDecimal limit = new BigDecimal("500.00");

        Result a = engine.evaluate(new Input(
                new BigDecimal("400.00"), new BigDecimal("400.00"), limit,
                100, BigDecimal.ZERO, BigDecimal.ZERO, false, 1));
        assertThat(a.limitConsumption()).isEqualByComparingTo("400.00");
        assertThat(a.remainingLimit()).isEqualByComparingTo("100.00");

        Result b = engine.evaluate(new Input(
                new BigDecimal("400.00"), new BigDecimal("400.00"), a.remainingLimit(),
                100, BigDecimal.ZERO, BigDecimal.ZERO, false, 1));
        assertThat(b.limitCoveredBase()).isEqualByComparingTo("100.00");
        assertThat(b.patientLimitExcess()).isEqualByComparingTo("300.00");
        assertThat(b.remainingLimit()).isEqualByComparingTo("0.00");

        assertThat(a.limitConsumption().add(b.limitConsumption()))
                .as("combined consumption must never exceed the original limit")
                .isEqualByComparingTo("500.00");
        assertInvariant(a);
        assertInvariant(b);
    }
}
