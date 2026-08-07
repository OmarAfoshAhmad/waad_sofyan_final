package com.waad.tba.modules.settlement.service;

import static com.waad.tba.common.finance.Money.SCALE;
import static com.waad.tba.common.finance.Money.ZERO;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.BusinessRuleException;
import com.waad.tba.common.finance.Money;
import com.waad.tba.modules.settlement.dto.PaymentAllocationSuggestionDto;
import com.waad.tba.modules.settlement.dto.PaymentAllocationSuggestionDto.SuggestedAllocation;
import com.waad.tba.modules.settlement.entity.ProviderPaymentAllocation.AllocationMethod;
import com.waad.tba.modules.settlement.repository.ProviderAccountRepository;
import com.waad.tba.modules.settlement.repository.ProviderPaymentRepository;
import com.waad.tba.modules.settlement.repository.ProviderPaymentRepository.OutstandingPeriod;

import lombok.RequiredArgsConstructor;

/**
 * Pure preview of oldest-period-first allocation. It reads aggregate liability
 * and returns a deterministic proposal; it never saves a payment/allocation and
 * never debits the provider account.
 */
@Service
@RequiredArgsConstructor
public class ProviderPaymentAllocationSuggestionService {
    private final ProviderPaymentRepository payments;
    private final ProviderAccountRepository accounts;

    @Transactional(readOnly = true)
    public PaymentAllocationSuggestionDto suggest(Long providerId, BigDecimal paymentAmount, LocalDate asOfDate) {
        validate(providerId, paymentAmount, asOfDate);
        BigDecimal normalizedPayment = Money.normalize(paymentAmount);
        List<OutstandingBucket> outstanding = payments
                .findOutstandingPeriodsForAllocation(providerId, asOfDate).stream()
                .map(OutstandingBucket::from)
                .filter(bucket -> bucket.amount().signum() > 0)
                .sorted(OutstandingBucket.ORDER)
                .toList();
        Long accountVersion = accounts.findByProviderId(providerId).map(a -> a.getVersion()).orElse(null);
        return allocate(providerId, normalizedPayment, asOfDate, accountVersion, outstanding, Clock.systemDefaultZone());
    }

