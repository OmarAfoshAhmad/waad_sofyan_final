package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.entity.*;
import com.waad.tba.modules.benefitpolicy.enums.ConsumptionBasis;
import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.benefitpolicy.enums.LimitPeriodType;
import com.waad.tba.modules.benefitpolicy.repository.*;
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
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Append-only, idempotent balance ledger for shared and hierarchical limits.
 * Entries are created only after claim approval and are neutralized on reversal.
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
        Set<TimesLimitEvaluator.CountedKey> countedOnce = new HashSet<>();
        Set<Long> validatedDays = new HashSet<>();

        for (ClaimLine line : claim.getLines()) {
            if (line.getAppliedRuleId() == null || amount(line).signum() <= 0) continue;

            // The general ceiling, once per line and before its buckets.
            //
            // The ledger could not previously say what a claim had spent
            // against the policy's own annual limit -- that figure was summed
            // out of claim_lines while the ceiling's reservations lived here,
            // so the two halves of "limit - committed - reserved" came from
            // different places. Anything general that is not a claim, which is
            // precisely what an imported opening balance is, fell into the gap
            // between them and was invisible to every decision.
            postGeneralCeiling(claim, line, policy, memberId, serviceDate);

            LinkedHashMap<Long, BenefitLimitBucket> buckets = new LinkedHashMap<>();
            for (BenefitRuleBucket link : ruleBucketRepository.findByRuleIdOrderByConsumptionOrder(line.getAppliedRuleId())) {
                addWithParents(link.getBucket(), buckets);
            }
            for (BenefitLimitBucket candidate : buckets.values()) {
                if (isLegacyPolicyAnnualMirror(candidate, policy)) continue;
                BenefitLimitBucket bucket = bucketRepository.findByIdForUpdate(candidate.getId()).orElseThrow();
                if (!bucket.isActive()) continue;
                String key = "CLAIM:" + claimId + ":LINE:" + line.getId() + ":BUCKET:" + bucket.getId() + ":V" + line.getCalculationVersion();
                if (consumptionRepository.existsByIdempotencyKey(key)) continue;
                Period period = period(bucket, policy, serviceDate);
                int times = consumedTimes(bucket, line, countedOnce, serviceDate);
                BigDecimal consumedAmount;
                if (bucket.getAmountLimit() != null) {
                    consumedAmount = Optional.ofNullable(line.getLimitConsumption()).orElseThrow(() ->
                            new IllegalStateException("CANONICAL_LIMIT_CONSUMPTION_MISSING: claimLine=" + line.getId()));
                } else {
                    // Count/day-only buckets retain their historical informational
                    // amount; their monetary meaning is deliberately not invented.
                    consumedAmount = bucket.getConsumptionBasis() == ConsumptionBasis.COMPANY_SHARE
                            ? amount(line) : eligibleAmount(line);
                }
                validateAvailableBalance(bucket, memberId, serviceDate, period, consumedAmount, times,
                        validatedDays.add(bucket.getId()));
                entryWriter.appendClaimCommit(claim, line, policy, memberId, bucket,
                        period.start(), period.end(), consumedAmount, times,
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

    /**
     * The policy's general annual ceiling, spent by one claim line.
     *
     * The window is the calendar year of the service date, which is what
     * BucketPeriodCalculator returns for an ANNUAL period -- the same window
     * the reservation rows and the balance reader already use. Deriving it any
     * other way here would put the committed and reserved halves of one
     * ceiling in different periods.
     */
    private void postGeneralCeiling(Claim claim, ClaimLine line, BenefitPolicy policy, Long memberId,
            LocalDate serviceDate) {

        if (policy.getAnnualLimit() == null || policy.getAnnualLimit().signum() <= 0) return;

        BigDecimal consumed = line.getLimitConsumption();
        if (consumed == null || consumed.signum() <= 0) return;

        String key = "CLAIM:" + claim.getId() + ":LINE:" + line.getId()
                + ":GENERAL:V" + line.getCalculationVersion();
        if (consumptionRepository.existsByIdempotencyKey(key)) return;

        LocalDate start = LocalDate.of(serviceDate.getYear(), 1, 1);
        LocalDate end = LocalDate.of(serviceDate.getYear(), 12, 31);
        entryWriter.appendClaimGeneralCommit(claim, line, policy, memberId, start, end, consumed,
                line.getCalculationVersion(), key);
    }

    private void validatePolicyAnnualLimit(Claim claim, BenefitPolicy policy, Long memberId, LocalDate serviceDate) {
        if (policy.getAnnualLimit() == null || policy.getAnnualLimit().signum() <= 0) return;
        BenefitPolicy lockedPolicy = benefitPolicyRepository.findByIdForUpdate(policy.getId()).orElseThrow();
        LocalDate yearStart = LocalDate.of(serviceDate.getYear(), 1, 1);
        LocalDate yearEnd = LocalDate.of(serviceDate.getYear(), 12, 31);
        BigDecimal previouslyUsed = claimRepository.sumLimitConsumptionByMemberAndPeriodExcludingClaim(
                memberId, yearStart, yearEnd, claim.getId());
        BigDecimal current = claim.getLines().stream()
                .map(line -> Optional.ofNullable(line.getLimitConsumption()).orElse(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (previouslyUsed.add(current).compareTo(lockedPolicy.getAnnualLimit()) > 0) {
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

    private void addWithParents(BenefitLimitBucket bucket, Map<Long, BenefitLimitBucket> target) {
        BenefitLimitBucket current = bucket;
        while (current != null) {
            target.putIfAbsent(current.getId(), current);
            current = current.getParentBucket();
        }
    }

    /**
     * Delegates to the shared evaluator so claims and pre-authorizations have
     * ONE definition of how many occurrences a decision consumes. Two
     * definitions would let an approval hold one quantity and the claim that
     * follows consume another, leaving a residue at conversion.
     */
    private int consumedTimes(BenefitLimitBucket bucket, ClaimLine line,
            Set<TimesLimitEvaluator.CountedKey> countedOnce, LocalDate serviceDate) {
        int quantity = Math.max(1, line.getQuantity() == null ? 1 : line.getQuantity());
        return timesLimitEvaluator.occurrencesFor(bucket, quantity, countedOnce, serviceDate);
    }

    private void validateAvailableBalance(BenefitLimitBucket bucket, Long memberId, LocalDate serviceDate,
                                          Period period, BigDecimal consumedAmount, int consumedTimes,
                                          boolean validateDay) {
        BigDecimal usedAmount = consumptionRepository.sumCommittedAmount(memberId, bucket.getId(),
                period.start(), period.end(), null);
        Integer usedTimes = consumptionRepository.sumCommittedTimes(memberId, bucket.getId(),
                period.start(), period.end(), null);

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
        if (validateDay && bucket.getDaysLimit() != null
                && !consumptionRepository.existsCommittedForServiceDay(memberId, bucket.getId(), serviceDate, null)) {
            long usedDays = consumptionRepository.countCommittedServiceDays(memberId, bucket.getId(),
                    period.start(), period.end(), null);
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

