package com.waad.tba.modules.settlement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Account Summary DTO
 * 
 * Provides a comprehensive view of a provider's financial account.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountSummaryDTO {

    private Long accountId;
    private Long providerId;

    /**
     * Provider name from Provider entity
     */
    private String providerName;

    /**
     * Current running balance (amount owed to provider)
     * INVARIANT: runningBalance = totalApproved - totalPaid
     */
    private BigDecimal runningBalance;

    /**
     * Total amount approved across all claims
     */
    private BigDecimal totalApproved;

    /**
     * Total amount paid across all batches
     */
    private BigDecimal totalPaid;

    /**
     * Account status (ACTIVE, SUSPENDED, CLOSED)
     */
    private String status;

    /**
     * Status in Arabic
     */
    private String statusArabic;

    /**
     * Total number of transactions
     */
    private long transactionCount;

    /**
     * Whether balance matches transaction history
     * Should always be true in a healthy system
     */
    private boolean balanceVerified;

    /**
     * Whether runningBalance == totalApproved - totalPaid
     * Second integrity check - formula invariant
     */
    private boolean formulaVerified;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
