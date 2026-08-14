package com.waad.tba.modules.preauthorization.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What an approval WOULD record, computed without recording any of it.
 *
 * This is the whole output of {@link PreAuthorizationDecisionBuilder}: a
 * value, not an effect. Nothing here has been written, no status has changed,
 * and no limit has been held. The approval service takes one of these and
 * persists it under locks; until then it is a proposal that can be discarded
 * or recomputed freely.
 *
 * Keeping it a value is what lets the same computation serve two callers with
 * different guarantees: a preview endpoint that must not touch anything, and
 * the approval service that must re-run it under locks before committing. The
 * second is why nothing here may be treated as final -- balances read before
 * a lock are advisory, and the approval service says so again after locking.
 */
public record PreAuthorizationDecision(
        Outcome outcome,
        Long preauthId,
        Long memberId,
        int calculationVersion,
        Basis basis,
        List<Line> lines,
        CoverageOutcome coverageOutcome,
        BigDecimal requestedTotal,
        /** What was asked, less what was explicitly refused. A ceiling does not reduce it. */
        BigDecimal authorizedServiceTotal,
        /** What the two parties pay between them: patient + company. */
        BigDecimal settlementTotal,
        BigDecimal providerDiscountTotal,
        /** Service value outside the ceiling, and therefore the patient's (S6-S7). */
        BigDecimal limitExcessTotal,
        boolean limitCapped,
        BigDecimal rejectedTotal,
        BigDecimal patientShareTotal,
        BigDecimal companyShareTotal,
        /** Present exactly when outcome is REJECTED; null otherwise. */
        String rejectionReason) {

    /**
     * REJECTED is a real outcome, not an error: a request for a service whose
     * limit is exhausted was correctly evaluated and correctly refused. The
     * builder throws only when it cannot evaluate at all.
     */
    public enum Outcome { APPROVED, PARTIALLY_APPROVED, REJECTED }

    /**
     * Whether the INSURER covered its calculated share in full -- a different
     * question from whether the SERVICE was approved, and deliberately not
     * folded into Outcome.
     *
     * A service can be fully authorised while a ceiling caps what the insurer
     * pays. Calling that "partially approved" tells a patient their request
     * was cut down when it was not: the whole service was authorised, and a
     * limit they had already spent decides who pays for it.
     */
    public enum CoverageOutcome {
        FULLY_COVERED,
        /** Part of the service value exceeded the ceiling; the insurer still pays something. */
        LIMIT_CAPPED,
        /**
         * Nothing was left to pay from. NOT "zero coverage": the policy still
         * covers its percentage -- what reached zero is the payable amount,
         * because the ceiling is spent. Showing a member 0% would misstate
         * their entitlement.
         */
        LIMIT_EXHAUSTED,
        PARTIALLY_COVERED
    }

    /** The unit a scope reserves in. Values in different units are never summed. */
    public enum ReservedUnit { CURRENCY, TIMES, DAYS }

    /**
     * Where value beyond a ceiling lands. Today the constitution fixes one
     * answer (S6-S7), but naming it keeps the snapshot readable and leaves
     * room for a policy that refuses the service or charges the provider
     * instead -- without that meaning being implicit in the arithmetic.
     */
    public enum LimitExcessDisposition { PATIENT_RESPONSIBILITY }

    /**
     * Everything the decision was made ON. Recorded so a conversion months
     * later settles on this basis rather than on whatever the configuration
     * says by then.
     */
    public record Basis(
            Long memberPolicyAssignmentId,
            Long policyId,
            /**
             * The policy's optimistic-lock version at decision time. This
             * codebase has no separate "structure revision" concept yet (the
             * claim snapshot path records null for it), and the version is
             * what actually changes when a policy is edited -- so it is what
             * makes drift between approval and conversion detectable.
             */
            Long policyVersion,
            LocalDate expectedServiceDate,
            Long providerId,
            Long providerContractId,
            Long contractTermsId,
            BigDecimal discountPercent,
            /**
             * Whether the discount applied before the rejection was removed.
             * The same percentage yields different money either way, so the
             * order is part of the basis, not a detail of the contract.
             */
            Boolean discountBeforeRejection) {}

    public record Line(
            Long preauthLineId,
            Long providerServiceId,
            String serviceCode,
            String serviceName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal requestedAmount,
            Integer coveragePercent,
            BigDecimal copayAmount,
            BigDecimal rejectedAmount,
            Long medicalServiceId,
            Long medicalCategoryId,
            Long benefitRuleId,
            int requestedQuantity,
            /** What the REVIEWER authorised. Never reduced by a ceiling. */
            int approvedQuantity,
            /** Of the approved quantity, how many occurrences the insurance covers. */
            int coveredTimes,
            /** Approved occurrences the ceiling could not cover; the patient's under S6-S7. */
            int limitExcessTimes,
            LimitExcessDisposition limitExcessDisposition,
            String reviewDecision,
            String rejectionReason,
            /** The insurer's share BEFORE the ceiling was applied -- what the policy would have paid. */
            BigDecimal companyShareBeforeLimit,
            BigDecimal authorizedServiceAmount,
            BigDecimal settlementAmount,
            BigDecimal providerDiscount,
            BigDecimal limitExcess,
            BigDecimal patientShare,
            BigDecimal companyShare,
            /**
             * What this line will hold, per applicable limit. NEVER summed
             * across scopes: a line mapped to a service bucket, its group, its
             * parent and the general ceiling holds ONE amount that each of
             * those scopes measures, not four separate amounts.
             */
            List<LimitHold> limitHolds) {}

    public record LimitHold(
            String limitSemanticKey,
            /** BUCKET or POLICY_GENERAL, matching the ledger's own scope split. */
            String limitScope,
            Long bucketId,
            Long policyId,
            String periodType,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal effectiveLimit,
            BigDecimal committedBefore,
            BigDecimal reservedBefore,
            BigDecimal actualRemainingBefore,
            BigDecimal reservableAvailableBefore,
            /**
             * The occurrence dimension's own balances. Null throughout when
             * the bucket caps no count -- a hold cannot be justified without
             * the figures it was decided against, so these travel together.
             */
            Integer timesLimit,
            Integer committedTimesBefore,
            Integer reservedTimesBefore,
            Integer actualRemainingTimesBefore,
            Integer reservableTimesBefore,
            /**
             * What this scope MEASURES. One decision, several independent
             * measures: the general ceiling counts the insurer's money, a
             * service bucket may count the eligible amount, a visit bucket
             * counts occurrences. Never comparable, never summed.
             */
            String consumptionBasis,
            ReservedUnit reservedUnit,
            /** Set exactly when reservedUnit is CURRENCY. */
            BigDecimal amountReserved,
            /** Set exactly when reservedUnit is TIMES. */
            Integer timesReserved,
            /** Set exactly when reservedUnit is DAYS. */
            Integer daysReserved,
            boolean binding) {}
}
