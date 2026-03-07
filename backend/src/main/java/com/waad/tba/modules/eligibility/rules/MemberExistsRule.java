package com.waad.tba.modules.eligibility.rules;

import com.waad.tba.modules.eligibility.domain.EligibilityContext;
import com.waad.tba.modules.eligibility.domain.EligibilityReason;
import com.waad.tba.modules.eligibility.domain.EligibilityRule;
import com.waad.tba.modules.eligibility.domain.RuleResult;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Rule: Member Exists
 * Phase E1 - Eligibility Engine
 * 
 * Validates that the member exists in the system.
 * This is a hard rule - failure stops evaluation.
 * 
 * Priority: 10 (evaluated first)
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Component
@Order(10)
public class MemberExistsRule implements EligibilityRule {

    @Override
    public String getRuleCode() {
        return "MEMBER_EXISTS";
    }

    @Override
    public String getNameAr() {
        return "التحقق من وجود العضو";
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public boolean isHardRule() {
        return true;
    }

    @Override
    public RuleResult evaluate(EligibilityContext context) {
        if (!context.hasMember()) {
            return RuleResult.fail(
                EligibilityReason.MEMBER_NOT_FOUND,
                "Member ID: " + context.getMemberId()
            );
        }
        return RuleResult.pass();
    }
}
