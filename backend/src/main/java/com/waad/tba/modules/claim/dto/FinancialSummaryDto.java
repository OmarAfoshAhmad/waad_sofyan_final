package com.waad.tba.modules.claim.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Financial Summary DTO for reports.
 *
 * Provides aggregated financial KPIs for a set of claims.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialSummaryDto {
    private BigDecimal totalClaimsAmount;
    private BigDecimal totalApprovedAmount;
    private BigDecimal totalRefusedAmount;
    private BigDecimal totalPaidAmount;
    private BigDecimal outstandingAmount;
    private long claimsCount;
    private long approvedCount;
    private long settledCount;
}
