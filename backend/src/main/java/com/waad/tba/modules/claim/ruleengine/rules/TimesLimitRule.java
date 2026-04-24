package com.waad.tba.modules.claim.ruleengine.rules;

import com.waad.tba.modules.claim.ruleengine.entity.ClaimCoverageRule;
import com.waad.tba.modules.claim.ruleengine.model.RuleContext;
import com.waad.tba.modules.claim.ruleengine.model.RuleResult;
import com.waad.tba.modules.claim.ruleengine.model.RuleType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TimesLimitRule implements FinancialRule {

    @Override
    public RuleType supportedType() {
        return RuleType.TIMES_LIMIT_RULE;
    }

    @Override
    public RuleResult evaluate(RuleContext context, ClaimCoverageRule rule, Map<String, Object> configuration) {
        Integer timesLimit = context.getTimesLimit();
        if (timesLimit == null) {
            return RuleResult.skip("Times limit is not configured");
        }

        if (context.getUsedTimes() < timesLimit) {
            return RuleResult.pass("Times usage within limit");
        }

        Map<String, Object> modified = new LinkedHashMap<>();
        modified.put("terminalRejected", true);
        modified.put("rejectionReason", "TIMES_LIMIT_EXCEEDED");
        modified.put("coveredAmount", BigDecimal.ZERO.setScale(RuleContext.MONEY_SCALE, RuleContext.MONEY_ROUNDING));
        modified.put("patientShare", context.getRequestedAmount());

        return RuleResult.reject("Times limit exceeded", modified);
    }
}
