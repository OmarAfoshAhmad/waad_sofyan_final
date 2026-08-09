package com.waad.tba.modules.claim.service.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;

/**
 * Fails closed the moment a claim's aggregate financial fields disagree with
 * the sum of its own lines. Two call sites are required, not one:
 *
 * 1. Right after ClaimMapper aggregates the lines (proves the aggregation
 *    itself is correct at the moment it happens).
 * 2. At the approval gate, after the last step that can still touch the
 *    numbers and before the status becomes APPROVED (proves nothing touched
 *    the numbers between aggregation and approval -- historically,
 *    ClaimFinancialSnapshotService.finalizeSnapshot did exactly that).
 *
 * A guard placed only at (1) would pass even with finalizeSnapshot's defect
 * present, because the lines are correct at that instant; it is (2) that
 * actually catches a later rewrite.
 */
@Component
public class ClaimFinancialInvariantGuard {

    /** The one rounding tolerance allowed across the whole check: Money's own scale. */
    private static final BigDecimal EPSILON = new BigDecimal("0.01");

    public void assertConsistent(Claim claim) {
        List<ClaimLine> lines = claim.getLines();
        if (lines == null || lines.isEmpty()) {
            return;
        }

        BigDecimal sumCompanyShare = sum(lines, ClaimLine::getCompanyShare);
        BigDecimal sumPatientShare = sum(lines, ClaimLine::getPatientShare);
        BigDecimal sumRefusedAmount = sum(lines, ClaimLine::getRefusedAmount);

        check(claim.getId(), "approvedAmount", claim.getApprovedAmount(), sumCompanyShare);
        check(claim.getId(), "netProviderAmount", claim.getNetProviderAmount(), sumCompanyShare);
        check(claim.getId(), "patientCoPay", claim.getPatientCoPay(), sumPatientShare);
        check(claim.getId(), "refusedAmount", claim.getRefusedAmount(), sumRefusedAmount);
    }

    private void check(Long claimId, String field, BigDecimal claimValue, BigDecimal lineSum) {
        BigDecimal actual = value(claimValue);
        BigDecimal expected = value(lineSum);
        BigDecimal diff = actual.subtract(expected).abs();
        if (diff.compareTo(EPSILON) > 0) {
            throw new BusinessRuleException(String.format(
                    "تناقض مالي في المطالبة %s: Claim.%s = %s لكن مجموع البنود = %s (الفرق %s يتجاوز "
                            + "هامش التقريب المعتمد %s). العملية أُلغيت بالكامل لمنع اعتماد رقم غير صحيح.",
                    claimId, field, actual, expected, diff, EPSILON));
        }
    }

    private BigDecimal sum(List<ClaimLine> lines, Function<ClaimLine, BigDecimal> extractor) {
        return lines.stream()
                .map(extractor)
                .map(this::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal value(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
    }
}
