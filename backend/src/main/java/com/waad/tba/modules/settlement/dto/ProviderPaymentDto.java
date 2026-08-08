package com.waad.tba.modules.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.waad.tba.modules.settlement.entity.PaymentMethod;
import com.waad.tba.modules.settlement.entity.ProviderPayment;
import com.waad.tba.modules.settlement.entity.ProviderPayment.Status;
import com.waad.tba.modules.settlement.entity.ProviderPaymentAllocation.AllocationMethod;

import lombok.Builder;
import lombok.Value;

/** Read view of a ProviderPayment and its allocations — the payment document. */
@Value
@Builder
public class ProviderPaymentDto {
    Long id;
    Long providerId;
    BigDecimal amount;
    LocalDate paymentDate;
    PaymentMethod paymentMethod;
    String referenceNumber;
    String idempotencyKey;
    Status status;
    String notes;
    BigDecimal allocatedAmount;
    BigDecimal unallocatedAmount;
    boolean fullyAllocated;
    Long ledgerTransactionId;
    Long reversalLedgerTransactionId;
    LocalDateTime postedAt;
    String postedBy;
    LocalDateTime reversedAt;
    String reversedBy;
    String reversalReason;
    Long version;
    List<AllocationView> allocations;

    @Value
    @Builder
    public static class AllocationView {
        Long id;
        Long employerId;
        Integer targetYear;
        Integer targetMonth;
        BigDecimal amount;
        BigDecimal outstandingAtAllocation;
        AllocationMethod allocationMethod;
        String overrideReason;
    }

    public static ProviderPaymentDto from(ProviderPayment payment) {
        return ProviderPaymentDto.builder()
                .id(payment.getId())
                .providerId(payment.getProviderId())
                .amount(payment.getAmount())
                .paymentDate(payment.getPaymentDate())
                .paymentMethod(payment.getPaymentMethod())
                .referenceNumber(payment.getReferenceNumber())
                .idempotencyKey(payment.getIdempotencyKey())
                .status(payment.getStatus())
                .notes(payment.getNotes())
                .allocatedAmount(payment.getAllocatedAmount())
                .unallocatedAmount(payment.getUnallocatedAmount())
                .fullyAllocated(payment.isFullyAllocated())
                .ledgerTransactionId(payment.getLedgerTransactionId())
                .reversalLedgerTransactionId(payment.getReversalLedgerTransactionId())
                .postedAt(payment.getPostedAt())
                .postedBy(payment.getPostedBy())
                .reversedAt(payment.getReversedAt())
                .reversedBy(payment.getReversedBy())
                .reversalReason(payment.getReversalReason())
                .version(payment.getVersion())
                .allocations(payment.getAllocations().stream()
                        .map(a -> AllocationView.builder()
                                .id(a.getId())
                                .employerId(a.getEmployerId())
                                .targetYear(a.getTargetYear())
                                .targetMonth(a.getTargetMonth())
                                .amount(a.getAmount())
                                .outstandingAtAllocation(a.getOutstandingAtAllocation())
                                .allocationMethod(a.getAllocationMethod())
                                .overrideReason(a.getOverrideReason())
                                .build())
                        .toList())
                .build();
    }
}
