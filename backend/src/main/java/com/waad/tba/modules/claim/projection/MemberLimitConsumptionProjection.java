package com.waad.tba.modules.claim.projection;

import java.math.BigDecimal;

/**
 * One member's limit-consumption total for a period -- the row shape behind
 * {@code ClaimRepository.sumLimitConsumptionByMembersAndPeriodExcludingClaim}.
 * This is the WAAD-FIN-1.0 axis ({@code ClaimLine.limitConsumption}, i.e.
 * settlement value inside the binding limit), never
 * {@code Claim.approvedAmount} -- see
 * {@link MemberFinancialAggregateProjection}'s javadoc for why the two must
 * not be conflated.
 */
public interface MemberLimitConsumptionProjection {

    Long getMemberId();

    BigDecimal getConsumedAmount();
}
