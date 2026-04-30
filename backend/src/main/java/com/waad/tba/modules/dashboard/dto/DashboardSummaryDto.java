package com.waad.tba.modules.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Dashboard Summary DTO
 * 
 * Aggregated statistics for dashboard KPIs.
 * All calculations done server-side using JPQL aggregations.
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDto {
    
    /**
     * Total members count
     */
    private Long totalMembers;
    
    /**
     * Active members count (status = 'ACTIVE')
     */
    private Long activeMembers;
    
    /**
     * Total claims count
     */
    private Long totalClaims;
    
    /**
     * Open claims (PENDING, PENDING_REVIEW)
     */
    private Long openClaims;
    
    /**
     * Approved claims (APPROVED, SETTLED)
     */
    private Long approvedClaims;
    
    /**
     * Rejected claims (REJECTED)
     */
    private Long rejectedClaims;
    
    /**
     * Total providers count
     */
    private Long totalProviders;
    
    /**
     * Active providers count
     */
    private Long activeProviders;
    
    /**
     * Total contracts count
     */
    private Long totalContracts;
    
    /**
     * Active contracts count
     */
    private Long activeContracts;
    
    /**
     * Total medical cost (sum of approved amounts)
     */
    private BigDecimal totalMedicalCost;
    
    /**
     * Monthly growth percentage (compared to previous month)
     */
    private BigDecimal monthlyGrowth;
}

