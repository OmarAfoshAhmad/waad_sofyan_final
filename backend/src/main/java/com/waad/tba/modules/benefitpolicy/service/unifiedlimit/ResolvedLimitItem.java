package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

/**
 * P1.6.x: one resolved limit, both halves together -- the numeric balance
 * {@link UnifiedLimitResolver} actually operates on, and the descriptive
 * identity {@code ClaimLimitSnapshotFactory} needs for the audit trail.
 * Pairing them in one object (rather than two parallel lists, or a lookup
 * keyed separately from each side) is what makes it structurally impossible
 * to write one bucket's numbers against another bucket's description --
 * there is no join to get wrong.
 *
 * {@code numericSnapshot().limitType()} may be AMOUNT, TIMES, or DAYS for a
 * single bucket (up to three items can share one {@code descriptor()} whose
 * {@code limitKey}/{@code bucketId} match); the descriptor itself never
 * varies by axis.
 */
public record ResolvedLimitItem(BucketLimitSnapshot numericSnapshot, ResolvedLimitDescriptor descriptor) {
}
