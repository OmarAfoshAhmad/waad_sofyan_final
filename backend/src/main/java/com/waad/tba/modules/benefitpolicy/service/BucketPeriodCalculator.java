package com.waad.tba.modules.benefitpolicy.service;

import com.waad.tba.modules.benefitpolicy.entity.BenefitLimitBucket;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy;
import com.waad.tba.modules.benefitpolicy.enums.LimitPeriodType;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * Single source of truth for resolving a bucket's consumption period window,
 * shared by {@link BenefitBucketLedgerService} and
 * {@link BenefitBucketLimitService}.
 *
 * Extracted 2026-07-27: both services previously carried their own copy of
 * this logic, and WEEKLY/QUARTERLY were anchored to the calendar (Saturday
 * of the ISO week, January-based quarters) instead of the policy's own start
 * date — the same rolling-window anchor already used correctly for
 * CUSTOM_WEEKS/CUSTOM_MONTHS. For a policy that doesn't start in January (or
 * on a Saturday), that meant WEEKLY/QUARTERLY buckets reset on a boundary
 * that had nothing to do with when the policy actually began. WEEKLY is now
 * a fixed 7-day custom period and QUARTERLY a fixed 3-month custom period,
 * both anchored to the policy start date like every other rolling window.
 */
final class BucketPeriodCalculator {

    private BucketPeriodCalculator() {
    }

    record Period(LocalDate start, LocalDate end) {
    }

    static Period resolve(LimitPeriodType periodTypeOrNull, Integer periodValue, BenefitPolicy policy, LocalDate date) {
        LimitPeriodType periodType = periodTypeOrNull != null ? periodTypeOrNull : LimitPeriodType.ANNUAL;
        return switch (periodType) {
            case PER_SERVICE, PER_VISIT, DAILY -> new Period(date, date);
            case WEEKLY -> customPeriod(policy, date, 1, ChronoUnit.WEEKS);
            case MONTHLY -> new Period(date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth()));
            case QUARTERLY -> customPeriod(policy, date, 3, ChronoUnit.MONTHS);
            case ANNUAL -> new Period(LocalDate.of(date.getYear(), 1, 1), LocalDate.of(date.getYear(), 12, 31));
            case MULTI_YEAR_POLICY -> multiYearPolicyPeriod(policy, date, periodValue);
            case CUSTOM_DAYS -> customPeriod(policy, date, periodValue, ChronoUnit.DAYS);
            case CUSTOM_WEEKS -> customPeriod(policy, date, periodValue, ChronoUnit.WEEKS);
            case CUSTOM_MONTHS -> customPeriod(policy, date, periodValue, ChronoUnit.MONTHS);
            case CUSTOM_YEARS -> customPeriod(policy, date, periodValue, ChronoUnit.YEARS);
            case POLICY_PERIOD -> policy != null && policy.getStartDate() != null
                    ? new Period(policy.getStartDate(), policy.getEndDate())
                    : new Period(LocalDate.of(date.getYear(), 1, 1), LocalDate.of(date.getYear(), 12, 31));
            case LIFETIME -> new Period(LocalDate.of(1900, 1, 1), null);
        };
    }

    static Period resolve(BenefitLimitBucket bucket, BenefitPolicy policy, LocalDate date) {
        return resolve(bucket.getPeriodType(), bucket.getPeriodValue(), policy, date);
    }

    private static Period multiYearPolicyPeriod(BenefitPolicy policy, LocalDate date, Integer periodValue) {
        LocalDate anchor = policy.getStartDate();
        int years = periodValue == null ? 1 : Math.max(1, periodValue);
        long elapsed = Math.max(0, ChronoUnit.YEARS.between(anchor, date));
        LocalDate start = anchor.plusYears((elapsed / years) * years);
        LocalDate end = start.plusYears(years).minusDays(1);
        if (policy.getEndDate() != null && end.isAfter(policy.getEndDate())) {
            end = policy.getEndDate();
        }
        return new Period(start, end);
    }

    private static Period customPeriod(BenefitPolicy policy, LocalDate date, Integer periodValue, ChronoUnit unit) {
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
        if (policy != null && policy.getEndDate() != null && end.isAfter(policy.getEndDate())) {
            end = policy.getEndDate();
        }
        return new Period(start, end);
    }
}
