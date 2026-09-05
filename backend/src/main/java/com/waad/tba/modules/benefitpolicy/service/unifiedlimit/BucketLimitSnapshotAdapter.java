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
 * P1.5.0a: turns today's live DB state into {@link BucketLimitSnapshot} rows
 * -- the only bridge between "existing data" and the P1.3 contract. No new
 * query is written here; every number comes from a query that already
 * exists and is already exercised by its own tests.
 *
 * Why NOT {@code EffectiveLimitResolver}/{@code LimitBalanceReader.read}
 * (found while building this, corrects the design docs' original plan):
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
 *
 * NORMAL mode only (P1.5.0a). PREAUTHORIZED_CLAIM (P1.5.0b) needs its own
 * method built on {@code consumptionRepository.sumOwnActiveReservation*} --
 * deliberately not attempted here so this method's own scope stays provable.
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

    /**
     * NORMAL reservation mode only (an ordinary, non-preauth claim). See
     * class javadoc for PREAUTHORIZED_CLAIM's deliberate exclusion here.
     */
    @Transactional(readOnly = true)
    public Result buildForNormalClaim(Long policyId, Long ruleId, Long memberId,
            LocalDate serviceDate, EncounterType encounterType, Long excludeClaimId) {
        Objects.requireNonNull(policyId, "policyId is required");
        Objects.requireNonNull(ruleId, "ruleId is required");
        Objects.requireNonNull(memberId, "memberId is required");

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
                return Result.blocked("BUCKET_POLICY_MISMATCH: bucket id=" + bucketId
                        + " belongs to policy id=" + owningPolicyId
                        + ", not the requested policy id=" + policyId);
            }
        }

        // The exact query LimitBalanceReader.read uses internally to see
        // RESERVED amounts -- called directly here so a times/days-only
        // bucket (excluded from ApplicableLimitResolver's output) can still
        // get its reserved amount read when it also carries an amountLimit.
        Map<String, BigDecimal> reservedAmountByKey = new HashMap<>();
        if (!realBucketIds.isEmpty()) {
            for (var row : consumptionRepository.aggregateAmountBalances(memberId, realBucketIds, excludeClaimId)) {
                if (!Status.RESERVED.name().equals(row.getStatus())) continue;
                reservedAmountByKey.put(balanceKey(row.getBucketId(), row.getPeriodStart(), row.getPeriodEnd()),
                        row.getAmount());
            }
        }

        List<BucketLimitSnapshot> result = new ArrayList<>();
        for (var snapshot : applicable) {
            Long bucketId = snapshot.bucketId();
            Long owningPolicyId = bucketId == null ? policyId : owningPolicyByBucket.get(bucketId);

            if (snapshot.amountLimit() != null) {
                BigDecimal committed = orZero(snapshot.usedAmount());
                BigDecimal reserved = bucketId == null
                        ? orZero(consumptionRepository.sumGeneralScopeReserved(
                                memberId, policyId, snapshot.periodStart(), snapshot.periodEnd()))
                        : reservedAmountByKey.getOrDefault(
                                balanceKey(bucketId, snapshot.periodStart(), snapshot.periodEnd()), BigDecimal.ZERO);
                BigDecimal remaining = snapshot.amountLimit().subtract(committed).subtract(reserved);
                result.add(new BucketLimitSnapshot(bucketId, owningPolicyId, LimitAxisType.AMOUNT,
                        snapshot.amountLimit(), committed, reserved, remaining,
                        snapshot.periodStart(), snapshot.periodEnd()));
            }

            if (snapshot.timesLimit() != null && bucketId != null) {
                int committedTimes = Optional.ofNullable(snapshot.usedTimes()).orElse(0);
                int reservedTimes = Optional.ofNullable(consumptionRepository.sumReservedTimes(
                        memberId, bucketId, snapshot.periodStart(), snapshot.periodEnd())).orElse(0);
                int remaining = snapshot.timesLimit() - committedTimes - reservedTimes;
                result.add(new BucketLimitSnapshot(bucketId, owningPolicyId, LimitAxisType.TIMES,
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
                result.add(new BucketLimitSnapshot(bucketId, owningPolicyId, LimitAxisType.DAYS,
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
