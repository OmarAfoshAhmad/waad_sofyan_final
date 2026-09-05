package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.entity.*;
import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;
import com.waad.tba.modules.benefitpolicy.enums.LimitPeriodType;
import com.waad.tba.modules.benefitpolicy.repository.*;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.CanonicalConsumptionTarget;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.CanonicalConsumptionTargetBuilder;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ResolvedLimitItem;
import com.waad.tba.modules.benefitpolicy.service.unifiedlimit.UnifiedLimitDecision;
import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.common.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Append-only, idempotent balance ledger for shared and hierarchical limits.
 * Entries are created only after claim approval and are neutralized on reversal.
 *
 * P1.11.2: an EXECUTOR, not a second decision-maker. For every line whose
 * canonical decision is still available (every claim approved through the
 * live P1.6 path -- 100% of real-time approvals), the ONLY question this
 * class asks is {@code CanonicalConsumptionTarget}'s own fields:
 * limitKey/bucketId/amountToConsume/timesToConsume/consumeDay/period are
 * never recomputed here. No {@code approvedQuantity}, no
 * {@code countingMethod}, no rule/bucket-chain walk, no coverage or
 * financial decision type is known to this class for that path. The one
 * thing it still legitimately does per target is a CONCURRENCY-SAFETY
 * re-check against the bucket's own configured ceiling (locked, then
 * compared) -- verifying the already-decided instruction still fits, never
 * deciding a different one.
 *
 * See {@link #legacyReconcileTargets} for the one documented, bounded
 * exception (a historical claim reloaded independently for
 * {@code reconcileApprovedClaim} carries no live decision at all).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BenefitBucketLedgerService {
    private final ClaimRepository claimRepository;
    private final com.waad.tba.modules.member.service.MemberPolicyResolver memberPolicyResolver;
    private final BenefitPolicyRepository benefitPolicyRepository;
    private final BenefitRuleBucketRepository ruleBucketRepository;
    private final BenefitLimitBucketRepository bucketRepository;
    private final BenefitBucketConsumptionRepository consumptionRepository;
    // The single save gate. This service still owns the claim life cycle --
    // its locks, its idempotency keys, its ordering -- and delegates only
    // the append itself, so the ledger has one place to enforce invariants.
    private final BenefitConsumptionEntryWriter entryWriter;
    private final TimesLimitEvaluator timesLimitEvaluator;
    private final LimitBalanceReader limitBalanceReader;

    /**
     * One-time operational repair for an already approved legacy claim.
     * Uses the same idempotent commit path as normal approval; it never inserts
     * synthetic rows and is safe to retry.
     */
    @Transactional
    public int reconcileApprovedClaim(Long claimId) {
        Claim claim = claimRepository.findById(claimId).orElseThrow();
        String status = claim.getStatus() == null ? "" : claim.getStatus().name();
        if (!Set.of("APPROVED", "BATCHED", "SETTLED").contains(status)) {
            throw new BusinessRuleException(
                    "لا يمكن ترحيل مطالبة غير معتمدة إلى دفتر المنافع. الحالة الحالية: " + status);
        }

        int before = consumptionRepository.findByClaimIdAndStatus(
                claimId, BenefitBucketConsumption.Status.COMMITTED).size();
        commitClaim(claimId);
        int after = consumptionRepository.findByClaimIdAndStatus(
                claimId, BenefitBucketConsumption.Status.COMMITTED).size();
        log.info("Reconciled approved claim {} into benefit ledger: created={} committed={}",
                claimId, Math.max(0, after - before), after);
        return Math.max(0, after - before);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void commitClaim(Long claimId) {
        Claim claim = claimRepository.findById(claimId).orElseThrow();
        if (claim.getMember() == null) {
            log.warn("Skipping benefit ledger for claim {}: claim has no member", claimId);
            return;
        }
        if (claim.getServiceDate() == null) {
            // The ledger posts consumption against a dated bucket. Substituting
            // today would charge the wrong period's bucket without any trace.
            throw new IllegalStateException(
                    "CLAIM_SERVICE_DATE_REQUIRED: claim " + claimId + " has no service date");
        }
        LocalDate serviceDate = claim.getServiceDate();
        BenefitPolicy policy = resolvePolicy(claim, serviceDate);
        if (policy == null) {
            log.warn("Skipping benefit ledger for claim {}: no effective member/employer policy", claimId);
            return;
        }
        Long memberId = claim.getMember().getId();
        validatePolicyAnnualLimit(claim, policy, memberId, serviceDate);
        if (consumptionRepository.existsUnledgeredApprovedBucketClaim(memberId, claimId, policy.getAnnualLimit())) {
            throw new BusinessRuleException(
                    "يوجد للمستفيد مطالبة معتمدة سابقة لم تُرحّل إلى دفتر سقوف المنافع. "
                    + "تم إيقاف الاعتماد لمنع تجاوز السقف؛ راجع سلامة دفتر المنافع ثم أعد المحاولة.");
        }
        if (consumptionRepository.existsUnledgeredApprovedGeneralClaim(memberId, claimId, policy.getAnnualLimit())) {
            throw new BusinessRuleException(
                    "يوجد للمستفيد مطالبة معتمدة سابقة لم تُرحّل استهلاكها إلى السقف العام في دفتر المنافع. "
                    + "تم إيقاف الاعتماد لمنع تجاوز السقف؛ راجع سلامة دفتر المنافع ثم أعد المحاولة.");
        }

        for (ClaimLine line : claim.getLines()) {
            if (line.getAppliedRuleId() == null) continue;

            List<CanonicalConsumptionTarget> targets = line.getUnifiedLimitDecision() != null
                    ? CanonicalConsumptionTargetBuilder.build(line.getUnifiedLimitDecision(),
                            line.getLimitConsumption() == null ? BigDecimal.ZERO : line.getLimitConsumption(),
                            line.getResolvedLimitItems() == null ? List.of() : line.getResolvedLimitItems(),
                            serviceDate)
                    : legacyReconcileTargets(line, policy, serviceDate);

            for (CanonicalConsumptionTarget target : targets) {
                String key = "CLAIM:" + claimId + ":LINE:" + line.getId()
                        + ":TARGET:" + target.limitKey() + ":V" + line.getCalculationVersion();
                if (consumptionRepository.existsByIdempotencyKey(key)) continue;

                BigDecimal amount = target.amountToConsume() == null ? BigDecimal.ZERO : target.amountToConsume();
                int times = target.timesToConsume() == null ? 0 : target.timesToConsume();

                if (target.bucketId() == null) {
                    // POLICY_GENERAL: a scope, not a bucket -- nothing to lock or
                    // look up. validatePolicyAnnualLimit() above already verified
                    // the claim's own total fits before any row was written.
                    if (amount.signum() <= 0) continue;
                    entryWriter.appendClaimGeneralCommit(claim, line, policy, memberId,
                            target.periodFrom(), target.periodTo(), amount, line.getCalculationVersion(), key);
                    continue;
                }

                BenefitLimitBucket bucket = bucketRepository.findByIdForUpdate(target.bucketId()).orElseThrow();
                if (!bucket.isActive() || isLegacyPolicyAnnualMirror(bucket, policy)) continue;

                // The one legitimate re-check left: does this ALREADY-DECIDED
                // instruction still fit the bucket's OWN configured ceiling,
                // now that this transaction holds its lock? Never a different
                // instruction -- only a safety comparison against what target
                // already says to consume.
                validateAvailableBalance(bucket, memberId, target.serviceDate(), target.periodFrom(), target.periodTo(),
                        amount, times, target.consumeDay());
                entryWriter.appendClaimCommit(claim, line, policy, memberId, bucket,
                        target.periodFrom(), target.periodTo(), amount, times, target.consumeDay(),
                        line.getCalculationVersion(), key);
            }
        }
    }

    private boolean isLegacyPolicyAnnualMirror(BenefitLimitBucket bucket, BenefitPolicy policy) {
        return policy.getAnnualLimit() != null
                && bucket.getPeriodType() == LimitPeriodType.ANNUAL
                && bucket.getAmountLimit() != null
                && bucket.getAmountLimit().compareTo(policy.getAnnualLimit()) == 0
                && ("B-GENERAL".equalsIgnoreCase(bucket.getCode())
                    || (bucket.getBenefitGroup() != null && "G-GENERAL".equalsIgnoreCase(bucket.getBenefitGroup().getCode())));
    }

    private void validatePolicyAnnualLimit(Claim claim, BenefitPolicy policy, Long memberId, LocalDate serviceDate) {
        if (policy.getAnnualLimit() == null || policy.getAnnualLimit().signum() <= 0) return;
        BenefitPolicy lockedPolicy = benefitPolicyRepository.findByIdForUpdate(policy.getId()).orElseThrow();
        LocalDate yearStart = LocalDate.of(serviceDate.getYear(), 1, 1);
        LocalDate yearEnd = LocalDate.of(serviceDate.getYear(), 12, 31);
        // Read from the ledger (V189), not claim_lines -- the two guards above
        // just proved every approved claim for this member has a COMMITTED
        // POLICY_GENERAL row, so the ledger's committed figure is complete.
        var ceiling = limitBalanceReader.readGeneralCeiling(
                memberId, lockedPolicy.getId(), lockedPolicy.getAnnualLimit(), yearStart, yearEnd, claim.getId());
        BigDecimal previouslyUsed = ceiling == null ? BigDecimal.ZERO : ceiling.committed();
        BigDecimal current = claim.getLines().stream()
                .map(line -> Optional.ofNullable(line.getLimitConsumption()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // The EFFECTIVE ceiling, not the policy's. These differ whenever an
        // exceptional uplift has been granted to this member, and comparing
        // against the policy figure here would refuse a claim that fits inside
        // the ceiling every screen shows -- an exception that looks granted
        // and is never honoured.
        BigDecimal effectiveCeiling = ceiling == null
                ? lockedPolicy.getAnnualLimit()
                : ceiling.annualLimit();
        if (previouslyUsed.add(current).compareTo(effectiveCeiling) > 0) {
            throw new BusinessRuleException("تغير الرصيد أثناء الاعتماد وتجاوز السقف العام السنوي للوثيقة. أعد احتساب المطالبة ثم حاول مجددًا.");
        }
    }

    /**
     * The policy that applied ON THE SERVICE DATE -- the ledger must post
     * consumption against the bucket that was actually in force then, not
     * whichever policy the member points at today.
     */
    private BenefitPolicy resolvePolicy(Claim claim, LocalDate serviceDate) {
        return memberPolicyResolver.resolveFor(claim.getMember(), serviceDate).orElse(null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reverseClaim(Long claimId) {
        for (BenefitBucketConsumption original : consumptionRepository.findByClaimIdAndStatus(
                claimId, BenefitBucketConsumption.Status.COMMITTED)) {
            String key = original.getIdempotencyKey() + ":REVERSAL";
            if (consumptionRepository.existsByIdempotencyKey(key)) continue;
            // A general-ceiling movement has no bucket to lock. It measures the
            // policy itself, and the member row this transaction already holds
            // is what serialises it -- there is no narrower row to take.
            if (original.getBucket() != null) {
                bucketRepository.findByIdForUpdate(original.getBucket().getId()).orElseThrow();
            }
            LocalDateTime now = LocalDateTime.now();
            // The original is NOT touched. It stays COMMITTED forever, and the
            // compensating row below is what removes the money from the
            // balance -- because every balance query now reads
            // net = original - SUM(its reversals) rather than filtering the
            // original out by status. Flipping the original (the previous
            // behaviour) both erased the history and made partial reversal
            // impossible to express.
            // Release what is still OUTSTANDING, not the gross original. The
            // pre-authorization path already does this; releasing gross here
            // would give back more than is held the moment any partial
            // reversal exists, and the database would reject it as a raw
            // constraint error rather than posting the correct residual.
            java.math.BigDecimal releasedAmount = java.util.Optional.ofNullable(
                    consumptionRepository.sumReleasedAmount(original.getId()))
                    .orElse(java.math.BigDecimal.ZERO);
            java.math.BigDecimal outstandingAmount = java.util.Optional.ofNullable(
                    original.getApprovedAmount()).orElse(java.math.BigDecimal.ZERO)
                    .subtract(releasedAmount).max(java.math.BigDecimal.ZERO);

            int releasedTimes = java.util.Optional.ofNullable(
                    consumptionRepository.sumReleasedTimes(original.getId())).orElse(0);
            int outstandingTimes = Math.max(0, java.util.Optional.ofNullable(
                    original.getTimesConsumed()).orElse(0) - releasedTimes);

            if (outstandingAmount.signum() == 0 && outstandingTimes == 0) {
                continue;
            }
            entryWriter.appendClaimReversal(original, outstandingAmount, outstandingTimes, key, now);
        }
    }

    /**
     * P1.11.2's one documented, bounded exception: {@link #reconcileApprovedClaim}
     * repairs an ALREADY approved historical claim, reloaded independently in
     * its own transaction -- it never carries a live
     * {@code ClaimLine.unifiedLimitDecision}/{@code resolvedLimitItems} from
     * any in-flight request (those are {@code @Transient}, request-scoped
     * carriers, not a persisted source of truth). This reconstructs the same
     * {@link CanonicalConsumptionTarget} SHAPE the canonical path would have
     * produced, from what a historical line actually has on disk
     * (appliedRuleId, approvedQuantity, limitConsumption) and the same
     * rule-to-bucket walk {@code BenefitBucketLimitService} uses elsewhere --
     * not a second, independently-invented resolution algorithm.
     *
     * <pre>
     * Reason:                no transient decision survives past the
     *                        original request that produced it
     * Canonical replacement: none needed once every claim's own approval
     *                        runs through the live P1.6 canonical path --
     *                        true for every claim approved after P1.6
     * Who still depends on it: BenefitLedgerAdminController.reconcileApprovedClaim,
     *                        ClaimLegacyReconciliationService (both act
     *                        ONLY on historical claims, never new approvals)
     * New code may use it?  NO
     * Removal condition:     no claim lacking a live decision can ever
     *                        reach commitClaim again (confirmed against
     *                        production data, or the reconcile endpoint
     *                        itself is retired)
     * Target removal milestone: P1.11.3 or later, once confirmed safe
     * </pre>
     */
    private List<CanonicalConsumptionTarget> legacyReconcileTargets(ClaimLine line, BenefitPolicy policy,
            LocalDate serviceDate) {
        if (amount(line).signum() <= 0) return List.of();

        LinkedHashMap<Long, BenefitLimitBucket> buckets = new LinkedHashMap<>();
        for (BenefitRuleBucket link : ruleBucketRepository.findByRuleIdOrderByConsumptionOrder(line.getAppliedRuleId())) {
            addWithParents(link.getBucket(), buckets);
        }

        List<CanonicalConsumptionTarget> targets = new ArrayList<>();
        Set<TimesLimitEvaluator.CountedKey> countedOnce = new HashSet<>();
        for (BenefitLimitBucket bucket : buckets.values()) {
            if (isLegacyPolicyAnnualMirror(bucket, policy) || !bucket.isActive()) continue;
            Period period = period(bucket, policy, serviceDate);
            int times = legacyConsumedTimes(bucket, line, countedOnce, serviceDate);
            BigDecimal bucketAmount;
            if (bucket.getAmountLimit() != null) {
                bucketAmount = Optional.ofNullable(line.getLimitConsumption()).orElseThrow(() ->
                        new IllegalStateException("CANONICAL_LIMIT_CONSUMPTION_MISSING: claimLine=" + line.getId()));
            } else {
                bucketAmount = bucket.getConsumptionBasis() == ConsumptionBasis.COMPANY_SHARE
                        ? amount(line) : eligibleAmount(line);
            }
            // Historical claims predate the approvedDays-aware decision
            // (P1.11) entirely -- preserved exactly as this ledger always
            // behaved for a legacy claim: a days-limited bucket gets a row
            // whenever the line itself had positive money, since that is
            // the only signal a pre-P1.11 claim ever recorded.
            boolean consumeDay = bucket.getDaysLimit() != null;
            if (bucketAmount.signum() <= 0 && times == 0 && !consumeDay) continue;

            targets.add(new CanonicalConsumptionTarget(
                    com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ResolvedLimitDescriptor.bucketKey(bucket.getId()),
                    bucket.getId(), bucketAmount.signum() > 0 ? bucketAmount : null, times > 0 ? times : null,
                    consumeDay, serviceDate, period.start(), period.end(), null));
        }

        BigDecimal generalAmount = line.getLimitConsumption();
        if (policy.getAnnualLimit() != null && policy.getAnnualLimit().signum() > 0
                && generalAmount != null && generalAmount.signum() > 0) {
            LocalDate yearStart = LocalDate.of(serviceDate.getYear(), 1, 1);
            LocalDate yearEnd = LocalDate.of(serviceDate.getYear(), 12, 31);
            targets.add(new CanonicalConsumptionTarget(
                    com.waad.tba.modules.benefitpolicy.service.unifiedlimit.ResolvedLimitDescriptor.policyGeneralKey(policy.getId()),
                    null, generalAmount, null, false, serviceDate, yearStart, yearEnd, null));
        }
        return targets;
    }

    private void addWithParents(BenefitLimitBucket bucket, Map<Long, BenefitLimitBucket> target) {
        BenefitLimitBucket current = bucket;
        while (current != null) {
            target.putIfAbsent(current.getId(), current);
            current = current.getParentBucket();
        }
    }

    /** Legacy-path-only occurrence counting -- see {@link #legacyReconcileTargets}. */
    private int legacyConsumedTimes(BenefitLimitBucket bucket, ClaimLine line,
            Set<TimesLimitEvaluator.CountedKey> countedOnce, LocalDate serviceDate) {
        if (bucket.getTimesLimit() == null) {
            return 0;
        }
        if (line.getApprovedQuantity() == null) {
            throw new IllegalStateException(
                    "TIMES_LEDGER_APPROVED_QUANTITY_MISSING: bucket=" + bucket.getId()
                            + " claimLine=" + line.getId()
                            + " -- a TIMES-limited bucket cannot commit consumption for a line "
                            + "whose canonical decision recorded no approvedQuantity");
        }
        int quantity = Math.max(0, line.getApprovedQuantity());
        return timesLimitEvaluator.occurrencesFor(bucket, quantity, countedOnce, serviceDate);
    }

    private void validateAvailableBalance(BenefitLimitBucket bucket, Long memberId, LocalDate serviceDate,
                                          LocalDate periodStart, LocalDate periodEnd,
                                          BigDecimal consumedAmount, int consumedTimes, boolean consumeDay) {
        BigDecimal usedAmount = consumptionRepository.sumCommittedAmount(memberId, bucket.getId(),
                periodStart, periodEnd, null);
        Integer usedTimes = consumptionRepository.sumCommittedTimes(memberId, bucket.getId(),
                periodStart, periodEnd, null);

        if (bucket.getAmountLimit() != null
                && usedAmount.add(consumedAmount).compareTo(bucket.getAmountLimit()) > 0) {
            throw new BusinessRuleException("تغير الرصيد أثناء الاعتماد وتجاوز السقف المالي للوعاء «"
                    + bucket.getNameAr() + "». أعد احتساب المطالبة ثم حاول مجددًا.");
        }
        if (bucket.getTimesLimit() != null
                && (usedTimes == null ? 0 : usedTimes) + consumedTimes > bucket.getTimesLimit()) {
            throw new BusinessRuleException("تغير الرصيد أثناء الاعتماد وتجاوز حد المرات للوعاء «"
                    + bucket.getNameAr() + "». أعد احتساب المطالبة ثم حاول مجددًا.");
        }
        if (consumeDay && bucket.getDaysLimit() != null
                && !consumptionRepository.existsCommittedForServiceDay(memberId, bucket.getId(), serviceDate, null)) {
            long usedDays = consumptionRepository.countCommittedServiceDays(memberId, bucket.getId(),
                    periodStart, periodEnd, null);
            if (usedDays + 1 > bucket.getDaysLimit()) {
                throw new BusinessRuleException("تغير الرصيد أثناء الاعتماد وتجاوز حد الأيام للوعاء «"
                        + bucket.getNameAr() + "». أعد احتساب المطالبة ثم حاول مجددًا.");
            }
        }
    }

    private BigDecimal amount(ClaimLine line) {
        return Optional.ofNullable(line.getCompanyShare()).orElseGet(() ->
                Optional.ofNullable(line.getApprovedAmount()).orElse(BigDecimal.ZERO));
    }

    private BigDecimal eligibleAmount(ClaimLine line) {
        BigDecimal effectiveTotal = Optional.ofNullable(line.getTotalPrice()).orElse(BigDecimal.ZERO);
        BigDecimal limitRefused = Optional.ofNullable(line.getLimitRefused()).orElse(BigDecimal.ZERO);
        // The ledger must consume the eligible amount after the benefit ceiling.
        // Recording the pre-cap total makes a correctly partial-approved claim fail
        // again during approval and can overstate later usage.
        return effectiveTotal.subtract(limitRefused).max(BigDecimal.ZERO);
    }

    private Period period(BenefitLimitBucket bucket, BenefitPolicy policy, LocalDate date) {
        BucketPeriodCalculator.Period resolved = BucketPeriodCalculator.resolve(bucket, policy, date);
        return new Period(resolved.start(), resolved.end());
    }

    private record Period(LocalDate start, LocalDate end) {}
}
