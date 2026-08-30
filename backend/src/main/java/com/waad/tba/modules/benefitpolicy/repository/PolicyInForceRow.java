package com.waad.tba.modules.benefitpolicy.repository;

import java.time.LocalDate;

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;

/**
 * Whether a policy was in force on a date, and whose it was.
 *
 * A projection rather than the entity for the same reason
 * {@link PolicyAnnualLimit} is one: BenefitPolicy carries an EAGER element
 * collection, so loading N policies as entities costs N extra selects.
 *
 * Repeats {@code BenefitPolicy.isEffectiveOn}'s rule rather than calling it,
 * which is a duplication worth naming: the rule is three comparisons, and the
 * alternative is loading the entity this projection exists to avoid. The two
 * are pinned together by test, not by hope.
 */
public record PolicyInForceRow(
        Long policyId,
        BenefitPolicyStatus status,
        LocalDate startDate,
        LocalDate endDate,
        Long employerId) {

    /** Mirrors {@code BenefitPolicy.isEffectiveOn(date)} exactly. */
    public boolean isInForceOn(LocalDate date) {
        if (status != BenefitPolicyStatus.ACTIVE) {
            return false;
        }
        return startDate != null && endDate != null
                && !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    /**
     * The date-range half of "in force" only -- mirrors
     * {@code BenefitPolicy.coversDate(date)}. Deliberately excludes status:
     * whether the policy was ACTIVE on {@code date} is a dated question
     * answered by {@code BenefitPolicyStatusHistory}, not by this
     * projection's (current) status column.
     */
    public boolean coversDate(LocalDate date) {
        return startDate != null && endDate != null
                && !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
