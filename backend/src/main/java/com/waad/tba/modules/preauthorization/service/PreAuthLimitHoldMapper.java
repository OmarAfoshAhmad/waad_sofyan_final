package com.waad.tba.modules.preauthorization.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;
import com.waad.tba.modules.benefitpolicy.service.TimesLimitEvaluator;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.BucketLimitSnapshot;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ResolvedLimitDescriptor;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ResolvedLimitItem;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ResolvedLimitMeasure;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitDecision;

import lombok.RequiredArgsConstructor;

/**
 * P1.12.2 — the ONLY place a PreAuth reservation AMOUNT is measured. Takes an
 * already-decided {@link UnifiedLimitDecision}, the {@link ResolvedLimitItem}s
 * it was resolved against, each real bucket's own reservation measure (P1.12.1
 * found PreAuth's amounts genuinely differ per bucket by
 * {@link ConsumptionBasis}, unlike a claim's single uniform
 * {@code limitConsumption}), and the financial engine's own two candidate
 * figures for the line -- and produces one {@link PreAuthorizationDecision.LimitHold}
 * per resolved limit, in the exact shape
 * {@code PreAuthorizationDecisionBuilder.decideLine} builds today.
 *
 * This class decides NOTHING:
 * <ul>
 * <li>not which buckets apply -- {@code BucketLimitSnapshotAdapter}</li>
 * <li>not what is approved -- {@code UnifiedLimitResolver}</li>
 * <li>not how much money the line settles for -- the caller's own
 *     {@code WaadFinancialEngine} + any PreAuth-specific split (e.g. money
 *     following an indivisible occurrence shortfall) happens BEFORE this
 *     mapper runs; it receives the two already-final candidate figures, never
 *     a raw {@code WaadFinancialEngine.Result} to re-derive from</li>
 * <li>not how many occurrences an approved quantity represents beyond
 *     translating it through a bucket's OWN counting method, via
 *     {@link TimesLimitEvaluator}'s STATELESS core -- {@code decision.approvedQuantity()}
 *     was already gated once-per-batch by the CALLER before the decision
 *     ever ran (P1.12.3's own mirror of {@code CoverageEngineService.requestedQuantity()}),
 *     so this mapper must never re-apply that dedup against the same
 *     tracking set a second time (that would find the slot already taken
 *     and silently under-count)</li>
 * </ul>
 * No repository, no {@code LimitBalanceReader}, no
 * {@code EffectiveLimitResolver}, no {@code ApplicableCountingLimitResolver},
 * no parent-chain walk, no re-derivation of {@code remaining} -- every
 * numeric "before" figure is read verbatim off the {@link BucketLimitSnapshot}
 * the adapter already computed, except {@code actualRemainingBefore}
 * (configured minus committed), a pure arithmetic re-expression of two
 * numbers already on the snapshot, never a new balance read.
 */
@Component
@RequiredArgsConstructor
public class PreAuthLimitHoldMapper {

    private final TimesLimitEvaluator timesLimitEvaluator;