    static PaymentAllocationSuggestionDto allocate(Long providerId, BigDecimal paymentAmount,
            LocalDate asOfDate, Long accountVersion, List<OutstandingBucket> input, Clock clock) {
        List<OutstandingBucket> buckets = input.stream()
                .filter(b -> b != null && b.amount() != null && b.amount().signum() > 0)
                .map(b -> new OutstandingBucket(b.employerId(), b.year(), b.month(), Money.normalize(b.amount())))
                .sorted(OutstandingBucket.ORDER).toList();

        BigDecimal snapshotTotal = buckets.stream().map(OutstandingBucket::amount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal remaining = Money.normalize(paymentAmount);
        List<SuggestedAllocation> result = new ArrayList<>();
        int sequence = 1;

        for (int start = 0; start < buckets.size() && remaining.signum() > 0;) {
            int end = start + 1;
            while (end < buckets.size() && buckets.get(end).samePeriod(buckets.get(start))) end++;
            List<OutstandingBucket> period = buckets.subList(start, end);
            BigDecimal periodTotal = period.stream().map(OutstandingBucket::amount)
                    .reduce(ZERO, BigDecimal::add);

            if (remaining.compareTo(periodTotal) >= 0) {
                for (OutstandingBucket bucket : period) {
                    result.add(toDto(bucket, bucket.amount(), AllocationMethod.AUTO_FIFO, sequence++));
                }
                remaining = remaining.subtract(periodTotal);
            } else {
                for (AllocatedShare share : proportionalLargestRemainder(period, remaining)) {
                    if (share.amount().signum() > 0) {
                        result.add(toDto(share.bucket(), share.amount(),
                                AllocationMethod.AUTO_PROPORTIONAL, sequence++));
                    }
                }
                remaining = ZERO;
            }
            start = end;
        }

        BigDecimal allocated = result.stream().map(SuggestedAllocation::getSuggestedAmount)
                .reduce(ZERO, BigDecimal::add);
        return PaymentAllocationSuggestionDto.builder()
                .providerId(providerId).paymentAmount(Money.normalize(paymentAmount))
                .outstandingSnapshotTotal(Money.normalize(snapshotTotal))
                .allocatedTotal(Money.normalize(allocated))
                .unallocatedAmount(Money.normalize(paymentAmount.subtract(allocated)))
                .asOfDate(asOfDate).calculatedAt(LocalDateTime.now(clock))
                .accountVersion(accountVersion).allocations(List.copyOf(result)).build();
    }

    /** Integer-cents largest remainder: exact total, deterministic employer tie-break. */
    private static List<AllocatedShare> proportionalLargestRemainder(
            List<OutstandingBucket> buckets, BigDecimal amount) {
        long cents = toCents(amount);
        BigInteger totalCents = buckets.stream().map(b -> BigInteger.valueOf(toCents(b.amount())))
                .reduce(BigInteger.ZERO, BigInteger::add);
        List<ShareWork> work = new ArrayList<>();
        long floorTotal = 0;
        for (OutstandingBucket bucket : buckets) {
            long bucketCents = toCents(bucket.amount());
            BigInteger numerator = BigInteger.valueOf(cents).multiply(BigInteger.valueOf(bucketCents));
            BigInteger[] division = numerator.divideAndRemainder(totalCents);
            long floor = division[0].longValueExact();
            floorTotal += floor;
            work.add(new ShareWork(bucket, floor, division[1]));
        }
        long leftovers = cents - floorTotal;
        work.sort(Comparator.comparing(ShareWork::remainder).reversed()
                .thenComparing(w -> w.bucket().employerId()));
        for (int i = 0; i < leftovers; i++) work.get(i).addCent();
        work.sort(Comparator.comparing(w -> w.bucket(), OutstandingBucket.ORDER));
        return work.stream().map(w -> new AllocatedShare(w.bucket(), fromCents(w.cents()))).toList();
    }

    private static SuggestedAllocation toDto(OutstandingBucket bucket, BigDecimal amount,
            AllocationMethod method, int sequence) {
        return SuggestedAllocation.builder().employerId(bucket.employerId())
                .targetYear(bucket.year()).targetMonth(bucket.month())
                .outstandingAtAllocation(bucket.amount()).suggestedAmount(Money.normalize(amount))
                .allocationMethod(method).sequence(sequence).build();
    }

    private void validate(Long providerId, BigDecimal amount, LocalDate asOfDate) {
        if (providerId == null) throw new BusinessRuleException("مقدم الخدمة مطلوب لاقتراح التوزيع");
        if (amount == null || amount.signum() <= 0) throw new BusinessRuleException("مبلغ الدفعة يجب أن يكون موجباً");
        if (!Money.isExact(amount)) throw new BusinessRuleException("مبلغ الدفعة يجب ألا يتجاوز منزلتين عشريتين");
        if (asOfDate == null) throw new BusinessRuleException("تاريخ احتساب الاستحقاق مطلوب");
    }

    private static long toCents(BigDecimal value) {
        return Money.normalize(value).movePointRight(SCALE).longValueExact();
    }

    private static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, SCALE);
    }

    record OutstandingBucket(Long employerId, int year, int month, BigDecimal amount) {
        static final Comparator<OutstandingBucket> ORDER = Comparator
                .comparingInt(OutstandingBucket::year).thenComparingInt(OutstandingBucket::month)
                .thenComparing(OutstandingBucket::employerId);
        static OutstandingBucket from(OutstandingPeriod p) {
            return new OutstandingBucket(p.getEmployerId(), p.getTargetYear(), p.getTargetMonth(),
                    p.getOutstandingAmount());
        }
        boolean samePeriod(OutstandingBucket other) { return year == other.year && month == other.month; }
    }

    record AllocatedShare(OutstandingBucket bucket, BigDecimal amount) {}
    private record ShareWork(OutstandingBucket bucket, long[] mutableCents, BigInteger remainder) {
        ShareWork(OutstandingBucket bucket, long cents, BigInteger remainder) {
            this(bucket, new long[] { cents }, remainder);
        }
        long cents() { return mutableCents[0]; }
        void addCent() { mutableCents[0]++; }
    }
}
