package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

import com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption.Status;
import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.benefitpolicy.service.BenefitBucketLimitService;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * P1.5.0a/b: turns today's live DB state into {@link BucketLimitSnapshot}
 * rows -- the only bridge between "existing data" and the P1.3 contract. No
 * new query is written for bucket selection or the ordinary reserved read;
 * every number there comes from a query that already exists and is already
 * exercised by its own tests. P1.5.0b's own-reservation read is the one
 * genuinely new query (see {@link BenefitBucketConsumptionRepository#aggregateOwnActiveReservation}),
 * added as the bulk form of two single-bucket queries {@code LimitBalanceReader}
 * already used, not a new concept.
 *
 * Why NOT {@code EffectiveLimitResolver}/{@code LimitBalanceReader.read}
 * (found while building P1.5.0a, corrects the design docs' original plan):
 * {@code ApplicableLimitResolver.resolve} silently {@code continue}s past
 * any bucket whose {@code amountLimit() == null} -- i.e. a pure
 * times-only or days-only ceiling (exactly Physio's shape) NEVER reaches
 * {@code EffectiveLimitResolver}'s output at all. Building on that path
 * would have reproduced the same class of gap this whole effort exists to
 * close. Bucket SELECTION here instead reuses
 * {@link BenefitBucketLimitService#findApplicable} verbatim (it sees every
 * bucket type, walks BucketChainWalker, and already carries
 * configured/committed/period for AMOUNT/TIMES/DAYS) -- only the RESERVED
 * reading it is missing (P1.1's confirmed gap) is added here, using the
 * exact same queries {@code LimitBalanceReader} already uses internally
 * ({@code aggregateAmountBalances}, {@code sumReservedTimes}), not new ones.
 * The same reasoning is why {@code readForPreauthorizedClaim} is not reused
 * for P1.5.0b either -- it is built on the same {@code read()} that has the
 * identical gap.
 */
@Service
@RequiredArgsConstructor
public class BucketLimitSnapshotAdapter {

    private final BenefitBucketLimitService bucketLimitService;
    private final BenefitLimitBucketRepository bucketRepository;
    private final BenefitBucketConsumptionRepository consumptionRepository;

    public record Result(List<BucketLimitSnapshot> snapshots, boolean blocked, String blockReason) {
        public static Result of(List<BucketLimitSnapshot> snapshots) {
            return new Result(snapshots, false, null);
        }

        public static Result blocked(String reason) {
            return new Result(List.of(), true, reason);
        }
    }

    /** Bucket selection + BUCKET_POLICY_MISMATCH validation, shared by every reservation mode. */
    private record Selection(List<BenefitBucketLimitService.LimitSnapshot> applicable,
            Map<Long, Long> owningPolicyByBucket, List<Long> realBucketIds, String blockReason) {
        boolean blocked() {
            return blockReason != null;
        }
    }

    private Selection selectApplicableBuckets(Long policyId, Long ruleId, Long memberId,
            LocalDate serviceDate, EncounterType encounterType, Long excludeClaimId) {
        List<BenefitBucketLimitService.LimitSnapshot> applicable =
                bucketLimitService.findApplicable(ruleId, memberId, serviceDate, encounterType, excludeClaimId);

        List<Long> realBucketIds = applicable.stream()
                .map(BenefitBucketLimitService.LimitSnapshot::bucketId)
                .filter(Objects::nonNull).distinct().toList();

        // BUCKET_POLICY_MISMATCH FIRST, before any balance is read (P1.3 §2):
        // BenefitBucketLimitService.findApplicable never checks this itself
        // (P1.1's confirmed gap) -- this adapter is where that check now lives.
        // One findAllById call for every distinct bucket -- not one findById
        // per bucket -- BenefitLimitBucketRepository already extends
        // JpaRepository, so this is not a new query, just the bulk form of
        // the one already used here.
        Map<Long, Long> owningPolicyByBucket = new HashMap<>();
        if (!realBucketIds.isEmpty()) {
            for (var bucket : bucketRepository.findAllById(realBucketIds)) {
                owningPolicyByBucket.put(bucket.getId(),
                        bucket.getPolicy() == null ? null : bucket.getPolicy().getId());
            }
        }
        for (Long bucketId : realBucketIds) {
            Long owningPolicyId = owningPolicyByBucket.get(bucketId);
            if (owningPolicyId != null && !owningPolicyId.equals(policyId)) {
                return new Selection(null, null, null, "BUCKET_POLICY_MISMATCH: bucket id=" + bucketId
                        + " belongs to policy id=" + owningPolicyId
                        + ", not the requested policy id=" + policyId);
            }
        }
        return new Selection(applicable, owningPolicyByBucket, realBucketIds, null);
    }

    /** The exact query LimitBalanceReader.read uses internally to see RESERVED amounts, bulk across buckets. */
    private Map<String, BigDecimal> reservedAmountByBucketPeriod(Long memberId, List<Long> realBucketIds,
            Long excludeClaimId) {
        Map<String, BigDecimal> reservedAmountByKey = new HashMap<>();
        if (!realBucketIds.isEmpty()) {
            for (var row : consumptionRepository.aggregateAmountBalances(memberId, realBucketIds, excludeClaimId)) {
                if (!Status.RESERVED.name().equals(row.getStatus())) continue;
                reservedAmountByKey.put(balanceKey(row.getBucketId(), row.getPeriodStart(), row.getPeriodEnd()),
                        row.getAmount());
            }
        }
        return reservedAmountByKey;
    }

    /**
     * NORMAL reservation mode only (an ordinary, non-preauth claim). See
     * {@link #buildForPreauthorizedClaim} for a claim converting its own
     * pre-authorization hold.
     */
    @Transactional(readOnly = true)
    public Result buildForNormalClaim(Long policyId, Long ruleId, Long memberId,
            LocalDate serviceDate, EncounterType encounterType, Long excludeClaimId) {
        Objects.requireNonNull(policyId, "policyId is required");
        Objects.requireNonNull(ruleId, "ruleId is required");
        Objects.requireNonNull(memberId, "memberId is required");

        Selection selection = selectApplicableBuckets(policyId, ruleId, memberId, serviceDate, encounterType, excludeClaimId);
        if (selection.blocked()) return Result.blocked(selection.blockReason());

        Map<String, BigDecimal> reservedAmountByKey =
                reservedAmountByBucketPeriod(memberId, selection.realBucketIds(), excludeClaimId);

        List<BucketLimitSnapshot> result = new ArrayList<>();
        for (var snapshot : selection.applicable()) {
            Long bucketId = snapshot.bucketId();
            Long owningPolicyId = bucketId == null ? policyId : selection.owningPolicyByBucket().get(bucketId);

            if (snapshot.amountLimit() != null) {
                BigDecimal committed = orZero(snapshot.usedAmount());
                BigDecimal reserved = bucketId == null
                        ? orZero(consumptionRepository.sumGeneralScopeReserved(
                                memberId, policyId, snapshot.periodStart(), snapshot.periodEnd()))
                        : reservedAmountByKey.getOrDefault(
                                balanceKey(bucketId, snapshot.periodStart(), snapshot.periodEnd()), BigDecimal.ZERO);
                BigDecimal remaining = snapshot.amountLimit().subtract(committed).subtract(reserved);
                result.add(new BucketLimitSnapshot(bucketId, owningPolicyId, LimitAxisType.AMOUNT, snapshot.countingMethod(),
                        snapshot.amountLimit(), committed, reserved, remaining,
                        snapshot.periodStart(), snapshot.periodEnd()));
            }

            if (snapshot.timesLimit() != null && bucketId != null) {
                int committedTimes = Optional.ofNullable(snapshot.usedTimes()).orElse(0);
                int reservedTimes = Optional.ofNullable(consumptionRepository.sumReservedTimes(
                        memberId, bucketId, snapshot.periodStart(), snapshot.periodEnd())).orElse(0);
                int remaining = snapshot.timesLimit() - committedTimes - reservedTimes;
                result.add(new BucketLimitSnapshot(bucketId, owningPolicyId, LimitAxisType.TIMES, snapshot.countingMethod(),
                        BigDecimal.valueOf(snapshot.timesLimit()), BigDecimal.valueOf(committedTimes),
                        BigDecimal.valueOf(reservedTimes), BigDecimal.valueOf(remaining),
                        snapshot.periodStart(), snapshot.periodEnd()));
            }

            if (snapshot.daysLimit() != null && bucketId != null) {
                // No reservation concept exists for days anywhere in this
                // product -- there is no sumReservedDays query to call.
                // activeReserved is zero because the capability does not
                // exist, not because it was measured and happened to be zero.
                int committedDays = Optional.ofNullable(snapshot.usedDays()).orElse(0);
                int remaining = snapshot.daysLimit() - committedDays;
                result.add(new BucketLimitSnapshot(bucketId, owningPolicyId, LimitAxisType.DAYS, snapshot.countingMethod(),
                        BigDecimal.valueOf(snapshot.daysLimit()), BigDecimal.valueOf(committedDays),
                        BigDecimal.ZERO, BigDecimal.valueOf(remaining),
                        snapshot.periodStart(), snapshot.periodEnd()));
            }
        }
        return Result.of(result);
    }

    /**
     * PREAUTHORIZED_CLAIM reservation mode (P1.5.0b): a claim converting its
     * own pre-authorization hold. The hold protects limit FROM other
     * decisions but was placed FOR this one, so it is added back before the
     * ceiling is applied -- {@code LimitBalanceReader.readForPreauthorizedClaim}'s
     * formula, reused verbatim here since it is already the canonical
     * definition of this math, just not its data source (see class javadoc).
     *
     * <pre>
     * actualRemaining      = configured - committed
     * reservableAvailable  = configured - committed - allActiveReserved
     * availableForThisClaim = min(actualRemaining, reservableAvailable + ownActiveReservation)
     * </pre>
     *
     * "Own" is legally scoped, not just "same member": same preauthorization,
     * same allocation (member_policy_assignment_id), same bucket, same
     * resolved limit period, status RESERVED. Any one of those differing
     * means the hold belongs to someone/something else and must NOT be
     * released back into this claim's availability.
     */
    @Transactional(readOnly = true)
    public Result buildForPreauthorizedClaim(Long policyId, Long ruleId, Long memberId,
            LocalDate serviceDate, EncounterType encounterType, Long excludeClaimId,
            Long preAuthorizationId, Long memberPolicyAssignmentId) {
        Objects.requireNonNull(policyId, "policyId is required");
        Objects.requireNonNull(ruleId, "ruleId is required");
        Objects.requireNonNull(memberId, "memberId is required");
        Objects.requireNonNull(preAuthorizationId, "preAuthorizationId is required");
        Objects.requireNonNull(memberPolicyAssignmentId, "memberPolicyAssignmentId is required");

        Selection selection = selectApplicableBuckets(policyId, ruleId, memberId, serviceDate, encounterType, excludeClaimId);
        if (selection.blocked()) return Result.blocked(selection.blockReason());

        Map<String, BigDecimal> reservedAmountByKey =
                reservedAmountByBucketPeriod(memberId, selection.realBucketIds(), excludeClaimId);

        // The one genuinely new bulk query (P1.5.0b): every RESERVED row this
        // preauthorization+allocation holds against any of the applicable
        // buckets, in one call -- not one call per bucket. Period is matched
        // in Java below against each snapshot's own already-resolved period,
        // not filtered in SQL, because different buckets can resolve to
        // different periods (an annual bucket next to a monthly one).
        Map<String, BigDecimal> ownAmountByKey = new HashMap<>();
        Map<String, Integer> ownTimesByKey = new HashMap<>();
        if (!selection.realBucketIds().isEmpty()) {
            for (var row : consumptionRepository.aggregateOwnActiveReservation(
                    memberId, preAuthorizationId, memberPolicyAssignmentId, selection.realBucketIds())) {
                String key = balanceKey(row.getBucketId(), row.getPeriodStart(), row.getPeriodEnd());
                ownAmountByKey.put(key, row.getAmount());
                ownTimesByKey.put(key, row.getTimes());
            }
        }

        List<BucketLimitSnapshot> result = new ArrayList<>();
        for (var snapshot : selection.applicable()) {
            Long bucketId = snapshot.bucketId();
            if (bucketId == null) continue; // the synthetic general ceiling has no preauth-scoped hold to convert
            Long owningPolicyId = selection.owningPolicyByBucket().get(bucketId);
            String key = balanceKey(bucketId, snapshot.periodStart(), snapshot.periodEnd());

            if (snapshot.amountLimit() != null) {
                BigDecimal configured = snapshot.amountLimit();
                BigDecimal committed = orZero(snapshot.usedAmount());
                BigDecimal allActiveReserved = reservedAmountByKey.getOrDefault(key, BigDecimal.ZERO);
                BigDecimal own = ownAmountByKey.getOrDefault(key, BigDecimal.ZERO);

                BigDecimal actualRemaining = configured.subtract(committed);
                BigDecimal reservableAvailable = actualRemaining.subtract(allActiveReserved);
                BigDecimal availableForThisClaim = reservableAvailable.add(own).min(actualRemaining);

                result.add(new BucketLimitSnapshot(bucketId, owningPolicyId, LimitAxisType.AMOUNT, snapshot.countingMethod(),
                        configured, committed, allActiveReserved, availableForThisClaim,
                        snapshot.periodStart(), snapshot.periodEnd()));
            }

            if (snapshot.timesLimit() != null) {
                int configured = snapshot.timesLimit();
                int committed = Optional.ofNullable(snapshot.usedTimes()).orElse(0);
                int allActiveReserved = Optional.ofNullable(consumptionRepository.sumReservedTimes(
                        memberId, bucketId, snapshot.periodStart(), snapshot.periodEnd())).orElse(0);
                int own = ownTimesByKey.getOrDefault(key, 0);

                int actualRemaining = configured - committed;
                int reservableAvailable = actualRemaining - allActiveReserved;
                int availableForThisClaim = Math.min(actualRemaining, reservableAvailable + own);

                result.add(new BucketLimitSnapshot(bucketId, owningPolicyId, LimitAxisType.TIMES, snapshot.countingMethod(),
                        BigDecimal.valueOf(configured), BigDecimal.valueOf(committed),
                        BigDecimal.valueOf(allActiveReserved), BigDecimal.valueOf(availableForThisClaim),
                        snapshot.periodStart(), snapshot.periodEnd()));
            }

            if (snapshot.daysLimit() != null) {
                // No reservation concept exists for days at all (see
                // buildForNormalClaim) -- there is nothing of "its own" for a
                // preauthorized claim to reclaim on this axis either.
                int committedDays = Optional.ofNullable(snapshot.usedDays()).orElse(0);
                int remaining = snapshot.daysLimit() - committedDays;
                result.add(new BucketLimitSnapshot(bucketId, owningPolicyId, LimitAxisType.DAYS, snapshot.countingMethod(),
                        BigDecimal.valueOf(snapshot.daysLimit()), BigDecimal.valueOf(committedDays),
                        BigDecimal.ZERO, BigDecimal.valueOf(remaining),
                        snapshot.periodStart(), snapshot.periodEnd()));
            }
        }
        return Result.of(result);
    }

    private static String balanceKey(Long bucketId, LocalDate periodStart, LocalDate periodEnd) {
        return bucketId + "|" + periodStart + "|" + periodEnd;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
