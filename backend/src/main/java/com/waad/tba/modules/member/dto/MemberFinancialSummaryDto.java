package com.waad.tba.modules.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Member Financial Summary DTO
 * 
 * Provides comprehensive financial overview for a member including:
 * - Policy information
 * - Utilization metrics
 * - Claim statistics
 * 
 * PHASE 1: Critical endpoint for financial visibility
 * 
 * @version 2026.1
 * @since Phase 1 - Financial Lifecycle Completion
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberFinancialSummaryDto {

    // ==================== MEMBER INFO ====================
    
    /**
     * Member ID
     */
    private Long memberId;
    
    /**
     * Full name (Arabic)
     */
    private String fullName;
    
    /**
     * Card number (for display)
     */
    private String cardNumber;
    
    /**
     * Barcode (for Principal members)
     */
    private String barcode;
    
    /**
     * Is this member a dependent?
     */
    private Boolean isDependent;
    
    // ==================== POLICY INFO ====================
    
    /**
     * Benefit policy ID
     */
    private Long policyId;
    
    /**
     * Benefit policy name
     */
    private String policyName;
    
    /**
     * Annual coverage limit from policy
     */
    private BigDecimal annualLimit;
    
    /**
     * Policy start date
     */
    private LocalDate policyStartDate;
    
    /**
     * Policy end date
     */
    private LocalDate policyEndDate;
    
    /**
     * Is policy active?
     */
    private Boolean policyActive;
    
    // ==================== FINANCIAL METRICS ====================
    
    /**
     * Total amount claimed (sum of all requestedAmount)
     */
    private BigDecimal totalClaimed;
    
    /**
     * Total amount approved by insurance (sum of approvedAmount) -- what the
     * insurer ultimately pays. A distinct, legitimate metric from
     * {@link #limitConsumedAmount}: do not use this one for "how much of the
     * annual limit is used" or for {@link #remainingCoverage}. See
     * {@link #limitConsumedAmount}'s doc for why the two differ.
     */
    private BigDecimal totalApproved;

    /**
     * Total amount paid/settled (sum of claims with status PAID/SETTLED)
     */
    private BigDecimal totalPaid;

    /**
     * What this member has consumed against their annual benefit ceiling this
     * year -- WAAD-FIN-1.0 S4's axis ({@code ClaimLine.limitConsumption}: the
     * settlement value capped by the binding limit, summed across every
     * approved/settled/batched claim line). This is <b>not</b>
     * {@link #totalApproved}: a limit is consumed before coverage split,
     * contract discount, and rejection are applied on top of it, so
     * limitConsumedAmount is always &gt;= totalApproved for the same claim.
     * "المستخدم من السقف" in any UI must read this field, never totalApproved
     * -- see {@code BenefitPolicyCoverageService.getLimitConsumedForYear}.
     */
    private BigDecimal limitConsumedAmount;

    /**
     * Remaining coverage: {@code annualLimit - limitConsumedAmount}. Computed
     * on the same axis limitConsumedAmount is, per WAAD-FIN-1.0 S4 -- never
     * derived from totalApproved.
     */
    private BigDecimal remainingCoverage;

    /**
     * Utilization percentage: {@code (limitConsumedAmount / annualLimit) * 100}.
     */
    private BigDecimal utilizationPercent;
    
    // ==================== CLAIM STATISTICS ====================
    
    /**
     * Total number of claims
     */
    private Integer claimsCount;
    
    /**
     * Number of pending claims
     */
    private Integer pendingClaimsCount;
    
    /**
     * Number of approved claims
     */
    private Integer approvedClaimsCount;
    
    /**
     * Number of rejected claims
     */
    private Integer rejectedClaimsCount;
    
    /**
     * Date of last claim submission
     */
    private LocalDate lastClaimDate;
    
    // ==================== PATIENT RESPONSIBILITY ====================
    
    /**
     * Total patient co-pay across all approved claims
     */
    private BigDecimal totalPatientCoPay;
    
    /**
     * Total deductible applied across all approved claims
     */
    private BigDecimal totalDeductibleApplied;
    
    // ==================== WARNINGS / ALERTS ====================
    
    /**
     * Warning message if coverage is low or expired
     */
    private String warningMessage;
    
    /**
     * Indicates if member is close to annual limit (>80% utilization)
     */
    private Boolean nearingLimit;
    
    /**
     * Indicates if policy is expiring soon (within 30 days)
     */
    private Boolean policyExpiringSoon;
}
