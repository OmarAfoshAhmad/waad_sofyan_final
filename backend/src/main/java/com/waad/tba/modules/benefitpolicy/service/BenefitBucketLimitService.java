package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.entity.*;
import com.waad.tba.modules.benefitpolicy.enums.*;
import com.waad.tba.modules.benefitpolicy.repository.*;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BenefitBucketLimitService {
    private final BenefitRuleBucketRepository ruleBucketRepository;
    private final BenefitBucketConsumptionRepository consumptionRepository;
    private final BenefitPolicyRuleRepository policyRuleRepository;

    @Transactional(readOnly = true)
    public List<LimitSnapshot> findApplicable(Long ruleId, Long memberId, LocalDate serviceDate,
                                               EncounterType encounterType, Long excludeClaimId) {
        if (ruleId == null || memberId == null) return List.of();
        if (serviceDate == null) {
            throw new IllegalArgumentException(
                    "تاريخ الخدمة إلزامي لحساب السقوف، ولا يجوز استبداله بتاريخ اليوم");
        }
        LocalDate date = serviceDate;
        Map<Long, BenefitLimitBucket> buckets = new LinkedHashMap<>();
        Set<Long> directlyLinkedBucketIds = new HashSet<>();
        for (BenefitRuleBucket link : ruleBucketRepository.findByRuleIdOrderByConsumptionOrder(ruleId)) {
            directlyLinkedBucketIds.add(link.getBucket().getId());
            for (BenefitLimitBucket b : BucketChainWalker.chainFrom(link.getBucket())) {
                buckets.putIfAbsent(b.getId(), b);
            }
        }
        BenefitPolicy policy = policyRuleRepository.findById(ruleId)
                .map(BenefitPolicyRule::getBenefitPolicy).orElse(null);
        for (BenefitLimitBucket bucket : buckets.values()) {
            if (bucket.getLimitRole() == BenefitLimitBucket.LimitRole.POLICY_GENERAL_MIRROR) {
                validatePolicyGeneralMirror(bucket, policy);
            }
        }
        buckets.values().removeIf(bucket -> bucket.getLimitRole() == BenefitLimitBucket.LimitRole.POLICY_GENERAL_MIRROR);
        List<LimitSnapshot> result = new ArrayList<>();
        for (BenefitLimitBucket bucket : buckets.values()) {
            if (!bucket.isActive() || (bucket.getContextType() != EncounterType.ANY
                    && bucket.getContextType() != encounterType)) continue;
            Period period = period(bucket, date);
            BigDecimal usedAmount = consumptionRepository.sumCommittedAmount(memberId, bucket.getId(),
                    period.start(), period.end(), excludeClaimId);
            Integer usedTimes = consumptionRepository.sumCommittedTimes(memberId, bucket.getId(),
                    period.start(), period.end(), excludeClaimId);
            long usedDays = consumptionRepository.countCommittedServiceDays(memberId, bucket.getId(),
                    period.start(), period.end(), excludeClaimId);
            boolean serviceDayAlreadyUsed = consumptionRepository.existsCommittedForServiceDay(
                    memberId, bucket.getId(), date, excludeClaimId);
            result.add(new LimitSnapshot(bucket.getId(), bucket.getNameAr(), bucket.getAmountLimit(),
                    bucket.getTimesLimit(), bucket.getDaysLimit(), usedAmount, usedTimes,
                    (int) Math.min(Integer.MAX_VALUE, usedDays), serviceDayAlreadyUsed,
                    bucket.getCountingMethod() != null ? bucket.getCountingMethod() : CountingMethod.EACH_LINE,
                    bucket.getConsumptionBasis() != null ? bucket.getConsumptionBasis() : ConsumptionBasis.COMPANY_SHARE,
                    directlyLinkedBucketIds.contains(bucket.getId())));
        }
        if (policy != null && policy.getAnnualLimit() != null && policy.getAnnualLimit().signum() > 0) {
            BucketPeriodCalculator.Period annual = BucketPeriodCalculator.resolve(
                    LimitPeriodType.ANNUAL, 1, policy, date);
            BigDecimal committed = consumptionRepository.sumGeneralScopeCommitted(
                    memberId, policy.getId(), annual.start(), annual.end(), excludeClaimId);
            BigDecimal reserved = consumptionRepository.sumGeneralScopeReserved(
                    memberId, policy.getId(), annual.start(), annual.end());
            BigDecimal unavailable = Optional.ofNullable(committed).orElse(BigDecimal.ZERO)
                    .add(Optional.ofNullable(reserved).orElse(BigDecimal.ZERO));
            result.add(new LimitSnapshot(null, "السقف السنوي العام", policy.getAnnualLimit(), null, null,
                    unavailable, 0, 0, false, CountingMethod.EACH_LINE,
                    ConsumptionBasis.ELIGIBLE_AMOUNT, false));
        }
        return result;
    }

    // WAAD-FIN-1.0 (V146): a POLICY_GENERAL_MIRROR bucket duplicates
    // policy.annualLimit and is never treated as an independent limit. Any
    // drift between the two -- amount mismatch, wrong period type, or a
    // mirror on a policy with no annualLimit at all -- is a data-integrity
    // fault, not something to silently pick a value for.
    private void validatePolicyGeneralMirror(BenefitLimitBucket bucket, BenefitPolicy policy) {
        if (policy == null || policy.getAnnualLimit() == null) {
            throw new IllegalStateException("POLICY_GENERAL_MIRROR_MISMATCH: bucket id=" + bucket.getId()
                    + " is tagged POLICY_GENERAL_MIRROR but its policy has no annualLimit");
        }
        if (bucket.getPeriodType() != LimitPeriodType.ANNUAL
                || bucket.getAmountLimit() == null
                || bucket.getAmountLimit().compareTo(policy.getAnnualLimit()) != 0) {
            throw new IllegalStateException("POLICY_GENERAL_MIRROR_MISMATCH: bucket id=" + bucket.getId()
                    + " (periodType=" + bucket.getPeriodType() + ", amountLimit=" + bucket.getAmountLimit()
                    + ") does not match policy id=" + policy.getId() + " annualLimit=" + policy.getAnnualLimit());
        }
    }

    private Period period(BenefitLimitBucket bucket, LocalDate date) {
        BucketPeriodCalculator.Period resolved = BucketPeriodCalculator.resolve(bucket, bucket.getPolicy(), date);
        return new Period(resolved.start(), resolved.end());
    }

    public record LimitSnapshot(Long bucketId, String bucketName, BigDecimal amountLimit, Integer timesLimit,
                                Integer daysLimit, BigDecimal usedAmount, Integer usedTimes,
                                Integer usedDays, boolean serviceDayAlreadyUsed,
                                CountingMethod countingMethod, ConsumptionBasis consumptionBasis,
                                boolean directlyLinked) {
        // Backwards-compatible constructor for existing tests and callers; an
        // explicitly supplied snapshot represents a service-level bucket.
        public LimitSnapshot(Long bucketId, String bucketName, BigDecimal amountLimit, Integer timesLimit,
                             Integer daysLimit, BigDecimal usedAmount, Integer usedTimes,
                             Integer usedDays, boolean serviceDayAlreadyUsed,
                             CountingMethod countingMethod, ConsumptionBasis consumptionBasis) {
            this(bucketId, bucketName, amountLimit, timesLimit, daysLimit, usedAmount, usedTimes,
                    usedDays, serviceDayAlreadyUsed, countingMethod, consumptionBasis, true);
        }
    }
    private record Period(LocalDate start, LocalDate end) {}
}

