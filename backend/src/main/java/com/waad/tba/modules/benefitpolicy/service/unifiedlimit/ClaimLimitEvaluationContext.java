package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * P1.5.1a: an isolated skeleton, not wired into any production call site.
 *
 * {@link UnifiedLimitResolver} is deliberately pure, single-line, and
 * stateless (reviewed and kept that way in P1.5.0/P1.5.1a on purpose). But a
 * real claim or batch evaluates several lines against the same buckets in
 * one call, and line 2 must see what line 1 already consumed -- otherwise
 * every line independently believes the full limit is still available and a
 * multi-line claim can exceed a ceiling that any single line, alone, would
 * have respected. This is the in-memory layer that carries that pending
 * consumption; {@code UnifiedLimitResolver} itself never learns of it.
 *
 * Today's live equivalent is {@code CoverageEngineService.BatchUsageAccumulator}.
 * This type is NOT a port of that class -- it corrects what was found while
 * designing this round (see {@code docs/finance/P1_5_1A_BATCH_AWARE_DESIGN.md}):
 * DAYS consumption is scoped per (bucket, period, SERVICE DATE) -- a claim
 * touching two different buckets, or the same bucket on two different
 * service dates, must track each independently, never collapse to one
 * claim-wide flag. AMOUNT/TIMES pending usage is recorded against EVERY
 * snapshot the line was actually approved against for that axis -- the
 * decision's own approved values (zero when refused or BLOCKED) already
 * scope this correctly; nothing is recorded just because a bucket was read
 * or considered.
 *
 * One instance is scoped to one claim/batch evaluation and discarded after.
 */
public final class ClaimLimitEvaluationContext {

    /**
     * Deliberately NOT including countingMethod, unlike today's
     * AccumulatorKey -- see the design doc §2.1: countingMethod is a fixed
     * property of the bucket for the lifetime of one evaluation, so keying
     * on it separately can never produce two different pending entries for
     * the same (bucket, period). {@code bucketId} may be {@code null} -- the
     * synthetic policy-general ceiling has no bucket row (V174) but is
     * still shared pending state across every line of the claim.
     */
    public record PendingKey(Long bucketId, LocalDate periodStart, LocalDate periodEnd) {
    }

    /**
     * DAYS is never a running total (design doc §1.2/§2.3): a claim with two
     * lines on the same bucket AND the same service date spends one day
     * total, but a second bucket, or the same bucket on a different service
     * date, is a completely independent question. The key carries all three
     * dimensions so neither collapses into the other.
     */
    public record DayConsumptionKey(Long bucketId, LocalDate periodStart, LocalDate periodEnd, LocalDate serviceDate) {
    }

    private static final class PendingUsage {
        BigDecimal pendingAmount = BigDecimal.ZERO;
        int pendingTimes = 0;
    }

    private final Map<PendingKey, PendingUsage> pending = new HashMap<>();
    private final Set<DayConsumptionKey> consumedDays = new HashSet<>();

    private static PendingKey keyOf(BucketLimitSnapshot snapshot) {
        return new PendingKey(snapshot.bucketId(), snapshot.periodStart(), snapshot.periodEnd());
    }

    /**
     * Whether an earlier line in THIS SAME context already spent this exact
     * (bucket, period, service date)'s one day. Callers use this, together
     * with the base snapshot's own DB-sourced "already used" state, to
     * decide whether the NEXT line should ask for a day at all -- mirroring
     * {@code !limit.serviceDayAlreadyUsed() && !acc.addedDay} exactly, but
     * scoped correctly per bucket and per date rather than claim-wide.
     */
    public boolean dayAlreadyConsumedThisBatch(Long bucketId, LocalDate periodStart, LocalDate periodEnd,
            LocalDate serviceDate) {
        return consumedDays.contains(new DayConsumptionKey(bucketId, periodStart, periodEnd, serviceDate));
    }

