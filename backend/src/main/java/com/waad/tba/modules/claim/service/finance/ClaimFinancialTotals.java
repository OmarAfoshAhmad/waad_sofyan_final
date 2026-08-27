package com.waad.tba.modules.claim.service.finance;

import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;

/** The sole aggregation from canonical line results to claim-level totals. */
public final class ClaimFinancialTotals {
    private ClaimFinancialTotals() {}

    public static void aggregate(Claim claim) {
        List<ClaimLine> lines = claim.getLines();
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("claim requires at least one financial line");
        }
        BigDecimal requested = sum(lines, ClaimLine::getRequestedTotal);
        BigDecimal refused = sum(lines, ClaimLine::getRefusedAmount);
        BigDecimal patient = sum(lines, ClaimLine::getPatientShare);
        BigDecimal approved = sum(lines, ClaimLine::getCompanyShare);
        BigDecimal discount = sum(lines, ClaimLine::getProviderContractDiscount);

        claim.setRequestedAmount(requested);
        claim.setRefusedAmount(refused);
        claim.setApprovedAmount(approved);
        claim.setNetProviderAmount(approved);
        claim.setPatientCoPay(patient);
        claim.setCompanyDiscountAmount(discount);
        claim.setDifferenceAmount(money(requested.subtract(approved)));
    }

    /**
     * What this claim consumes against its member's annual benefit ceiling --
     * WAAD-FIN-1.0 S4's axis (settlement value inside the binding limit), not
     * {@link Claim#getApprovedAmount()}. The two are different numbers by
     * construction: a limit is capped, coverage-split, discounted, and
     * rejected on top of it before insurerFinalPayment (approvedAmount) is
     * reached. Any code that checks a claim against a benefit-limit ceiling
     * must use this, not approvedAmount -- see
     * {@link com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService#getLimitConsumedForYear}
     * for the matching "previously consumed" read.
     */
    public static BigDecimal sumLimitConsumption(Claim claim) {
        List<ClaimLine> lines = claim.getLines();
        if (lines == null || lines.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return sum(lines, ClaimLine::getLimitConsumption);
    }

    private static BigDecimal sum(List<ClaimLine> lines, Function<ClaimLine, BigDecimal> field) {
        return money(lines.stream().map(field).map(ClaimFinancialTotals::zero)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return zero(value).setScale(2, RoundingMode.HALF_UP);
    }
}
