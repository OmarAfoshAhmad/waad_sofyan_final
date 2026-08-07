package com.waad.tba.modules.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.waad.tba.modules.settlement.entity.ProviderPaymentAllocation.AllocationMethod;

import lombok.Builder;
import lombok.Value;

/** Read-only FIFO preview. Creating it has no database side effects. */
@Value
@Builder
public class PaymentAllocationSuggestionDto {
    Long providerId;
    BigDecimal paymentAmount;
    BigDecimal outstandingSnapshotTotal;
    BigDecimal allocatedTotal;
    BigDecimal unallocatedAmount;
    LocalDate asOfDate;
    LocalDateTime calculatedAt;
    Long accountVersion;
    List<SuggestedAllocation> allocations;

    @Value
    @Builder
    public static class SuggestedAllocation {
        Long employerId;
        Integer targetYear;
        Integer targetMonth;
        BigDecimal outstandingAtAllocation;
        BigDecimal suggestedAmount;
        AllocationMethod allocationMethod;
        Integer sequence;
    }
}
