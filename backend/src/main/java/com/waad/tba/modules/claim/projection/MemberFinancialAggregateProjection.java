package com.waad.tba.modules.claim.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One member's full claim-history aggregate, computed entirely in the
 * database. Backs {@code ClaimRepository.findFinancialAggregatesByMemberIds}
 * — the single query that replaced Member{@code FinancialSummaryService}'s
 * former pattern of loading every claim for a member into memory and
 * reducing it with Java streams (a per-member query that, unbounded, cost a
 * family-eligibility check one full claim-history load per family member).
 *
 * Every sum here is {@code Claim.approvedAmount}/{@code requestedAmount}-axis
 * money: what the insurer approved/requested, not what a benefit limit
 * consumed. For "how much of the annual limit is used", see
 * {@link com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService#getLimitConsumedForYear}
 * instead — a claim's approvedAmount and its limitConsumption are different
 * numbers by construction (WAAD-FIN-1.0 S4), and conflating them was exactly
 * the member-window defect this projection's introduction fixed.
 */
public interface MemberFinancialAggregateProjection {

    Long getMemberId();

    Long getClaimsCount();

    Long getPendingClaimsCount();

    Long getApprovedClaimsCount();

    Long getRejectedClaimsCount();

    BigDecimal getTotalClaimed();

    BigDecimal getTotalApproved();

    BigDecimal getTotalPaid();

    BigDecimal getTotalPatientCoPay();

    BigDecimal getTotalDeductibleApplied();

    LocalDateTime getLastClaimAt();
}
