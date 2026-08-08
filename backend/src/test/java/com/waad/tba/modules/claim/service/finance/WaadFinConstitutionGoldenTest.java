package com.waad.tba.modules.claim.service.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.waad.tba.modules.claim.service.finance.WaadFinancialEngine.Input;
import com.waad.tba.modules.claim.service.finance.WaadFinancialEngine.Result;

/**
 * WAAD-FIN-1.0 Golden Scenario -- backend/docs/design/FINANCIAL_CONSTITUTION.md, S14.
 *
 * This is the single reference test for the constitution. It is not edited
 * to match whatever an engine currently returns; an engine is edited to
 * match this. It changes only when a new constitution version is issued.
 *
 * Pure unit test: no Spring, no database, no mock, sub-millisecond.
 *
 * History: this test was first written against the OLD ClaimLineFinancialEngine
 * and was RED (commit "test: WAAD-FIN-1.0 golden test, RED against the
 * current engine"), proving the old engine's maximumCompanyShare capped
 * insurerFinalPayment AFTER the patient/discount split instead of capping
 * settlementBase BEFORE it. It now exercises WaadFinancialEngine, the
 * canonical engine built to the constitution, and is GREEN.
 */
class WaadFinConstitutionGoldenTest {

    private final WaadFinancialEngine engine = new WaadFinancialEngine();

    @Test
    void goldenScenario() {
        Result r = engine.evaluate(new Input(
                new BigDecimal("1200.00"), // requestedAmount
                new BigDecimal("1000.00"), // contractualPrice
                new BigDecimal("600.00"),  // availableLimit
                80,                        // coveragePercent
                new BigDecimal("10.00"),   // providerDiscountPercent
                new BigDecimal("50.00"),   // providerRejectedAmount
                false,                     // fullyRejected
                1));

        assertThat(r.contractualPriceExcess()).isEqualByComparingTo("200.00");
        assertThat(r.settlementBase()).isEqualByComparingTo("1000.00");
        assertThat(r.limitCoveredBase()).isEqualByComparingTo("600.00");
        assertThat(r.patientLimitExcess()).isEqualByComparingTo("400.00");
        assertThat(r.patientCoverageShare()).isEqualByComparingTo("120.00");
        assertThat(r.patientTotalResponsibility()).isEqualByComparingTo("520.00");
        assertThat(r.insurerGrossShare()).isEqualByComparingTo("480.00");
        assertThat(r.providerContractDiscount()).isEqualByComparingTo("48.00");
        assertThat(r.providerNetBeforeRejection()).isEqualByComparingTo("432.00");
        assertThat(r.providerRejectedAmount()).isEqualByComparingTo("50.00");
        assertThat(r.insurerFinalPayment()).isEqualByComparingTo("382.00");
        assertThat(r.limitConsumption()).isEqualByComparingTo("600.00");
        assertThat(r.remainingLimit()).isEqualByComparingTo("0.00");

        // S13 invariant: 200 + 120 + 400 + 48 + 50 + 382 = 1200
        BigDecimal reconstructed = r.contractualPriceExcess()
                .add(r.patientCoverageShare())
                .add(r.patientLimitExcess())
                .add(r.providerContractDiscount())
                .add(r.providerRejectedAmount())
                .add(r.insurerFinalPayment());
        assertThat(reconstructed).isEqualByComparingTo(r.requestedAmount());
        assertThat(reconstructed).isEqualByComparingTo("1200.00");
    }
}
