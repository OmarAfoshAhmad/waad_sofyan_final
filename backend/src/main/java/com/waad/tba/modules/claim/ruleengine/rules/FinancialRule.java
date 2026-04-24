package com.waad.tba.modules.claim.ruleengine.rules;

import com.waad.tba.modules.claim.ruleengine.entity.ClaimCoverageRule;
import com.waad.tba.modules.claim.ruleengine.model.RuleContext;
import com.waad.tba.modules.claim.ruleengine.model.RuleResult;
import com.waad.tba.modules.claim.ruleengine.model.RuleType;

import java.util.Map;

public interface FinancialRule {
    RuleType supportedType();

    RuleResult evaluate(RuleContext context, ClaimCoverageRule rule, Map<String, Object> configuration);
}
