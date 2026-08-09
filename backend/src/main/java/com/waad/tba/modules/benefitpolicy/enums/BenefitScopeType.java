package com.waad.tba.modules.benefitpolicy.enums;

/**
 * What kind of medical benefit a limit covers -- independent of how long the
 * limit period runs ({@link LimitPeriodType}) and who shares it
 * ({@link BeneficiaryScopeType}). SERVICE/CATEGORY/GROUP are real
 * {@code BenefitLimitBucket} classifications; POLICY_GENERAL only ever
 * appears on the synthetic policy-wide ceiling entry that
 * ApplicableLimitResolver synthesizes from {@code BenefitPolicy.annualLimit}
 * -- no real bucket row may carry it (V147's CHECK constraint on
 * benefit_limit_buckets enforces this; claim_line_limit_snapshots allows it
 * because a snapshot row records the synthetic entry too).
 */
public enum BenefitScopeType {
    SERVICE,
    CATEGORY,
    GROUP,
    POLICY_GENERAL
}
