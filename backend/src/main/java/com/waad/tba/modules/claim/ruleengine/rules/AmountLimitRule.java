package com.waad.tba.modules.claim.ruleengine.rules;

import com.waad.tba.modules.claim.ruleengine.entity.ClaimCoverageRule;
import com.waad.tba.modules.claim.ruleengine.model.RuleContext;
import com.waad.tba.modules.claim.ruleengine.model.RuleResult;
import com.waad.tba.modules.claim.ruleengine.model.RuleType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AmountLimitRule implements FinancialRule {

    @Override
    public RuleType supportedType() {
        return RuleType.AMOUNT_LIMIT_RULE;
    }

    @Override
    public RuleResult evaluate(RuleContext context, ClaimCoverageRule rule, Map<String, Object> configuration) {
        if (context.isTerminalRejected()) {
            return RuleResult.skip("Context already rejected");
        }

        if (context.getAmountLimit() == null) {
            return RuleResult.skip("Amount limit is not configured");
        }

        BigDecimal remaining = context.getAmountLimit().subtract(context.getUsedAmount())
                .setScale(RuleContext.MONEY_SCALE, RuleContext.MONEY_ROUNDING);

        BigDecimal currentCovered = context.getCoveredAmount();
        if (currentCovered == null) {
            BigDecimal percent = context.getCoveragePercent() == null ? BigDecimal.ZERO : context.getCoveragePercent();
            currentCovered = context.getRequestedAmount()
                    .multiply(percent)
                    .divide(new BigDecimal("100"), RuleContext.MONEY_SCALE, RoundingMode.HALF_UP);
        }

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            Map<String, Object> modified = new LinkedHashMap<>();
            modified.put("coveredAmount",
                    BigDecimal.ZERO.setScale(RuleContext.MONEY_SCALE, RuleContext.MONEY_ROUNDING));
            modified.put("patientShare", context.getRequestedAmount());
            modified.put("rejectionReason", "AMOUNT_LIMIT_EXCEEDED");
            return RuleResult.modify("Amount limit already exhausted", modified);
        }

        if (currentCovered.compareTo(remaining) <= 0) {
            return RuleResult.pass("Amount limit not exceeded");
        }

        BigDecimal cappedCovered = remaining.setScale(RuleContext.MONEY_SCALE, RuleContext.MONEY_ROUNDING);
        BigDecimal patientShare = context.getRequestedAmount().subtract(cappedCovered)
                .setScale(RuleContext.MONEY_SCALE, RuleContext.MONEY_ROUNDING);

        Map<String, Object> modified = new LinkedHashMap<>();
        modified.put("coveredAmount", cappedCovered);
        modified.put("patientShare", patientShare);
        modified.put("rejectionReason", "AMOUNT_LIMIT_PARTIAL_CAP");

        return RuleResult.modify("Amount limit partially exceeded", modified);
    }
}
