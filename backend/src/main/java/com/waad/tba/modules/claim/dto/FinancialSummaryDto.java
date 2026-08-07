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

    /**
     * Sum of Claim.companyDiscountAmount (contract discount / company profit)
     * across the same claim set as totalApprovedAmount — same status filter
     * (APPROVED/BATCHED/SETTLED). NOT derived from any fixed percentage; this
     * is the persisted per-claim value, matching the company profit report
     * and the financial consolidation matrix.
     */
    private BigDecimal totalCompanyDiscountAmount;

    private long claimsCount;
    private long approvedCount;
    private long settledCount;
}
