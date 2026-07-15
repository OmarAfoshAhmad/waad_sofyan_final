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

    @Transactional(readOnly = true)
    public List<LimitSnapshot> findApplicable(Long ruleId, Long memberId, LocalDate serviceDate,
                                               EncounterType encounterType, Long excludeClaimId) {
        if (ruleId == null || memberId == null) return List.of();
        LocalDate date = serviceDate == null ? LocalDate.now() : serviceDate;
        Map<Long, BenefitLimitBucket> buckets = new LinkedHashMap<>();
        for (BenefitRuleBucket link : ruleBucketRepository.findByRuleIdOrderByConsumptionOrder(ruleId)) {
            BenefitLimitBucket current = link.getBucket();
            while (current != null) {
                buckets.putIfAbsent(current.getId(), current);
                current = current.getParentBucket();
            }
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
                    bucket.getCountingMethod(), bucket.getConsumptionBasis()));
        }
        return result;
    }

    private Period period(BenefitLimitBucket bucket, LocalDate date) {
        BenefitPolicy policy = bucket.getPolicy();
        return switch (bucket.getPeriodType()) {
            case PER_SERVICE, PER_VISIT, DAILY -> new Period(date, date);
            case MONTHLY -> new Period(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()));
            case ANNUAL -> new Period(LocalDate.of(date.getYear(), 1, 1), LocalDate.of(date.getYear(), 12, 31));
            case MULTI_YEAR_POLICY -> multiYearPolicyPeriod(bucket, date);
            case POLICY_PERIOD -> new Period(policy.getStartDate(), policy.getEndDate());
            case LIFETIME -> new Period(LocalDate.of(1900, 1, 1), null);
        };
    }

    private Period multiYearPolicyPeriod(BenefitLimitBucket bucket, LocalDate date) {
        LocalDate anchor = bucket.getPolicy().getStartDate();
        int years = bucket.getPeriodValue() == null ? 1 : Math.max(1, bucket.getPeriodValue());
        long elapsed = Math.max(0, ChronoUnit.YEARS.between(anchor, date));
        LocalDate start = anchor.plusYears((elapsed / years) * years);
        LocalDate end = start.plusYears(years).minusDays(1);
        if (bucket.getPolicy().getEndDate() != null && end.isAfter(bucket.getPolicy().getEndDate())) end = bucket.getPolicy().getEndDate();
        return new Period(start, end);
    }

    public record LimitSnapshot(Long bucketId, String bucketName, BigDecimal amountLimit, Integer timesLimit,
                                Integer daysLimit, BigDecimal usedAmount, Integer usedTimes,
                                Integer usedDays, boolean serviceDayAlreadyUsed,
                                CountingMethod countingMethod, ConsumptionBasis consumptionBasis) {}
    private record Period(LocalDate start, LocalDate end) {}
}
