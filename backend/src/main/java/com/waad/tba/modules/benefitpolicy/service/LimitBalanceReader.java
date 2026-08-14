package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.entity.BenefitBucketConsumption.Status;
import com.waad.tba.modules.benefitpolicy.enums.BeneficiaryScopeType;
import com.waad.tba.modules.benefitpolicy.enums.BenefitScopeType;
import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.repository.BenefitBucketConsumptionRepository;
import com.waad.tba.modules.benefitpolicy.repository.BenefitLimitBucketRepository;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Reads committed/reserved balances without deciding which limits apply. */
@Service
@RequiredArgsConstructor
public class LimitBalanceReader {

    // Reads the occurrence limit a bucket declares, alongside its amount limit.
    private final BenefitBucketConsumptionRepository consumptionRepository;
    private final ClaimRepository claimRepository;
    private final BenefitLimitBucketRepository bucketRepository;

    /**
     * Two DIFFERENT balances, deliberately not collapsed into one number.
     *
     * actualRemaining = limit - committed
     *     What the member has actually used up. This is the figure for
     *     financial reports, the member's balance screen and exports. A
     *     reservation is a hold, not a consumption, and must never reduce it.
     *
     * reservableAvailable = actualRemaining - reserved
     *     What a NEW decision may still consume without breaking a hold
     *     already promised to a pre-authorization. Every decision that
     *     consumes limit -- approving a claim as well as issuing a
     *     pre-authorization -- reads this one.
     *
     * The old single `signedAvailable` subtracted reservations and was then
     * used for both purposes, which meant a pending hold silently understated
     * the member's remaining balance everywhere it was displayed.
     *
     * Values stay signed; clamping negatives is a display decision, not a
     * ledger one.
     */
    /**
     * Two independent dimensions, never mixed. A limit can bind by money, by
     * occurrences, or by both -- but min() across a currency amount and a
     * visit count is meaningless, so each constrains a decision in its own
     * terms.
     *
     * The times fields are null when the bucket carries no times limit --
     * "not applicable", not zero (zero means the count is used up, the
     * opposite of unconstrained).
     */
    public record LimitBalance(
            EffectiveLimitResolver.EffectiveLimit limit,
            BigDecimal committed,
            BigDecimal reserved,
            BigDecimal actualRemaining,
            BigDecimal reservableAvailable,
            Integer timesLimit,
            Integer committedTimes,
            Integer reservedTimes,
            Integer actualRemainingTimes,
            Integer reservableTimes) {}

    public record BalanceSet(
            Long memberId,
            List<LimitBalance> limits,
            BigDecimal bindingAvailableLimit,
            List<String> bindingSemanticKeys) {}

    private record BalanceKey(Long bucketId, LocalDate start, LocalDate end, Status status) {}

    @Transactional(readOnly = true)
    public BalanceSet read(Long memberId, List<EffectiveLimitResolver.EffectiveLimit> limits,
                           Long excludeClaimId) {
        if (memberId == null) throw new IllegalArgumentException("memberId is required");
        if (limits == null) throw new IllegalArgumentException("limits are required");
        if (limits.isEmpty()) return new BalanceSet(memberId, List.of(), null, List.of());

        for (var limit : limits) {
            if (limit.definition().beneficiaryScopeType() != BeneficiaryScopeType.MEMBER) {
                throw new IllegalStateException("UNSUPPORTED_BENEFICIARY_SCOPE: "
                        + limit.definition().beneficiaryScopeType());
            }
        }

        Collection<Long> bucketIds = limits.stream().map(l -> l.definition().bucketId())
                .filter(Objects::nonNull).distinct().toList();
        Map<BalanceKey, BigDecimal> bucketBalances = new HashMap<>();
        if (!bucketIds.isEmpty()) {
            for (var row : consumptionRepository.aggregateAmountBalances(memberId, bucketIds, excludeClaimId)) {
                bucketBalances.put(new BalanceKey(row.getBucketId(), row.getPeriodStart(), row.getPeriodEnd(),
                        Status.valueOf(row.getStatus())), row.getAmount());
            }
        }

        List<LimitBalance> balances = new ArrayList<>();
        for (var limit : limits) {
            var definition = limit.definition();
            BigDecimal committed;
            BigDecimal reserved;
            if (definition.benefitScopeType() == BenefitScopeType.POLICY_GENERAL) {
                committed = claimRepository.sumLimitConsumptionByMemberAndPeriodExcludingClaim(
                        memberId, definition.periodStart(), definition.periodEnd(), excludeClaimId);
                // Read from the general ceiling's OWN reservation rows (V174).
                // Still never derived from bucket rows: one line can map to
                // several buckets, so summing them would count the same money
                // repeatedly. Until the approval service starts writing holds
                // this is legitimately zero -- but it is now zero because
                // nothing is reserved, not because it cannot be expressed.
                reserved = consumptionRepository.sumGeneralScopeReserved(
                        memberId, definition.policyId(), definition.periodStart(), definition.periodEnd());
            } else {
                committed = amount(bucketBalances, definition, Status.COMMITTED);
                reserved = amount(bucketBalances, definition, Status.RESERVED);
            }
            BigDecimal actualRemaining = limit.effectiveLimit().subtract(committed);
            BigDecimal reservableAvailable = actualRemaining.subtract(reserved);

            // The occurrence dimension, read only where the bucket declares
            // one. Left null otherwise so "no times limit" cannot be mistaken
            // for "no times left".
            Integer timesLimit = null;
            Integer committedTimes = null;
            Integer reservedTimes = null;
            Integer actualRemainingTimes = null;
            Integer reservableTimes = null;
            if (definition.bucketId() != null) {
                timesLimit = bucketRepository.findById(definition.bucketId())
                        .map(BenefitLimitBucket::getTimesLimit).orElse(null);
                if (timesLimit != null) {
                    committedTimes = Optional.ofNullable(consumptionRepository.sumCommittedTimes(
                            memberId, definition.bucketId(), definition.periodStart(),
                            definition.periodEnd(), excludeClaimId)).orElse(0);
                    reservedTimes = Optional.ofNullable(consumptionRepository.sumReservedTimes(
                            memberId, definition.bucketId(), definition.periodStart(),
                            definition.periodEnd())).orElse(0);
                    actualRemainingTimes = Math.max(0, timesLimit - committedTimes);
                    reservableTimes = Math.max(0, actualRemainingTimes - reservedTimes);
                }
            }

            balances.add(new LimitBalance(limit, committed, reserved, actualRemaining, reservableAvailable,
                    timesLimit, committedTimes, reservedTimes, actualRemainingTimes, reservableTimes));
        }

        // The binding constraint for a NEW decision is the tightest
        // reservable figure, not the tightest remaining one: a hold already
        // promised elsewhere is not available to spend again.
        BigDecimal binding = balances.stream().map(LimitBalance::reservableAvailable)
                .min(BigDecimal::compareTo).orElse(null);
        List<String> bindingKeys = balances.stream()
                .filter(balance -> binding != null && balance.reservableAvailable().compareTo(binding) == 0)
                .map(balance -> balance.limit().definition().semanticKey()).toList();
        return new BalanceSet(memberId, List.copyOf(balances), binding, bindingKeys);
    }

    private BigDecimal amount(Map<BalanceKey, BigDecimal> values,
                              ApplicableLimitResolver.ApplicableLimitDefinition definition,
                              Status status) {
        return values.getOrDefault(new BalanceKey(definition.bucketId(), definition.periodStart(),
                definition.periodEnd(), status), BigDecimal.ZERO);
    }
}
