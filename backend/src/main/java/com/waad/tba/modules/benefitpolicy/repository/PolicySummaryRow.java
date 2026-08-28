package com.waad.tba.modules.benefitpolicy.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;

/**
 * What a screen needs to say about a policy, without the policy.
 *
 * A projection rather than the entity because BenefitPolicy carries an EAGER
 * element collection (excludedCategoryCodes): fetching N policies as entities
 * costs N extra selects, which turns a bulk read for a page of members back
 * into the per-row cost the bulk read was written to remove.
 *
 * Distinct from {@link PolicyInForceRow}, which deliberately carries no money
 * because it feeds dated resolution -- a resolver that can see the ceiling is
 * one that can be tempted to decide with it.
 */
public record PolicySummaryRow(
        Long policyId,
        String name,
        BigDecimal annualLimit,
        LocalDate startDate,
        LocalDate endDate,
        BenefitPolicyStatus status,
        boolean active) {

    /** Mirrors {@code BenefitPolicy.isEffectiveOn(date)} exactly. */
    public boolean isInForceOn(LocalDate date) {
        if (status != BenefitPolicyStatus.ACTIVE) {
            return false;
        }
        return startDate != null && endDate != null
                && !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
