package com.waad.tba.modules.eligibility.rules;

import com.waad.tba.modules.eligibility.domain.EligibilityContext;
import com.waad.tba.modules.eligibility.domain.EligibilityReason;
import com.waad.tba.modules.eligibility.domain.EligibilityRule;
import com.waad.tba.modules.eligibility.domain.RuleResult;
import com.waad.tba.modules.member.entity.Member;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Rule: Waiting Period
 * Phase E1 - Eligibility Engine
 * 
 * Validates that the member has satisfied the waiting period.
 * Uses BenefitPolicy.defaultWaitingPeriodDays as the canonical source.
 * 
 * This is a hard rule - failure stops evaluation.
 * 
 * Priority: 70
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Component
@Order(70)
public class WaitingPeriodRule implements EligibilityRule {

    @Override
    public String getRuleCode() {
        return "WAITING_PERIOD";
    }

    @Override
    public String getNameAr() {
        return "التحقق من فترة الانتظار";
    }

    @Override
    public int getPriority() {
        return 70;
    }

    @Override
    public boolean isHardRule() {
        return true;
    }

    @Override
    public boolean isApplicable(EligibilityContext context) {
        // Check if any policy has a waiting period configured
        Integer waitingPeriod = context.getEffectiveWaitingPeriodDays();
        
        // Skip if no waiting period or waiting period is 0
        return waitingPeriod != null && waitingPeriod > 0;
    }

    @Override
    public RuleResult evaluate(EligibilityContext context) {
        Member member = context.getMember();
        LocalDate serviceDate = context.getServiceDate();

        // Get waiting period from BenefitPolicy (canonical) or Policy (legacy)
        Integer waitingPeriodDays = context.getEffectiveWaitingPeriodDays();
        if (waitingPeriodDays == null || waitingPeriodDays <= 0) {
            return RuleResult.pass("No waiting period configured");
        }

        // Get member's enrollment/start date
        LocalDate enrollmentDate = member.getStartDate();
        if (enrollmentDate == null) {
            // If no start date, use join date
            enrollmentDate = member.getJoinDate();
        }

        if (enrollmentDate == null) {
            // Cannot determine enrollment date - pass with note
            return RuleResult.pass("Member enrollment date not set, skipping waiting period check");
        }

        // Calculate days since enrollment
        long daysSinceEnrollment = context.getDaysSinceEnrollment();

        if (daysSinceEnrollment < 0) {
            // Service date is before enrollment - this is an error
            return RuleResult.fail(
                EligibilityReason.SERVICE_DATE_BEFORE_COVERAGE,
                String.format(
                    "Service date (%s) is before member enrollment (%s)",
                    serviceDate,
                    enrollmentDate
                )
            );
        }

        if (daysSinceEnrollment < waitingPeriodDays) {
            return RuleResult.fail(
                EligibilityReason.WAITING_PERIOD_NOT_SATISFIED,
                String.format(
                    "Enrolled: %s, Required: %d days, Days elapsed: %d",
                    enrollmentDate,
                    waitingPeriodDays,
                    daysSinceEnrollment
                )
            );
        }

        return RuleResult.pass(
            String.format("Waiting period satisfied (%d days)", daysSinceEnrollment)
        );
    }
}
