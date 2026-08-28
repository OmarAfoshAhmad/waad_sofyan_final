package com.waad.tba.modules.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.waad.tba.modules.member.dto.CurrentGeneralLimitSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
     * The date these ceiling figures answer for, and the instant they were
     * read. Both are the server's; no caller supplies a date.
     *
     * Carried so two surfaces showing the same member can be compared
     * honestly. A claim approved between one read and the next makes their
     * figures differ correctly, and without a read timestamp that difference
     * looks like one of the screens being wrong.
     */
    private LocalDate asOfDate;

    private LocalDateTime readAt;

    /**
     * Whether there are figures at all. Every non-FOUND mode carries nulls
     * rather than zeroes, because a zero on a balance screen reads as a spent
     * ceiling.
     */
    private CurrentGeneralLimitSummary.Mode ceilingMode;

    /**
     * Total amount claimed (sum of requestedAmount).
     *
     * @deprecated on the claim-approval axis, and no screen reads it. Nulled
     *             rather than served: a number that is right about claims is
     *             routinely mistaken for a number about the ceiling, and this
     *             field has been made that mistake before. Ask the claims
     *             module for claim totals.
     */
    @Deprecated
    private BigDecimal totalClaimed;

    /**
     * Total amount approved by the insurer.
     *
     * @deprecated see {@link #totalClaimed}. Nulled, not zeroed.
     */
    @Deprecated
    private BigDecimal totalApproved;

    /**
     * @deprecated never a real disbursement. It summed approvedAmount over
     *             claims whose STATUS was SETTLED, so it reported money as
     *             paid on the strength of a status change and could not see a
     *             partial payment at all. It is nulled rather than repaired
     *             because it cannot be repaired here: payments are recorded
     *             per (employer, provider, year, month) and carry no member,
     *             and only individually-settled claims carry paidAmount --
     *             the batch path and the generic status path leave it unset.
     *             Attributing a monthly lump sum down to one member would be
     *             an invented allocation. See {@link #claimPaymentAttribution}.
     */
    @Deprecated
    private BigDecimal totalPaid;

    /**
     * Says out loud why {@link #totalPaid} is null, so a reader is not left to
     * guess whether the member simply has no payments.
     */
    private ClaimPaymentAttribution claimPaymentAttribution;

    /**
     * What this member has consumed against their annual ceiling -- the
     * settled value that actually reduced the limit, read from the ledger by
     * LimitBalanceReader. Not {@link #totalApproved}: a limit is consumed
     * before coverage split, contract discount, and rejection are applied.
     */
    private BigDecimal limitConsumedAmount;

    /** Money held by approved pre-authorizations, not yet spent. */
    private BigDecimal reservedAmount;

    /**
     * {@code annualLimit - committed}. Signed: an overspend stays negative,
     * because clamping it to zero hides the one case a reconciler is looking
     * for. The accounting view -- what has actually been consumed.
     */
    private BigDecimal actualRemaining;

    /**
     * {@code actualRemaining - reserved}. Signed, for the same reason.
     *
     * The figure a NEW commitment must be judged against, and the one a claim
     * or eligibility screen must show: money already held by an approved
     * pre-authorization is not available to commit again, and committing it
     * twice is exactly what the hold exists to prevent.
     */
    private BigDecimal reservableAvailable;

    /**
     * @deprecated ambiguous by name and, until this change, clamped at zero so
     *             an overspent member read as exactly spent. Kept as an
     *             unclamped alias of {@link #actualRemaining} while consumers
     *             migrate; new code must name which of the two it means.
     */
    @Deprecated
    private BigDecimal remainingCoverage;

    /**
     * {@code (limitConsumedAmount / annualLimit) * 100} -- the share actually
     * consumed. Deliberately not a measure of what is unavailable: it must
     * never be used alone to fill a bar labelled "available", because a large
     * hold makes those two numbers far apart.
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

    /**
     * Why no per-member payment figure is served.
     *
     * A single value today, and that is the point: it states a property of the
     * system rather than of the member. Building real attribution needs an
     * explicit Payment/Allocation -> Claim -> Member relation, which is a
     * separate piece of work and not something to approximate from a monthly
     * lump sum.
     */
    public enum ClaimPaymentAttribution {
        /** Payments are not recorded per member anywhere in the system. */
        NOT_SUPPORTED
    }
}