    /**
     * Returns a new list with every AMOUNT/TIMES snapshot's
     * {@code committed}/{@code remaining} reduced by whatever this context
     * has recorded so far for that (bucket, period). Never mutates
     * {@code base}. DAYS snapshots pass through unchanged -- that axis is
     * consulted via {@link #dayAlreadyConsumedThisBatch}, never adjusted
     * here (design doc §2.3: a one-shot flag, not a subtractable total).
     */
    public List<BucketLimitSnapshot> adjustForNextLine(List<BucketLimitSnapshot> base) {
        List<BucketLimitSnapshot> adjusted = new ArrayList<>(base.size());
        for (BucketLimitSnapshot snapshot : base) {
            PendingUsage usage = pending.get(keyOf(snapshot));
            if (usage == null) {
                adjusted.add(snapshot);
                continue;
            }
            switch (snapshot.limitType()) {
                case AMOUNT -> adjusted.add(withCommittedAndRemaining(snapshot,
                        snapshot.committed().add(usage.pendingAmount),
                        snapshot.remaining().subtract(usage.pendingAmount)));
                case TIMES -> adjusted.add(withCommittedAndRemaining(snapshot,
                        snapshot.committed().add(BigDecimal.valueOf(usage.pendingTimes)),
                        snapshot.remaining().subtract(BigDecimal.valueOf(usage.pendingTimes))));
                case DAYS -> adjusted.add(snapshot);
            }
        }
        return adjusted;
    }

    /**
     * Records what a resolved line actually consumed. Every snapshot the
     * line was evaluated against is a legitimate, independent ceiling for
     * the SAME approved transaction (a shared/parent bucket included, per
     * design doc §2.2) -- so every one of them receives the decision's own
     * actually-approved value for its axis. This is not "every bucket that
     * was read or checked": {@code decision.approvedQuantity()} /
     * {@code decision.bindingAvailableAmount()} / {@code decision.approvedDays()}
     * are already zero (or null) whenever nothing was actually approved on
     * that axis -- including a BLOCKED decision, whose factory sets all
     * three to zero/null -- so a refused or blocked line adds no pending
     * consumption anywhere, without this method needing its own refusal
     * check.
     *
     * @param lineSnapshots the exact (already-adjusted) snapshot list this
     *                      line was resolved against
     * @param decision      that line's resolved decision
     * @param serviceDate   this line's service date, for the DAYS key
     */
    public void recordLineConsumption(List<BucketLimitSnapshot> lineSnapshots, UnifiedLimitDecision decision,
            LocalDate serviceDate) {
        for (BucketLimitSnapshot snapshot : lineSnapshots) {
            switch (snapshot.limitType()) {
                case AMOUNT -> {
                    if (decision.bindingAvailableAmount() != null
                            && decision.bindingAvailableAmount().signum() > 0) {
                        PendingUsage usage = pending.computeIfAbsent(keyOf(snapshot), k -> new PendingUsage());
                        usage.pendingAmount = usage.pendingAmount.add(decision.bindingAvailableAmount());
                    }
                }
                case TIMES -> {
                    if (decision.approvedQuantity() > 0) {
                        PendingUsage usage = pending.computeIfAbsent(keyOf(snapshot), k -> new PendingUsage());
                        usage.pendingTimes += decision.approvedQuantity();
                    }
                }
                case DAYS -> {
                    if (decision.approvedDays() > 0) {
                        consumedDays.add(new DayConsumptionKey(
                                snapshot.bucketId(), snapshot.periodStart(), snapshot.periodEnd(), serviceDate));
                    }
                }
            }
        }
    }

    private static BucketLimitSnapshot withCommittedAndRemaining(
            BucketLimitSnapshot snapshot, BigDecimal committed, BigDecimal remaining) {
        return new BucketLimitSnapshot(snapshot.bucketId(), snapshot.owningPolicyId(), snapshot.limitType(),
                snapshot.configured(), committed, snapshot.activeReserved(), remaining,
                snapshot.periodStart(), snapshot.periodEnd());
    }
}
