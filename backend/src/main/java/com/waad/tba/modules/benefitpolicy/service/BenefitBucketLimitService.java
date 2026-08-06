package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.entity.*;
import com.waad.tba.modules.benefitpolicy.enums.*;
import com.waad.tba.modules.benefitpolicy.repository.*;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import com.waad.tba.modules.claim.repository.ClaimRepository;
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
    private final ClaimRepository claimRepository;

    @Transactional(readOnly = true)
    public List<LimitSnapshot> findApplicable(Long ruleId, Long memberId, LocalDate serviceDate,
                                               EncounterType encounterType, Long excludeClaimId) {
        if (ruleId == null || memberId == null) return List.of();
        LocalDate date = serviceDate == null ? LocalDate.now() : serviceDate;
        Map<Long, BenefitLimitBucket> buckets = new LinkedHashMap<>();
        Set<Long> directlyLinkedBucketIds = new HashSet<>();
        for (BenefitRuleBucket link : ruleBucketRepository.findByRuleIdOrderByConsumptionOrder(ruleId)) {
            directlyLinkedBucketIds.add(link.getBucket().getId());
            BenefitLimitBucket current = link.getBucket();
            while (current != null) {
                buckets.putIfAbsent(current.getId(), current);
                current = current.getParentBucket();
            }
        }
        BenefitPolicy policy = policyRuleRepository.findById(ruleId)
                .map(BenefitPolicyRule::getBenefitPolicy).orElse(null);
        if (policy != null && policy.getAnnualLimit() != null && policy.getAnnualLimit().signum() > 0) {
            buckets.values().removeIf(bucket -> isLegacyPolicyAnnualMirror(bucket, policy));
        }
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
            LocalDate yearStart = LocalDate.of(date.getYear(), 1, 1);
            LocalDate yearEnd = LocalDate.of(date.getYear(), 12, 31);
            BigDecimal used = claimRepository.sumApprovedAmountsByMemberAndYearExcludingClaim(
                    memberId, yearStart, yearEnd, excludeClaimId);
            result.add(new LimitSnapshot(-policy.getId(), "السقف العام السنوي للوثيقة",
                    policy.getAnnualLimit(), null, null, used, 0, 0, false,
                    CountingMethod.EACH_LINE, ConsumptionBasis.COMPANY_SHARE, false));
        }
        return result;
    }

    private boolean isLegacyPolicyAnnualMirror(BenefitLimitBucket bucket, BenefitPolicy policy) {
        return bucket.getPeriodType() == LimitPeriodType.ANNUAL
                && bucket.getAmountLimit() != null
                && bucket.getAmountLimit().compareTo(policy.getAnnualLimit()) == 0
                && ("B-GENERAL".equalsIgnoreCase(bucket.getCode())
                    || (bucket.getBenefitGroup() != null && "G-GENERAL".equalsIgnoreCase(bucket.getBenefitGroup().getCode())));
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

