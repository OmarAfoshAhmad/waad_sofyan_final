package com.waad.tba.modules.eligibility.rules;

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.eligibility.domain.EligibilityContext;
import com.waad.tba.modules.eligibility.domain.EligibilityReason;
import com.waad.tba.modules.eligibility.domain.EligibilityRule;
import com.waad.tba.modules.eligibility.domain.RuleResult;
import com.waad.tba.modules.member.entity.Member;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Rule: Member Enrollment
 * Phase E1 - Eligibility Engine
 * 
 * Validates that the member is enrolled in a BenefitPolicy.
 * Checks that the member has an assigned BenefitPolicy.
 * 
 * This is a hard rule - failure stops evaluation.
 * 
 * Priority: 60
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Component
@Order(60)
public class MemberEnrollmentRule implements EligibilityRule {

    @Override
    public String getRuleCode() {
        return "MEMBER_ENROLLMENT";
    }

    @Override
    public String getNameAr() {
        return "التحقق من تسجيل العضو في الوثيقة";
    }

    @Override
    public int getPriority() {
        return 60;
    }

    @Override
    public boolean isHardRule() {
        return true;
    }

    @Override
    public boolean isApplicable(EligibilityContext context) {
        return context.hasMember();
    }

    @Override
    public RuleResult evaluate(EligibilityContext context) {
        // The engine resolved this policy for context.serviceDate. Reading the
        // member's current convenience pointer here made this rule disagree
        // with every other dated eligibility rule after a policy change.
        BenefitPolicy benefitPolicy = context.getBenefitPolicy();
        if (benefitPolicy == null) {
            return RuleResult.fail(
                EligibilityReason.MEMBER_NOT_ENROLLED,
                "Member has no BenefitPolicy assigned"
            );
        }

        // Check: BenefitPolicy is active
        if (benefitPolicy.getStatus() != BenefitPolicy.BenefitPolicyStatus.ACTIVE) {
            return RuleResult.fail(
                EligibilityReason.POLICY_INACTIVE,
                String.format(
                    "Member's BenefitPolicy: %s is not ACTIVE (status: %s)",
                    benefitPolicy.getPolicyCode(),
                    benefitPolicy.getStatus()
                )
            );
        }

        return RuleResult.pass(
            String.format("Member enrolled in BenefitPolicy: %s", benefitPolicy.getPolicyCode())
        );
    }
}
