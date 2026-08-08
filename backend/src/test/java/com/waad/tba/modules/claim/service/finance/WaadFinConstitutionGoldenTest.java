package com.waad.tba.modules.claim.service.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.waad.tba.modules.claim.service.finance.ClaimLineFinancialEngine.Input;
import com.waad.tba.modules.claim.service.finance.ClaimLineFinancialEngine.Result;

/**
 * WAAD-FIN-1.0 GOLDEN TEST -- written FIRST, against the CURRENT production
 * engine, deliberately RED. Do not edit the expected values to match what
 * the engine currently returns; the engine is what must change.
 *
 * Constitution reference: backend/docs/design/FINANCIAL_CONSTITUTION.md, S14.
 *
 * Inputs (constitution vocabulary -> mapped onto today's engine vocabulary):
 *   requestedAmount=1200, contractualPrice=1000, availableLimit=600,
 *   coveragePercent=80%, providerDiscountPercent=10%, providerRejectedAmount=50
 *
 * Constitution-mandated results (S14):
 *   contractualPriceExcess=200, settlementBase=1000, limitCoveredBase=600,
 *   patientLimitExcess=400, patientCoverageShare=120,
 *   patientTotalResponsibility=520, insurerGrossShare=480,
 *   providerContractDiscount=48, providerRejectedAmount=50,
 *   insurerFinalPayment=382, limitConsumption=600, remainingLimit=0
 *   Invariant: 200 + 400 + 120 + 48 + 50 + 382 = 1200
 *
 * Why this MUST fail today, precisely (this is the "current vs target"
 * comparison the constitution requires before any engine change):
 *
 * 1. The current engine caps maximumCompanyShare AFTER splitting patient
 *    share and applying the discount -- i.e. it treats the ceiling as a cap
 *    on insurerFinalPayment. The constitution requires the ceiling to cap
 *    settlementBase BEFORE the patient/company split ever happens. Feeding
 *    availableLimit=600 into today's maximumCompanyShare therefore produces
 *    patientShare=200 (computed against the full 1000, unaware of any
 *    limit), not patientTotalResponsibility=520 (200 limit-excess + 120
 *    coverage-share on the reduced 600 base).
 * 2. The current engine has no patientLimitExcess concept at all -- limit
 *    overflow becomes "limitExceededAmount", a component nobody yet routes
 *    to the patient.
 * 3. The current engine's discount is computed on the pre-limit provider
 *    share (800), not on insurerGrossShare derived from the post-limit
 *    limitCoveredBase (480) -- discount=80, not the constitution's 48.
 */
class WaadFinConstitutionGoldenTest {

    private final ClaimLineFinancialEngine currentEngine = new ClaimLineFinancialEngine();

    @Test
    void goldenScenario_currentEngineDoesNotYetProduceTheConstitutionResult() {
        // requestedAmount=1200, contractualPrice=1000 -> settlementBase=1000,
        // contractualPriceExcess=200. Today's engine takes the post-price-cap
        // amount directly as "grossAmount" and the excess as "priceRefusedAmount"
        // (both concepts already match the constitution's S3 exactly).
        BigDecimal settlementBase = new BigDecimal("1000.00");
        BigDecimal contractualPriceExcess = new BigDecimal("200.00");

        Result current = currentEngine.evaluate(new Input(
                settlementBase,
                80, // coveragePercent
                new BigDecimal("50.00"), // manualRefusedAmount == providerRejectedAmount
                contractualPriceExcess, // priceRefusedAmount
                BigDecimal.ZERO, // limitRefusedAmount (today's benefit-bucket concept, unused here)
                new BigDecimal("10.00"), // providerDiscountPercent
                true, // discountBeforeRejection (irrelevant post-constitution; kept true today)
                false, // fullyRejected
                1,
                new BigDecimal("600.00"))); // today's only ceiling knob: maximumCompanyShare

        // ── What the CONSTITUTION requires (target -- do not edit to match "current") ──
        assertThat(current.requestedAmount())
                .as("requestedAmount must reconstruct to 1200.00 per S3")
                .isEqualByComparingTo("1200.00");

        // patientTotalResponsibility = patientCoverageShare(120) + patientLimitExcess(400) = 520
        // Today's engine has no patientLimitExcess; its patientShare alone must
        // equal the constitution's full patientTotalResponsibility.
        assertThat(current.patientShare())
                .as("S14: patientTotalResponsibility must be 520.00 "
                        + "(patientCoverageShare 120 + patientLimitExcess 400) -- "
                        + "today's engine computes patientShare against the unclipped "
                        + "settlementBase, ignoring the limit entirely")
                .isEqualByComparingTo("520.00");

        assertThat(current.companyDiscountAmount())
                .as("S14: providerContractDiscount must be 48.00 (10% of the post-limit "
                        + "insurerGrossShare 480), not 10% of the pre-limit provider share")
                .isEqualByComparingTo("48.00");

        assertThat(current.companyShare())
                .as("S14: insurerFinalPayment must be 382.00")
                .isEqualByComparingTo("382.00");

        // limitConsumption must be 600.00 (S4: consumption is measured against
        // settlementBase, not against what the insurer ends up paying).
        BigDecimal impliedLimitConsumption = current.limitExceededAmount().signum() > 0
                ? settlementBase.subtract(current.limitExceededAmount())
                : settlementBase;
        assertThat(impliedLimitConsumption)
                .as("S14: limitConsumption must be 600.00")
                .isEqualByComparingTo("600.00");
    }
}
