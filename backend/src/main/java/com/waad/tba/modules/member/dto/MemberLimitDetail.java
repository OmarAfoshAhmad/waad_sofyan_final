package com.waad.tba.modules.member.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the ceiling drawer shows for one member.
 *
 * The general ceiling and the buckets are kept apart and are never summed.
 * They measure different things against different limits: one claim line can
 * map to several buckets, so adding bucket consumption to the general figure
 * counts the same money once per category it happened to fall into.
 */
public record MemberLimitDetail(
        Long memberId,
        LocalDate asOfDate,
        java.time.LocalDateTime readAt,
        CurrentGeneralLimitSummary general,
        List<BucketBalance> buckets) {

    /**
     * One bucket's balance for this member, in the same shape as the general
     * ceiling so a reader does not have to learn two layouts.
     *
     * limit is the bucket's own amount limit, which is what the decision
     * engine resolves today: ApplicableLimitResolver reads
     * {@code bucket.amountLimit} into defaultLimit, and the single
     * LimitSourceProvider passes it through unchanged. A guard test pins that
     * arrangement, so the day a second provider is added this shortcut fails
     * rather than quietly showing a limit the engine does not use.
     */
    public record BucketBalance(
            Long bucketId,
            String code,
            String name,
            String periodType,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal limit,
            BigDecimal committed,
            BigDecimal reserved,
            /** Signed. Negative means the bucket has been overspent. */
            BigDecimal actualRemaining,
            /** Signed. What may still be committed against this bucket. */
            BigDecimal reservableAvailable,
            Integer timesLimit) {
    }
}
