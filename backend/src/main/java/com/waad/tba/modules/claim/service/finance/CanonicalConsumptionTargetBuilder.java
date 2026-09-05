package com.waad.tba.modules.claim.service.finance;

import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.BucketLimitSnapshot;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.CanonicalConsumptionTarget;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ResolvedLimitDescriptor;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ResolvedLimitItem;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitDecision;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P1.11.1: turns {@code UnifiedLimitDecision.consumptionTargets()} (numeric,
 * per-axis, P1.3-pure) plus {@code WaadFinancialEngine.Result} (the ONE
 * money authority, P1.6) plus each bucket's {@link ResolvedLimitDescriptor}
 * (P1.6.x) into complete, per-bucket {@link CanonicalConsumptionTarget}
 * instructions -- one merged AMOUNT+TIMES+DAYS row per real bucket or the
 * synthetic POLICY_GENERAL ceiling, matching exactly the shape
 * {@code BenefitConsumptionEntryWriter.appendClaimCommit} already writes
 * (one movement, both dimensions, never split).
 *
 * Deliberately pure and stateless: no repository, no entity, no Spring
 * bean. {@link #occurrencesFor} is the ONE place
 * {@code decision.approvedQuantity()} is converted into what a given
 * counting method actually counts as -- lifted here from
 * {@code TimesLimitEvaluator} (used only by the ledger's own PER_VISIT/
 * PER_DAY "already counted" bookkeeping, which is a SEPARATE, batch-only
 * concern this method does not need: Save-A's own
 * {@code CoverageEngineService.requestedQuantity()} already gates a
 * PER_VISIT/PER_DAY bucket's REQUEST to at most 1 per batch before the
 * resolver ever runs, so {@code decision.approvedQuantity()} for such a
 * bucket is already exactly 0 or 1 -- computing "occurrences" from it a
 * second time with a fresh "already counted" set is a no-op by
 * construction, never a second gate).
 */
public final class CanonicalConsumptionTargetBuilder {
    private CanonicalConsumptionTargetBuilder() {
    }

    /**
     * @throws IllegalStateException if a consumption target's limitKey has
     *         no matching descriptor in {@code items} -- a caller defect
     *         (descriptors must be captured in the SAME resolution pass
     *         that produced the decision), never silently skipped.
     */
    public static List<CanonicalConsumptionTarget> build(UnifiedLimitDecision decision,
            WaadFinancialEngine.Result financial, List<ResolvedLimitItem> items, LocalDate serviceDate) {
        Map<String, ResolvedLimitDescriptor> descriptorByKey = new LinkedHashMap<>();
        for (ResolvedLimitItem item : items) {
            descriptorByKey.putIfAbsent(item.descriptor().limitKey(), item.descriptor());
        }

        LinkedHashMap<String, Accumulator> byKey = new LinkedHashMap<>();
        for (BucketLimitSnapshot target : decision.consumptionTargets()) {
            String key = limitKey(target);
            Accumulator acc = byKey.computeIfAbsent(key,
                    k -> new Accumulator(target.bucketId(), target.periodStart(), target.periodEnd()));
            switch (target.limitType()) {
                case AMOUNT -> acc.amountToConsume = financial.limitConsumption();
                case TIMES -> acc.timesToConsume = occurrencesFor(target.countingMethod(), decision.approvedQuantity());
                case DAYS -> acc.consumeDay = decision.approvedDays() > 0;
            }
        }

        List<CanonicalConsumptionTarget> result = new ArrayList<>();
        for (Map.Entry<String, Accumulator> entry : byKey.entrySet()) {
            String key = entry.getKey();
            Accumulator acc = entry.getValue();
            ResolvedLimitDescriptor descriptor = descriptorByKey.get(key);
            if (descriptor == null) {
                throw new IllegalStateException("CANONICAL_CONSUMPTION_TARGET_MISSING_DESCRIPTOR: limitKey=" + key);
            }
            result.add(new CanonicalConsumptionTarget(key, acc.bucketId, acc.amountToConsume, acc.timesToConsume,
                    acc.consumeDay, serviceDate, acc.periodStart, acc.periodEnd, descriptor));
        }
        return List.copyOf(result);
    }

    private static String limitKey(BucketLimitSnapshot target) {
        return target.bucketId() == null
                ? ResolvedLimitDescriptor.policyGeneralKey(target.owningPolicyId())
                : ResolvedLimitDescriptor.bucketKey(target.bucketId());
    }

    /**
     * EACH_UNIT divides; every other method is indivisible and counts as
     * exactly one occurrence for the whole decision, whatever the approved
     * quantity's raw number is (a divisible-looking 2 approved units on an
     * EACH_LINE bucket is still one line, one occurrence).
     */
    private static Integer occurrencesFor(CountingMethod method, int approvedQuantity) {
        if (approvedQuantity <= 0) {
            return 0;
        }
        return switch (method) {
            case EACH_UNIT -> approvedQuantity;
            case EACH_LINE, PER_VISIT, PER_DAY -> 1;
        };
    }

    private static final class Accumulator {
        final Long bucketId;
        final LocalDate periodStart;
        final LocalDate periodEnd;
        BigDecimal amountToConsume;
        Integer timesToConsume;
        boolean consumeDay;

        Accumulator(Long bucketId, LocalDate periodStart, LocalDate periodEnd) {
            this.bucketId = bucketId;
            this.periodStart = periodStart;
            this.periodEnd = periodEnd;
        }
    }
}
