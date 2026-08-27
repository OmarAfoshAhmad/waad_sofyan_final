package com.waad.tba.modules.benefitpolicy.service;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;

/**
 * How many OCCURRENCES a decision consumes from a bucket.
 *
 * Extracted so claims and pre-authorizations share one definition. Two
 * definitions of "how many times" would let an approval hold one quantity and
 * the claim that follows consume another -- and the release at conversion
 * would then fail to cancel what the claim commits, leaving a residue nobody
 * can explain.
 *
 * This is the counting dimension only. It never touches money: a bucket may
 * cap occurrences without capping any amount, and the two constraints are
 * enforced independently and never summed.
 */
@Component
public class TimesLimitEvaluator {

    /**
     * @param bucket           the bucket being consumed
     * @param approvedQuantity what the reviewer authorised -- NOT what was
     *                         requested, since a refused unit is not an
     *                         occurrence
     * @param alreadyCountedBuckets buckets already counted once for this
     *                         decision; PER_VISIT and PER_DAY count one per
     *                         bucket however many lines map to it, so the
     *                         caller carries the set across lines
     */
    public int occurrencesFor(BenefitLimitBucket bucket, int approvedQuantity,
            Set<CountedKey> alreadyCounted) {
        return occurrencesFor(bucket, approvedQuantity, alreadyCounted, null);
    }

    /**
     * The identity a once-per-decision count is keyed on. The bucket alone is
     * not enough: a decision covering more than one service date would merge
     * two distinct visits into one, holding a single occurrence for both.
     * Today an approval carries one expected date, so this changes nothing --
     * which is exactly when it is cheap to get right.
     */
    public record CountedKey(Long bucketId, java.time.LocalDate serviceDate) {}

    public int occurrencesFor(BenefitLimitBucket bucket, int approvedQuantity,
            Set<CountedKey> alreadyCounted, java.time.LocalDate serviceDate) {

        if (approvedQuantity <= 0) {
            return 0;
        }
        CountingMethod method = bucket.getCountingMethod() == null
                ? CountingMethod.EACH_LINE
                : bucket.getCountingMethod();

        return switch (method) {
            case EACH_UNIT -> approvedQuantity;
            case EACH_LINE -> 1;
            // Indivisible by nature: a visit is not half-attended, and a day
            // is not half-spent. Counted once per bucket for the whole
            // decision, not once per line.
            //
            // PER_DAY here decides how many occurrences a single-dated
            // decision consumes. It is NOT the answer to daysLimit, which
            // counts distinct service dates across a stay -- that stays a
            // closed failure for pre-authorizations, which carry one expected
            // date and no admission or discharge.
            case PER_VISIT, PER_DAY -> alreadyCounted.add(new CountedKey(bucket.getId(), serviceDate)) ? 1 : 0;
        };
    }
}
