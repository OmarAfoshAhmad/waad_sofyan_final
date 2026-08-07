package com.waad.tba.modules.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProviderPaymentReversalResultDto {
    Long paymentId;
    Long originalLedgerTransactionId;
    Long reversalLedgerTransactionId;
    Long providerAccountId;
    BigDecimal amount;
    BigDecimal balanceBefore;
    BigDecimal balanceAfter;
    /** Current account value; on replay it may include movements after this reversal. */
    BigDecimal currentTotalPaid;
    int affectedAllocationCount;
    BigDecimal affectedAllocatedAmount;
    LocalDateTime reversedAt;
    String reversedBy;
    String reversalReason;
    Long paymentVersion;
    Long accountVersion;
    boolean idempotentReplay;
}
