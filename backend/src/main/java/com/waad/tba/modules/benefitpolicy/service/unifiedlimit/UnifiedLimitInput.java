package com.waad.tba.modules.benefitpolicy.service.unifiedlimit;

import com.waad.tba.modules.benefitpolicy.enums.CountingMethod;
import com.waad.tba.modules.providercontract.enums.EncounterType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * P1.4.1: the call contract a {@code UnifiedLimitResolver} is built from.
 * Every field mirrors an actual parameter at one of the three live call
 * sites inventoried in P1.4.0 (CoverageDecisionService,
 * ClaimFinancialAdjudicationService, PreAuthorizationDecisionBuilder) --
 * none is speculative.
 *
 * This is the input, not the decision. Same input must always produce the
 * same decision (P1's closure rule) -- keeping the two separate is what
 * makes that directly testable.
 */
public record UnifiedLimitInput(
        Long policyId,
        Long ruleId,
        Long memberId,
        LocalDate serviceDate,
        EncounterType encounterType,

        int requestedQuantity,
        int requestedDays,
        /**
         * Not named in P1.3/P1.4.1 -- found missing while writing this
         * skeleton (P1.4.2). Only EACH_UNIT is divisible (DivisibleLimitSplitter,
         * P2); every other method is one atomic occurrence, exactly like days
         * always are. Needed to reproduce G2/G4 vs G3 correctly, so it is
         * added here rather than hard-coding EACH_UNIT -- flagged in the
         * P1.4.2 report for the design doc to catch up.
         */
        CountingMethod countingMethod,
        /** Used to translate an approved quantity into a money amount (DivisibleLimitSplitter). */
        BigDecimal effectiveUnitPrice,
        /** The eligible amount for the whole line, before any limit is applied. */
        BigDecimal eligibleAmount,

        /** null outside a claim re-calculation (e.g. PREAUTH_RESERVATION). */
        Long excludeClaimId,

        ReservationEvaluationMode reservationMode,
        /** Required only when reservationMode == PREAUTHORIZED_CLAIM. */
        Long preAuthorizationId,
        /** Required only when reservationMode == PREAUTHORIZED_CLAIM. */
        Long memberPolicyAssignmentId) {

    public UnifiedLimitInput {
        if (policyId == null) throw new IllegalArgumentException("policyId is required");
        if (ruleId == null) throw new IllegalArgumentException("ruleId is required");
        if (memberId == null) throw new IllegalArgumentException("memberId is required");
        if (serviceDate == null) throw new IllegalArgumentException("serviceDate is required");
        if (reservationMode == null) throw new IllegalArgumentException("reservationMode is required");
        if (reservationMode == ReservationEvaluationMode.PREAUTHORIZED_CLAIM
                && (preAuthorizationId == null || memberPolicyAssignmentId == null)) {
            throw new IllegalArgumentException(
                    "PREAUTHORIZED_CLAIM requires preAuthorizationId and memberPolicyAssignmentId");
        }
    }
}
