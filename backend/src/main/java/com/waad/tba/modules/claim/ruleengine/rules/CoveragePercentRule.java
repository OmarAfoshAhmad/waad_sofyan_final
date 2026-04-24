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
public class CoveragePercentRule implements FinancialRule {

    @Override
    public RuleType supportedType() {
        return RuleType.COVERAGE_PERCENT_RULE;
    }

    @Override
    public RuleResult evaluate(RuleContext context, ClaimCoverageRule rule, Map<String, Object> configuration) {
        if (context.isTerminalRejected()) {
            return RuleResult.skip("Context already rejected");
        }

        BigDecimal percent = context.getCoveragePercent();
        if (percent == null) {
            return RuleResult.skip("Coverage percent is missing");
        }

        BigDecimal covered = context.getRequestedAmount()
                .multiply(percent)
                .divide(new BigDecimal("100"), RuleContext.MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal patient = context.getRequestedAmount().subtract(covered)
                .setScale(RuleContext.MONEY_SCALE, RoundingMode.HALF_UP);

        Map<String, Object> modified = new LinkedHashMap<>();
        modified.put("coveredAmount", covered);
        modified.put("patientShare", patient);

        return RuleResult.modify("Coverage percent applied", modified);
    }
}
