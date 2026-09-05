package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * P1.5.0: one bucket's already-computed balance for ONE limit axis.
 *
 * {@code remaining} is computed and owned ENTIRELY by whoever builds this
 * snapshot (the future {@code BucketLimitSnapshotAdapter}, backed by
 * {@code LimitBalanceReader} for AMOUNT/TIMES -- the one live reader that
 * already sees RESERVED -- and by the days-specific read for DAYS, which
 * has no reservation concept in this product at all: no
 * {@code sumReservedDays} query exists anywhere in the codebase).
 * {@link UnifiedLimitResolver} NEVER recomputes it -- it only takes the
 * tightest {@code remaining} across buckets that configure the same axis.
 * One owner for this number, not two.
 *
 * A bucket that sets no limit of this kind simply has no row here for that
 * axis -- never a row with {@code configured=null} pretending to be a
 * limit. {@link UnifiedLimitResolver} treats "no row for this axis" as
 * "unconfigured", exactly like P1.3 §1.2 requires.
 */
public record BucketLimitSnapshot(
        Long bucketId,
        /** The policy this bucket actually belongs to -- checked against UnifiedLimitInput.policyId (BUCKET_POLICY_MISMATCH). */
        Long owningPolicyId,
        LimitAxisType limitType,
        BigDecimal configured,
        BigDecimal committed,
        BigDecimal activeReserved,
        /**
         * The canonical answer for this axis, already reflecting the
         * decision's {@link ReservationEvaluationMode} (a PREAUTHORIZED_CLAIM
         * reading returns its own hold to it here; a NORMAL reading does
         * not). The resolver consumes this number as-is.
         */
        BigDecimal remaining,
        LocalDate periodStart,
        LocalDate periodEnd) {
}
