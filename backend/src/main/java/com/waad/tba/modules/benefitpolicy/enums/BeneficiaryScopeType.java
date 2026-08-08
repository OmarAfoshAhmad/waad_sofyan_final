package com.waad.tba.modules.benefitpolicy.enums;

/**
 * Who shares consumption of a limit. FAMILY is defined here but not yet
 * usable -- V147's CHECK constraints restrict both benefit_limit_buckets and
 * claim_line_limit_snapshots to MEMBER only until the family-sharing policy
 * (aggregation rules, which family members count, reservation semantics) is
 * actually built. Do not relax those CHECK constraints without that policy.
 */
public enum BeneficiaryScopeType {
    MEMBER,
    FAMILY
}
