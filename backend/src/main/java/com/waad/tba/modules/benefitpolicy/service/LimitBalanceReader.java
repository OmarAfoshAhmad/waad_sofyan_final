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

    /**
     * A balance seen from the perspective of the approval that placed the
     * hold. Every figure is named for exactly what it is:
     *
     *   reservableAvailable    what ANY new decision may take (unchanged)
     *   ownActiveReservation   what THIS approval still holds
     *   availableForThisClaim  reservable + own, capped at actualRemaining
     *
     * reservableAvailable deliberately keeps its ordinary meaning. Widening it
     * to include the owner's hold would make the name lie everywhere it is
     * reported -- in this record, in the snapshot and in every audit that
     * reads it.
     */
    public record PreauthorizedClaimBalance(
            LimitBalance balance,
            BigDecimal ownActiveReservation,
            BigDecimal availableForThisClaim,
            Integer ownReservedTimes,
            Integer availableTimesForThisClaim) {}

    public record PreauthorizedClaimBalanceSet(
            Long memberId,
            Long preAuthorizationId,
            List<PreauthorizedClaimBalance> limits,
            BigDecimal bindingAvailableForThisClaim) {

        /**
         * Adapter for the financial engine. The ordinary balances remain
         * unchanged and auditable above; only this claim-specific projection
         * exposes the amount/times that the owner of the hold may spend.
         */
        public BalanceSet asFinancialEngineInput() {
            List<LimitBalance> projected = limits.stream().map(item -> {
                LimitBalance b = item.balance();
                return new LimitBalance(b.limit(), b.committed(), b.reserved(),
                        b.actualRemaining(), item.availableForThisClaim(),
                        b.timesLimit(), b.committedTimes(), b.reservedTimes(),
                        b.actualRemainingTimes(), item.availableTimesForThisClaim());
            }).toList();
            List<String> bindingKeys = projected.stream()
                    .filter(b -> b.reservableAvailable() != null
                            && bindingAvailableForThisClaim != null
                            && b.reservableAvailable().compareTo(bindingAvailableForThisClaim) == 0)
                    .map(b -> b.limit().definition().semanticKey()).toList();
            return new BalanceSet(memberId, projected, bindingAvailableForThisClaim, bindingKeys);
        }
    }

    private record BalanceKey(Long bucketId, LocalDate start, LocalDate end, Status status) {}

    /**
     * The balance a claim born from a pre-authorization may spend against.
     *
     * A hold protects limit FROM other decisions, but it was placed FOR this
     * one. The ordinary read subtracts every reservation without asking whose
     * it is, so a claim converting its own approval would find the money it
     * was promised already spoken for -- by itself -- and could be refused for
     * insufficient balance.
     *
     *     availableForClaim = reservableAvailable + ownActiveReservation
     *     availableForClaim <= actualRemaining
     *
     * The ceiling matters: the own reservation is added back, never stacked on
     * top of what the member actually has left.
     *
     * A SEPARATE entry point on purpose. Changing read() would alter the
     * meaning of available for every caller in the system to serve one path.
     */
    @Transactional(readOnly = true)
    public PreauthorizedClaimBalanceSet readForPreauthorizedClaim(Long memberId,
            List<EffectiveLimitResolver.EffectiveLimit> limits, Long excludeClaimId,
            Long preAuthorizationId, Long memberPolicyAssignmentId) {

        if (preAuthorizationId == null) {
            throw new IllegalArgumentException("preAuthorizationId is required");
        }
        if (memberPolicyAssignmentId == null) {
            throw new IllegalArgumentException("memberPolicyAssignmentId is required");
        }
        BalanceSet base = read(memberId, limits, excludeClaimId);

        List<PreauthorizedClaimBalance> result = new ArrayList<>();
        for (LimitBalance balance : base.limits()) {
            var definition = balance.limit().definition();
            String scope = definition.benefitScopeType() == BenefitScopeType.POLICY_GENERAL
                    ? "POLICY_GENERAL" : "BUCKET";

            BigDecimal own = Optional.ofNullable(consumptionRepository.sumOwnActiveReservation(
                    memberId, preAuthorizationId, memberPolicyAssignmentId, definition.bucketId(), scope,
                    definition.periodStart(), definition.periodEnd())).orElse(BigDecimal.ZERO);

            // Added BACK, then capped: the hold returns to its owner, but never
            // lifts them above what the member actually has left.
            BigDecimal availableForClaim = balance.reservableAvailable() == null ? null
                    : balance.reservableAvailable().add(own).min(balance.actualRemaining());

            Integer ownTimes = null;
            Integer availableTimes = null;
            if (balance.timesLimit() != null) {
                ownTimes = Optional.ofNullable(consumptionRepository.sumOwnActiveReservationTimes(
                        memberId, preAuthorizationId, memberPolicyAssignmentId, definition.bucketId(), scope,
                        definition.periodStart(), definition.periodEnd())).orElse(0);
                // The same rule in occurrences: an approval that took the last
                // visit must not block the claim it was granted for.
                availableTimes = Math.min(
                        Optional.ofNullable(balance.reservableTimes()).orElse(0) + ownTimes,
                        Optional.ofNullable(balance.actualRemainingTimes()).orElse(0));
            }

            result.add(new PreauthorizedClaimBalance(
                    balance, own, availableForClaim, ownTimes, availableTimes));
        }

        BigDecimal binding = result.stream()
                .map(PreauthorizedClaimBalance::availableForThisClaim)
                .filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(null);

        return new PreauthorizedClaimBalanceSet(
                memberId, preAuthorizationId, List.copyOf(result), binding);
    }

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
                // Both halves from the ledger now (V189). Committed used to be
                // summed out of claim_lines while reserved was read here, so
                // one subtraction drew on two sources and only one of them
                // could represent a movement that was not a claim. An imported
                // opening balance is exactly such a movement, and under the old
                // arrangement the only way to make it count was to fabricate
                // the claim it was not.
                //
                // Neither figure is ever derived from bucket rows: one line can
                // map to several buckets, so summing those would count the same
                // money once per category it happened to fall into.
                committed = consumptionRepository.sumGeneralScopeCommitted(
                        memberId, definition.policyId(), definition.periodStart(),
                        definition.periodEnd(), excludeClaimId);
                reserved = consumptionRepository.sumGeneralScopeReserved(
                        memberId, definition.policyId(), definition.periodStart(), definition.periodEnd());
            } else {
                committed = amount(bucketBalances, definition, Status.COMMITTED);
                reserved = amount(bucketBalances, definition, Status.RESERVED);
            }
            // A count-only bucket constrains occurrences and not money, so it
            // has no monetary balance to report. Null here means "this
            // ceiling does not measure money" -- never "no money left".
            BigDecimal actualRemaining = limit.effectiveLimit() == null
                    ? null : limit.effectiveLimit().subtract(committed);
            BigDecimal reservableAvailable = actualRemaining == null
                    ? null : actualRemaining.subtract(reserved);

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
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo).orElse(null);
        List<String> bindingKeys = balances.stream()
                .filter(balance -> binding != null && balance.reservableAvailable() != null
                        && balance.reservableAvailable().compareTo(binding) == 0)
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
