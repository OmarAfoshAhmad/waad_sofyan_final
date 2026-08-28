package com.waad.tba.modules.benefitpolicy.repository;

import java.math.BigDecimal;

/**
 * A policy's monetary ceiling, without the policy.
 *
 * Exists so a caller that needs nothing but the ceiling does not load the
 * entity to get it. BenefitPolicy carries an EAGER element collection
 * (excludedCategoryCodes), so fetching N policies as entities costs N extra
 * selects -- which turns a bulk read for a page of members back into the
 * per-row cost that bulk read was written to remove.
 */
public record PolicyAnnualLimit(Long policyId, BigDecimal annualLimit) {
}
