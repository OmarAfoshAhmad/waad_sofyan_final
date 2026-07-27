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
    private final BenefitPolicyRepository benefitPolicyRepository;
    private final BenefitRuleBucketRepository ruleBucketRepository;
    private final BenefitLimitBucketRepository bucketRepository;
    private final BenefitBucketConsumptionRepository consumptionRepository;

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
        LocalDate serviceDate = claim.getServiceDate() == null ? LocalDate.now() : claim.getServiceDate();
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
        Set<Long> countedOnce = new HashSet<>();
        Set<Long> validatedDays = new HashSet<>();

        for (ClaimLine line : claim.getLines()) {
            if (line.getAppliedRuleId() == null || amount(line).signum() <= 0) continue;
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
                int times = consumedTimes(bucket, line, countedOnce);
                BigDecimal consumedAmount = bucket.getConsumptionBasis() == ConsumptionBasis.COMPANY_SHARE
                        ? amount(line) : eligibleAmount(line);
                validateAvailableBalance(bucket, memberId, serviceDate, period, consumedAmount, times,
                        validatedDays.add(bucket.getId()));
                consumptionRepository.save(BenefitBucketConsumption.builder()
                        .claim(claim).claimLine(line).policy(policy).memberId(memberId).bucket(bucket)
                        .periodStart(period.start()).periodEnd(period.end())
                        .approvedAmount(consumedAmount).timesConsumed(times)
                        .status(BenefitBucketConsumption.Status.COMMITTED)
                        .calculationVersion(line.getCalculationVersion()).idempotencyKey(key)
                        .committedAt(LocalDateTime.now()).build());
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
        BigDecimal previouslyUsed = claimRepository.sumApprovedAmountsByMemberAndYearExcludingClaim(
                memberId, yearStart, yearEnd, claim.getId());
        BigDecimal current = Optional.ofNullable(claim.getApprovedAmount()).orElse(BigDecimal.ZERO);
        if (previouslyUsed.add(current).compareTo(lockedPolicy.getAnnualLimit()) > 0) {
            throw new BusinessRuleException("تغير الرصيد أثناء الاعتماد وتجاوز السقف العام السنوي للوثيقة. أعد احتساب المطالبة ثم حاول مجددًا.");
        }
    }

    private BenefitPolicy resolvePolicy(Claim claim, LocalDate serviceDate) {
        BenefitPolicy direct = claim.getMember().getBenefitPolicy();
        if (direct != null) return direct;
        if (claim.getMember().getEmployer() == null) return null;
        return benefitPolicyRepository.findActiveEffectivePolicyForEmployer(
                claim.getMember().getEmployer().getId(), serviceDate).orElse(null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reverseClaim(Long claimId) {
        for (BenefitBucketConsumption original : consumptionRepository.findByClaimIdAndStatus(
                claimId, BenefitBucketConsumption.Status.COMMITTED)) {
            String key = original.getIdempotencyKey() + ":REVERSAL";
            if (consumptionRepository.existsByIdempotencyKey(key)) continue;
            bucketRepository.findByIdForUpdate(original.getBucket().getId()).orElseThrow();
            LocalDateTime now = LocalDateTime.now();
            original.setStatus(BenefitBucketConsumption.Status.REVERSED);
            original.setReversedAt(now);
            consumptionRepository.save(original);
            consumptionRepository.save(BenefitBucketConsumption.builder()
                    .claim(original.getClaim()).claimLine(original.getClaimLine()).policy(original.getPolicy())
                    .memberId(original.getMemberId()).bucket(original.getBucket())
                    .periodStart(original.getPeriodStart()).periodEnd(original.getPeriodEnd())
                    .approvedAmount(original.getApprovedAmount()).timesConsumed(original.getTimesConsumed())
                    .status(BenefitBucketConsumption.Status.REVERSED)
                    .calculationVersion(original.getCalculationVersion()).idempotencyKey(key)
                    .reversalOf(original).reversedAt(now).build());
        }
    }

    private void addWithParents(BenefitLimitBucket bucket, Map<Long, BenefitLimitBucket> target) {
        BenefitLimitBucket current = bucket;
        while (current != null) {
            target.putIfAbsent(current.getId(), current);
            current = current.getParentBucket();
        }
    }

    private int consumedTimes(BenefitLimitBucket bucket, ClaimLine line, Set<Long> countedOnce) {
        CountingMethod method = bucket.getCountingMethod() != null
                ? bucket.getCountingMethod()
                : CountingMethod.EACH_LINE;
        return switch (method) {
            case EACH_UNIT -> Math.max(1, line.getQuantity() == null ? 1 : line.getQuantity());
            case PER_VISIT, PER_DAY -> countedOnce.add(bucket.getId()) ? 1 : 0;
            case EACH_LINE -> 1;
        };
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
        LimitPeriodType periodType = bucket.getPeriodType() != null
                ? bucket.getPeriodType()
                : LimitPeriodType.ANNUAL;
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
            case MULTI_YEAR_POLICY -> {
                int years = bucket.getPeriodValue() == null ? 1 : Math.max(1, bucket.getPeriodValue());
                long elapsed = Math.max(0, ChronoUnit.YEARS.between(policy.getStartDate(), date));
                LocalDate start = policy.getStartDate().plusYears((elapsed / years) * years);
                LocalDate end = start.plusYears(years).minusDays(1);
                if (policy.getEndDate() != null && end.isAfter(policy.getEndDate())) end = policy.getEndDate();
                yield new Period(start, end);
            }
            case CUSTOM_DAYS -> customPeriod(policy, date, bucket.getPeriodValue(), ChronoUnit.DAYS);
            case CUSTOM_WEEKS -> customPeriod(policy, date, bucket.getPeriodValue(), ChronoUnit.WEEKS);
            case CUSTOM_MONTHS -> customPeriod(policy, date, bucket.getPeriodValue(), ChronoUnit.MONTHS);
            case CUSTOM_YEARS -> customPeriod(policy, date, bucket.getPeriodValue(), ChronoUnit.YEARS);
            case POLICY_PERIOD -> policy != null && policy.getStartDate() != null
                    ? new Period(policy.getStartDate(), policy.getEndDate())
                    : new Period(LocalDate.of(date.getYear(), 1, 1), LocalDate.of(date.getYear(), 12, 31));
            case LIFETIME -> new Period(LocalDate.of(1900, 1, 1), null);
        };
    }

    private Period customPeriod(BenefitPolicy policy, LocalDate date, Integer periodValue, ChronoUnit unit) {
        LocalDate anchor = policy != null && policy.getStartDate() != null
                ? policy.getStartDate()
                : LocalDate.of(date.getYear(), 1, 1);
        int value = periodValue == null ? 1 : Math.max(1, periodValue);
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
        if (policy != null && policy.getEndDate() != null && end.isAfter(policy.getEndDate())) end = policy.getEndDate();
        return new Period(start, end);
    }

    private record Period(LocalDate start, LocalDate end) {}
}

