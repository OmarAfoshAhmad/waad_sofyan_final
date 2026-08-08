package com.waad.tba.modules.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.waad.tba.modules.settlement.entity.PaymentMethod;
import com.waad.tba.modules.settlement.entity.ProviderPaymentAllocation.AllocationMethod;

import lombok.Data;

/**
 * A DRAFT payment as the accountant is preparing it — typically the accepted
 * (possibly edited) FIFO suggestion. Allocations need not sum to amount at this
 * stage; the DB only requires allocated &lt;= amount, and posting is where full
 * allocation actually matters.
 */
@Data
public class CreateProviderPaymentRequest {
    private Long providerId;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private PaymentMethod paymentMethod;
    private String referenceNumber;
    private String notes;
    private List<AllocationInput> allocations;

    @Data
    public static class AllocationInput {
        private Long employerId;
        private Integer targetYear;
        private Integer targetMonth;
        private BigDecimal amount;
        private BigDecimal outstandingAtAllocation;
        private AllocationMethod allocationMethod;
        /** Required by the database whenever allocationMethod is MANUAL. */
        private String overrideReason;
    }
}
