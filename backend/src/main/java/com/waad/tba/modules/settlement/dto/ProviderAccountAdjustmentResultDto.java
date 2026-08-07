package com.waad.tba.modules.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

/** Outcome of an approved correction to a provider account's paid total. */
@Data
@Builder
public class ProviderAccountAdjustmentResultDto {

    private Long providerId;
    private Long providerAccountId;
    private Long ledgerTransactionId;

    /** Signed: positive raised totalPaid, negative lowered it. */
    private BigDecimal adjustmentAmount;

    private BigDecimal totalPaidBefore;
    private BigDecimal totalPaidAfter;
    private BigDecimal runningBalanceBefore;
    private BigDecimal runningBalanceAfter;

    /** The drift measured before the correction — what justified it. */
    private BigDecimal ledgerVsAccountDriftBefore;

    /** Zero when the correction fully closed the gap. */
    private BigDecimal ledgerVsAccountDriftAfter;

    private String reason;
    private String performedBy;
    private LocalDateTime performedAt;
}
