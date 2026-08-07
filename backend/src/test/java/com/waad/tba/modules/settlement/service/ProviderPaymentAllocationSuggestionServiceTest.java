package com.waad.tba.modules.settlement.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.waad.tba.modules.settlement.entity.ProviderPaymentAllocation.AllocationMethod;
import com.waad.tba.modules.settlement.service.ProviderPaymentAllocationSuggestionService.OutstandingBucket;

class ProviderPaymentAllocationSuggestionServiceTest {
    private static final LocalDate AS_OF = LocalDate.of(2026, 8, 7);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-07T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void closesOldestPeriodBeforeMovingToTheNext() {
        var result = allocate("600.00",
                bucket(1, 2026, 1, "300.00"), bucket(2, 2026, 1, "200.00"),
                bucket(1, 2026, 2, "400.00"), bucket(3, 2026, 3, "100.00"));

        assertThat(result.getAllocations()).extracting(a -> a.getTargetMonth())
                .containsExactly(1, 1, 2);
        assertThat(result.getAllocations()).extracting(a -> a.getSuggestedAmount())
                .containsExactly(bd("300.00"), bd("200.00"), bd("100.00"));
        assertThat(result.getAllocatedTotal()).isEqualByComparingTo("600.00");
        assertThat(result.getUnallocatedAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void partialOldestPeriodIsProportionalAndDoesNotTouchNewerPeriods() {
        var result = allocate("30.00",
                bucket(1, 2026, 1, "75.00"), bucket(2, 2026, 1, "25.00"),
                bucket(3, 2026, 2, "900.00"));

        assertThat(result.getAllocations()).hasSize(2);
        assertThat(result.getAllocations()).extracting(a -> a.getSuggestedAmount())
                .containsExactly(bd("22.50"), bd("7.50"));
        assertThat(result.getAllocations()).allMatch(a -> a.getAllocationMethod() == AllocationMethod.AUTO_PROPORTIONAL);
    }

    @Test
    void largestRemainderDistributesEveryCentExactly() {
        var result = allocate("0.02",
                bucket(10, 2026, 1, "1.00"), bucket(20, 2026, 1, "1.00"),
                bucket(30, 2026, 1, "1.00"));

        assertThat(result.getAllocations()).extracting(a -> a.getSuggestedAmount())
                .containsExactly(bd("0.01"), bd("0.01"));
        assertThat(result.getAllocations()).extracting(a -> a.getEmployerId())
                .containsExactly(10L, 20L); // equal remainders: employerId is deterministic tie-break
        assertThat(result.getAllocatedTotal()).isEqualByComparingTo("0.02");
    }

    @Test
    void neverAllocatesMoreThanAnyBucketOutstanding() {
        var result = allocate("99.99",
                bucket(1, 2026, 1, "0.01"), bucket(2, 2026, 1, "99.99"));

        assertThat(result.getAllocations().get(0).getSuggestedAmount()).isLessThanOrEqualTo(
                result.getAllocations().get(0).getOutstandingAtAllocation());
        assertThat(result.getAllocations().get(1).getSuggestedAmount()).isLessThanOrEqualTo(
                result.getAllocations().get(1).getOutstandingAtAllocation());
        assertThat(result.getAllocatedTotal()).isEqualByComparingTo("99.99");
    }

    @Test
    void excessPaymentRemainsVisibleAsUnallocated() {
        var result = allocate("150.00", bucket(1, 2026, 1, "100.00"));

        assertThat(result.getAllocatedTotal()).isEqualByComparingTo("100.00");
        assertThat(result.getUnallocatedAmount()).isEqualByComparingTo("50.00");
        assertThat(result.getOutstandingSnapshotTotal()).isEqualByComparingTo("100.00");
    }

    @Test
    void noOutstandingLeavesTheWholePaymentUnallocated() {
        var result = allocate("80.00");

        assertThat(result.getAllocations()).isEmpty();
        assertThat(result.getAllocatedTotal()).isEqualByComparingTo("0.00");
        assertThat(result.getUnallocatedAmount()).isEqualByComparingTo("80.00");
    }

    @Test
    void sameInputsProduceTheSameOrderedProposal() {
        var input = new OutstandingBucket[] {
                bucket(30, 2026, 1, "10.00"), bucket(10, 2026, 1, "10.00"),
                bucket(20, 2026, 1, "10.00") };
        var first = allocate("10.01", input);
        var second = allocate("10.01", input);

        assertThat(second.getAllocations()).isEqualTo(first.getAllocations());
        assertThat(second.getCalculatedAt()).isEqualTo(first.getCalculatedAt());
    }

    @Test
    void snapshotAndAccountVersionAreReturnedForPostingRevalidation() {
        var result = ProviderPaymentAllocationSuggestionService.allocate(55L, bd("10.00"), AS_OF, 7L,
                List.of(bucket(1, 2026, 1, "20.00")), CLOCK);

        assertThat(result.getProviderId()).isEqualTo(55L);
        assertThat(result.getAccountVersion()).isEqualTo(7L);
        assertThat(result.getAsOfDate()).isEqualTo(AS_OF);
    }

    private static com.waad.tba.modules.settlement.dto.PaymentAllocationSuggestionDto allocate(
            String amount, OutstandingBucket... buckets) {
        return ProviderPaymentAllocationSuggestionService.allocate(55L, bd(amount), AS_OF, 7L,
                List.of(buckets), CLOCK);
    }

    private static OutstandingBucket bucket(long employer, int year, int month, String amount) {
        return new OutstandingBucket(employer, year, month, bd(amount));
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
