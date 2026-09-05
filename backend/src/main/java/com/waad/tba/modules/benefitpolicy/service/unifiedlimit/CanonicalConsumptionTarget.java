package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * P1.11.1: a fully executable consumption INSTRUCTION -- not a bucket to
 * inspect, a movement to write. One entry per real bucket (or the
 * synthetic POLICY_GENERAL ceiling) the canonical decision actually
 * consumed, carrying every number a ledger writer needs so it never has to
 * ask "how much?", "how many?", or "does this count as a day?" itself.
 * Asking those questions again is re-deciding the limit -- exactly what
 * P1.6 removed from claim save, and P1.11 removes from the ledger.
 *
 * {@code amountToConsume}/{@code timesToConsume} are {@code null} (not
 * zero) when this target has no movement on that axis at all -- a bucket
 * with no {@code amountLimit} configured never gets an amount instruction,
 * distinct from "configured but nothing consumed this time".
 *
 * {@code consumeDay} is the DAYS axis's entire answer: {@code true} means
 * "record one occurrence of this service date against this bucket's day
 * count", {@code false} (or the target's absence entirely, when DAYS was
 * this bucket's only axis and it was refused) means "do nothing" -- a
 * writer must never derive this from whether the line had positive money,
 * which was the P1.11.0 inventory's confirmed gap.
 *
 * {@code limitKey}/{@code bucketId} follow {@link ResolvedLimitDescriptor}'s
 * own rule: {@code bucketId} is {@code null} exactly for POLICY_GENERAL,
 * and {@code limitKey} is what a writer keys idempotency and locking on --
 * never bucketId alone, since POLICY_GENERAL has none.
 */
public record CanonicalConsumptionTarget(
        String limitKey,
        Long bucketId,
        BigDecimal amountToConsume,
        Integer timesToConsume,
        boolean consumeDay,
        LocalDate serviceDate,
        LocalDate periodFrom,
        LocalDate periodTo,
        ResolvedLimitDescriptor descriptor) {
}
