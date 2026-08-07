package com.waad.tba.modules.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProviderPaymentPostResultDto {
    Long paymentId;
    Long ledgerTransactionId;
    Long providerAccountId;
    BigDecimal amount;
    BigDecimal allocatedAmount;
    BigDecimal unallocatedAmount;
    BigDecimal balanceBefore;
    BigDecimal balanceAfter;
    LocalDateTime postedAt;
    String postedBy;
    Long paymentVersion;
    Long accountVersion;
    boolean idempotentReplay;
}
