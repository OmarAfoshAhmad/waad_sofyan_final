package com.waad.tba.modules.benefitpolicy.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitRuleBucket;
import com.waad.tba.modules.benefitpolicy.repository.BenefitRuleBucketRepository;

import lombok.RequiredArgsConstructor;

/**
 * The OCCURRENCE ceilings that apply to a benefit rule.
 *
 * Deliberately separate from ApplicableLimitResolver, which is not defective
 * -- it feeds the monetary engine and correctly declines to invent an amount
 * for a bucket that caps only visits. Its name simply suggests a breadth it
 * never had.
 *
 * The gap that made this class necessary was in the pre-authorization path,
 * which took the monetary resolver as its ONLY source of applicable ceilings
 * and therefore never saw a count-only bucket. Claims were never exposed:
 * BenefitBucketLedgerService walks the rule's buckets itself and enforces
 * timesLimit at commit. This resolver walks them the same way, so both paths
 * see the same ceilings.
 *
 * It reads no monetary value at all.
 */
@Component
@RequiredArgsConstructor
public class ApplicableCountingLimitResolver {

    private final BenefitRuleBucketRepository ruleBucketRepository;
    private final com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository
            consumptionRepository;

    /**
     * Every bucket with an occurrence ceiling that this rule consumes,
     * including inherited parents, de-duplicated by id and in a stable order.
     *
     * Buckets that cap only DAYS are excluded rather than reported as
     * occurrences. A day limit counts distinct service dates; calling it a
     * count of visits would let a pre-authorization hold "one day" from a
     * single expected date and understate a multi-day stay.
     */
    @Transactional(readOnly = true)
    public List<BenefitLimitBucket> resolve(Long benefitRuleId) {
        if (benefitRuleId == null) {
            return List.of();
        }

        LinkedHashMap<Long, BenefitLimitBucket> buckets = new LinkedHashMap<>();
        for (BenefitRuleBucket link : ruleBucketRepository
                .findByRuleIdOrderByConsumptionOrder(benefitRuleId)) {
            addWithParents(link.getBucket(), buckets);
        }

        List<BenefitLimitBucket> counting = new ArrayList<>();
        for (BenefitLimitBucket bucket : buckets.values()) {
            if (bucket.isActive() && bucket.getTimesLimit() != null) {
                counting.add(bucket);
            }
        }
        return List.copyOf(counting);
    }

    /**
     * A parent bucket constrains its children's consumption, so an occurrence
     * ceiling on a parent applies even when the child that was consumed caps
     * only money.
     */
    private void addWithParents(BenefitLimitBucket bucket, LinkedHashMap<Long, BenefitLimitBucket> target) {
        BenefitLimitBucket current = bucket;
        while (current != null) {
            if (target.putIfAbsent(current.getId(), current) != null) {
                return; // already walked this branch
            }
            current = current.getParentBucket();
        }
    }

    /**
     * What is still available to hold on a counting bucket: its ceiling, less
     * what is consumed, less what other approvals already hold.
     *
     * The distinction that matters everywhere else applies here too -- an
     * existing hold has already spoken for an occurrence even though nothing
     * is consumed yet, so a NEW decision may not take it. Deciding against
     * the consumed figure alone is how two approvals each take the last visit.
     */
    @Transactional(readOnly = true)
    public int reservableTimes(Long memberId, BenefitLimitBucket bucket,
            com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy policy,
            java.time.LocalDate serviceDate) {

        BucketPeriodCalculator.Period period =
                BucketPeriodCalculator.resolve(bucket, policy, serviceDate);

        Integer committed = consumptionRepository.sumCommittedTimes(
                memberId, bucket.getId(), period.start(), period.end(), null);
        Integer reserved = consumptionRepository.sumReservedTimes(
                memberId, bucket.getId(), period.start(), period.end());

        int remaining = bucket.getTimesLimit()
                - (committed == null ? 0 : committed)
                - (reserved == null ? 0 : reserved);
        return Math.max(0, remaining);
    }

    /** The bucket's period on this date, so callers outside this package need not compute it. */
    public java.time.LocalDate[] periodBounds(BenefitLimitBucket bucket,
            com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy policy,
            java.time.LocalDate serviceDate) {
        BucketPeriodCalculator.Period period =
                BucketPeriodCalculator.resolve(bucket, policy, serviceDate);
        return new java.time.LocalDate[] {period.start(), period.end()};
    }

    /** Same bounds, as a small record the caller can read by name. */
    public Period periodFor(BenefitLimitBucket bucket,
            com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy policy,
            java.time.LocalDate serviceDate) {
        java.time.LocalDate[] bounds = periodBounds(bucket, policy, serviceDate);
        return new Period(bounds[0], bounds[1]);
    }

    public record Period(java.time.LocalDate start, java.time.LocalDate end) {}
}
