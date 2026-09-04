package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

import java.math.BigDecimal;

/**
 * P1.4.2: one bucket's already-read limit data, as it applies to this
 * decision. Deliberately NOT resolved here from any repository -- this
 * skeleton is isolated by design (P1.4.0: build the capability before
 * wiring any live caller to it). A future integration step builds a list
 * of these from the unified reading of BenefitBucketLimitService +
 * ApplicableLimitResolver/EffectiveLimitResolver +
 * ApplicableCountingLimitResolver; this resolver only decides given them.
 *
 * {@code null} on a {@code configured*} field means "this bucket sets no
 * limit of that kind" (P1.3 §1.2) -- not zero.
 */
public record BucketLimitSnapshot(
        Long bucketId,
        /** The policy this bucket actually belongs to -- checked against UnifiedLimitInput.policyId (BUCKET_POLICY_MISMATCH). */
        Long owningPolicyId,

        BigDecimal amountConfigured,
        BigDecimal amountCommitted,
        BigDecimal amountReserved,
        /** Only meaningful when reservationMode == PREAUTHORIZED_CLAIM. */
        BigDecimal amountOwnActiveReservation,

        Integer timesConfigured,
        Integer timesCommitted,
        Integer timesReserved,
        Integer timesOwnActiveReservation,

        Integer daysConfigured,
        Integer daysCommitted,
        Integer daysReserved,
        Integer daysOwnActiveReservation) {
}
