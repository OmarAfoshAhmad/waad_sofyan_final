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
        BenefitPolicy policy = bucket.getPolicy();
        LimitPeriodType periodType = bucket.getPeriodType() != null ? bucket.getPeriodType() : LimitPeriodType.ANNUAL;
        return switch (periodType) {
            case PER_SERVICE, PER_VISIT, DAILY -> new Period(date, date);
            case WEEKLY -> new Period(date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SATURDAY)),
                    date.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.SATURDAY)).plusDays(6));
            case MONTHLY -> new Period(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()));
            case QUARTERLY -> {
                int quarterStartMonth = ((date.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate start = LocalDate.of(date.getYear(), quarterStartMonth, 1);
                yield new Period(start, start.plusMonths(3).minusDays(1));
            }
            case ANNUAL -> new Period(LocalDate.of(date.getYear(), 1, 1), LocalDate.of(date.getYear(), 12, 31));
            case MULTI_YEAR_POLICY -> multiYearPolicyPeriod(bucket, date);
            case CUSTOM_DAYS -> customPeriod(bucket, date, java.time.temporal.ChronoUnit.DAYS);
            case CUSTOM_WEEKS -> customPeriod(bucket, date, java.time.temporal.ChronoUnit.WEEKS);
            case CUSTOM_MONTHS -> customPeriod(bucket, date, java.time.temporal.ChronoUnit.MONTHS);
            case CUSTOM_YEARS -> customPeriod(bucket, date, java.time.temporal.ChronoUnit.YEARS);
            case POLICY_PERIOD -> policy != null && policy.getStartDate() != null
                    ? new Period(policy.getStartDate(), policy.getEndDate())
                    : new Period(LocalDate.of(date.getYear(), 1, 1), LocalDate.of(date.getYear(), 12, 31));
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

    private Period customPeriod(BenefitLimitBucket bucket, LocalDate date, ChronoUnit unit) {
        LocalDate anchor = bucket.getPolicy() != null && bucket.getPolicy().getStartDate() != null
                ? bucket.getPolicy().getStartDate()
                : LocalDate.of(date.getYear(), 1, 1);
        int value = bucket.getPeriodValue() == null ? 1 : Math.max(1, bucket.getPeriodValue());
        long elapsed = Math.max(0, switch (unit) {
            case DAYS -> ChronoUnit.DAYS.between(anchor, date);
            case WEEKS -> ChronoUnit.WEEKS.between(anchor, date);
            case MONTHS -> ChronoUnit.MONTHS.between(anchor, date);
            case YEARS -> ChronoUnit.YEARS.between(anchor, date);
            default -> throw new IllegalArgumentException("Unsupported custom period unit: " + unit);
        });
        long periods = elapsed / value;
        LocalDate start = switch (unit) {
            case DAYS -> anchor.plusDays(periods * value);
            case WEEKS -> anchor.plusWeeks(periods * value);
            case MONTHS -> anchor.plusMonths(periods * value);
            case YEARS -> anchor.plusYears(periods * value);
            default -> throw new IllegalArgumentException("Unsupported custom period unit: " + unit);
        };
        LocalDate end = switch (unit) {
            case DAYS -> start.plusDays(value).minusDays(1);
            case WEEKS -> start.plusWeeks(value).minusDays(1);
            case MONTHS -> start.plusMonths(value).minusDays(1);
            case YEARS -> start.plusYears(value).minusDays(1);
            default -> throw new IllegalArgumentException("Unsupported custom period unit: " + unit);
        };
        if (bucket.getPolicy() != null && bucket.getPolicy().getEndDate() != null && end.isAfter(bucket.getPolicy().getEndDate())) {
            end = bucket.getPolicy().getEndDate();
        }
        return new Period(start, end);
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