    /**
     * @param companyShareAmount the line's own insurer share -- what a
     *                           {@code COMPANY_SHARE} bucket (and the
     *                           synthetic POLICY_GENERAL ceiling) reserves
     * @param eligibleAmount     the line's own eligible amount inside the
     *                           limit -- what an {@code ELIGIBLE_AMOUNT}
     *                           bucket reserves; genuinely a DIFFERENT figure
     *                           from {@code companyShareAmount} on the same
     *                           decision (P1.12.1's characterized golden
     *                           case: an eligible-amount bucket holding
     *                           1000 beside a general ceiling holding 800)
     * @param policyId           the policy this decision was resolved
     *                           against -- carried by the caller (already
     *                           known before any bucket is touched), never
     *                           derivable from {@link ResolvedLimitDescriptor}
     *                           itself, which does not carry it
     */
    public List<PreAuthorizationDecision.LimitHold> map(
            UnifiedLimitDecision decision,
            List<ResolvedLimitItem> items,
            List<ResolvedLimitMeasure> measures,
            BigDecimal companyShareAmount,
            BigDecimal eligibleAmount,
            Long policyId) {

        Map<String, ConsumptionBasis> basisByKey = new LinkedHashMap<>();
        for (ResolvedLimitMeasure measure : measures) {
            basisByKey.put(measure.limitKey(), measure.consumptionBasis());
        }

        Map<String, ResolvedLimitDescriptor> descriptorByKey = new LinkedHashMap<>();
        Map<String, BucketLimitSnapshot> amountByKey = new LinkedHashMap<>();
        Map<String, BucketLimitSnapshot> timesByKey = new LinkedHashMap<>();
        // Preserves the order items were resolved in -- one hold per
        // limitKey, in that same order, matching how every other canonical
        // consumer (ClaimLimitSnapshotFactory, CanonicalConsumptionTargetBuilder)
        // groups by limitKey rather than by array position.
        List<String> orderedKeys = new ArrayList<>();
        for (ResolvedLimitItem item : items) {
            String key = item.descriptor().limitKey();
            if (descriptorByKey.putIfAbsent(key, item.descriptor()) == null) {
                orderedKeys.add(key);
            }
            switch (item.numericSnapshot().limitType()) {
                case AMOUNT -> amountByKey.put(key, item.numericSnapshot());
                case TIMES -> timesByKey.put(key, item.numericSnapshot());
                case DAYS -> throw new IllegalStateException(
                        "PREAUTH_RESERVATION_UNEXPECTED_DAYS_AXIS: limitKey=" + key
                                + " -- DAYS must never reach this mapper; PREAUTH_RESERVATION blocks it upstream");
            }
        }

        List<PreAuthorizationDecision.LimitHold> holds = new ArrayList<>();
        for (String key : orderedKeys) {
            ResolvedLimitDescriptor descriptor = descriptorByKey.get(key);
            BucketLimitSnapshot amountSnapshot = amountByKey.get(key);
            BucketLimitSnapshot timesSnapshot = timesByKey.get(key);
            boolean isGeneral = descriptor.bucketId() == null;

            BigDecimal effectiveLimit = amountSnapshot == null ? null : amountSnapshot.configured();
            BigDecimal committedBefore = amountSnapshot == null ? null : amountSnapshot.committed();
            // reserved_before is NOT NULL in the schema: "no money held" is a
            // true statement about a bucket that measures no money, and must
            // be a real zero, never null (legacy PreAuthorizationDecisionBuilder's
            // own count-only-bucket branch made the same exception).
            BigDecimal reservedBefore = amountSnapshot == null
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : amountSnapshot.activeReserved();
            BigDecimal actualRemainingBefore = amountSnapshot == null ? null
                    : amountSnapshot.configured().subtract(amountSnapshot.committed());
            BigDecimal reservableAvailableBefore = amountSnapshot == null ? null : amountSnapshot.remaining();

            Integer timesLimit = timesSnapshot == null ? null : timesSnapshot.configured().intValue();
            Integer committedTimesBefore = timesSnapshot == null ? null : timesSnapshot.committed().intValue();
            Integer reservedTimesBefore = timesSnapshot == null ? null : timesSnapshot.activeReserved().intValue();
            Integer actualRemainingTimesBefore = timesSnapshot == null ? null
                    : timesSnapshot.configured().subtract(timesSnapshot.committed()).intValue();
            Integer reservableTimesBefore = timesSnapshot == null ? null : timesSnapshot.remaining().intValue();

            // The measurement rule (P1.12.1's finding, now the ONE place it
            // applies): COMPANY_SHARE and the synthetic general ceiling both
            // reserve the insurer's share; an ELIGIBLE_AMOUNT bucket reserves
            // the eligible amount instead -- a genuinely different figure on
            // the SAME decision, never collapsed to one number.
            ConsumptionBasis basis = isGeneral ? ConsumptionBasis.COMPANY_SHARE : basisByKey.get(key);
            BigDecimal amountReserved = effectiveLimit == null ? null
                    : (basis == ConsumptionBasis.ELIGIBLE_AMOUNT ? eligibleAmount : companyShareAmount);

            Integer timesReserved = timesSnapshot == null ? null
                    : timesLimitEvaluator.occurrencesFor(timesSnapshot.countingMethod(), decision.approvedQuantity());

            boolean binding = Objects.equals(descriptor.bucketId(), decision.bindingBucketId());

            holds.add(new PreAuthorizationDecision.LimitHold(
                    key,
                    isGeneral ? "POLICY_GENERAL" : "BUCKET",
                    descriptor.bucketId(),
                    policyId,
                    descriptor.periodType(), descriptor.periodFrom(), descriptor.periodTo(),
                    effectiveLimit, committedBefore, reservedBefore, actualRemainingBefore, reservableAvailableBefore,
                    timesLimit, committedTimesBefore, reservedTimesBefore, actualRemainingTimesBefore,
                    reservableTimesBefore,
                    effectiveLimit == null ? null : basis.name(),
                    effectiveLimit == null ? null : PreAuthorizationDecision.ReservedUnit.CURRENCY,
                    amountReserved, timesReserved, null,
                    binding));
        }
        return List.copyOf(holds);
    }
}
