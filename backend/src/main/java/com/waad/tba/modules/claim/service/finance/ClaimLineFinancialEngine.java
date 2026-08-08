package com.waad.tba.modules.claim.service.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

/**
 * Pure calculation of one claim line's financial split: patient co-pay, the
 * provider contract's discount, the manual/full rejection, and the resulting
 * net company share -- in that dependency order (co-pay is split off first
 * because the member already paid it directly; the discount and rejection
 * are then ordered against each other by {@link Input#discountBeforeRejection()}).
 *
 * Extracted verbatim from ClaimMapper.processEngineCalculations (finance-00
 * step 2): no behavior change, only a location change. This class does not
 * touch a repository, an entity, a lock, or a transaction -- it consumes
 * plain numbers already resolved by the coverage engine / contract resolver
 * and returns a plain, immutable result. Resolving which contract term
 * applies, which price to charge, which coverage rule fires, checking
 * benefit-limit buckets, locking, persisting, or moving the claim's status
 * are all responsibilities of the caller, not of this class.
 *
 * Invariant enforced by construction (not just by convention): for every
 * result, requestedAmount == patientShare + refusedAmount +
 * companyDiscountAmount + companyShare + limitExceededAmount.
 */
@Component
public class ClaimLineFinancialEngine {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    /**
     * @param grossAmount           the line's effective total after the contract
     *                              price ceiling, before any benefit-limit cut
     *                              (CoverageResult.effectiveTotal)
     * @param coveragePercent       0-100; the patient's share is 100 minus this
     * @param manualRefusedAmount   a reviewer's partial refusal on an otherwise
     *                              approved line; ignored when fullyRejected
     * @param priceRefusedAmount    amount already excluded by the contract price
     *                              ceiling (CoverageResult.priceRefused) -- kept
     *                              only to reconstruct requestedAmount and to
     *                              fold into refusedAmount, never re-applied
     * @param limitRefusedAmount    amount already excluded by a benefit-limit
     *                              bucket ceiling (CoverageResult.limitRefused) --
     *                              same treatment as priceRefusedAmount
     * @param discountPercent       the provider contract's discount rate, 0-100
     * @param discountBeforeRejection true: discount the full provider share,
     *                              then subtract the rejection. false: subtract
     *                              the rejection first, then discount only the
     *                              remainder. The two are not equivalent.
     * @param fullyRejected         true when the reviewer rejected the entire
     *                              line; the whole provider share becomes the
     *                              rejection candidate, ignoring manualRefusedAmount
     * @param quantity              the line's quantity, used only to report
     *                              approvedQuantity (0 when nothing is payable)
     * @param maximumCompanyShare   the annual policy ceiling's remaining balance,
     *                              or null when no ceiling applies. This is the
     *                              ONLY number the caller passes in for the
     *                              ceiling -- the engine decides how it interacts
     *                              with the discount, not the caller. Cutting
     *                              companyShare down to this value AFTER calling
     *                              evaluate() would be wrong: in AFTER-rejection
     *                              mode the discount base must already reflect
     *                              the ceiling cut, exactly like it must reflect
     *                              the rejection.
     */
    public record Input(
            BigDecimal grossAmount,
            int coveragePercent,
            BigDecimal manualRefusedAmount,
            BigDecimal priceRefusedAmount,
            BigDecimal limitRefusedAmount,
            BigDecimal discountPercent,
            boolean discountBeforeRejection,
            boolean fullyRejected,
            int quantity,
            BigDecimal maximumCompanyShare) {
    }

    /**
     * @param requestedAmount       what was originally requested, before the
     *                              contract price ceiling: grossAmount + priceRefusedAmount
     * @param allowedAmount         grossAmount after the benefit-limit cut,
     *                              before the patient/company split
     * @param patientShare          the member's co-pay
     * @param companyShare          the net amount owed to the provider -- what
     *                              is later credited to the provider account
     * @param refusedAmount         priceRefusedAmount + limitRefusedAmount + the
     *                              rejection actually applied (capped at what
     *                              remained available to reject)
     * @param companyDiscountAmount the provider contract's discount actually applied
     * @param limitExceededAmount   the portion that would otherwise have gone to
     *                              companyShare but did not fit under
     *                              maximumCompanyShare. Deliberately NOT folded
     *                              into patientShare (the member did not agree
     *                              to absorb it) or refusedAmount (it is not a
     *                              medical or price refusal) -- who ultimately
     *                              bears it is a policy decision made elsewhere,
     *                              not by this engine.
     * @param approvedQuantity      quantity when companyShare is positive, else 0
     */
    public record Result(
            BigDecimal requestedAmount,
            BigDecimal allowedAmount,
            BigDecimal patientShare,
            BigDecimal companyShare,
            BigDecimal refusedAmount,
            BigDecimal companyDiscountAmount,
            BigDecimal limitExceededAmount,
            int approvedQuantity) {
    }

    public Result evaluate(Input input) {
        BigDecimal grossAmount = scale2(defaultZero(input.grossAmount()));
        BigDecimal priceRefused = maxZero(scale2(defaultZero(input.priceRefusedAmount())));
        BigDecimal limitRefused = maxZero(scale2(defaultZero(input.limitRefusedAmount())));
        BigDecimal manualRefused = maxZero(scale2(defaultZero(input.manualRefusedAmount())));
        BigDecimal discountPercent = defaultZero(input.discountPercent());

        BigDecimal requestedAmount = scale2(grossAmount.add(priceRefused));
        BigDecimal allowedAmount = maxZero(scale2(grossAmount.subtract(limitRefused)));

        int normalizedCoverage = Math.min(100, Math.max(0, input.coveragePercent()));
        BigDecimal patientRate = BigDecimal.valueOf(100 - normalizedCoverage);
        BigDecimal patientShare = scale2(
                allowedAmount.multiply(patientRate).divide(HUNDRED, 2, RoundingMode.HALF_UP));

        BigDecimal providerShare = maxZero(scale2(allowedAmount.subtract(patientShare)));

        // priceRefused/limitRefused have already reduced grossAmount/allowedAmount
        // above; applying them again here would double-reject the same amount.
        BigDecimal rejectionCandidate = input.fullyRejected() ? providerShare : manualRefused;

        BigDecimal rejectedAmount;
        BigDecimal companyShare;
        BigDecimal companyDiscountAmount;
        BigDecimal limitExceededAmount;
        BigDecimal maximumCompanyShare = input.maximumCompanyShare() == null
                ? null : maxZero(scale2(input.maximumCompanyShare()));

        if (input.discountBeforeRejection()) {
            // MODE: BEFORE. Order: discount on the full provider share -> subtract
            // the rejection -> THEN cap at the ceiling last, exactly as worked
            // through by hand: providerShare=800, discount=80 -> net=720,
            // reject=0 -> 720, ceiling=600 -> companyShare=600, exceeded=120.
            companyDiscountAmount = scale2(
                    providerShare.multiply(discountPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP));
            BigDecimal providerNet = maxZero(scale2(providerShare.subtract(companyDiscountAmount)));
            rejectedAmount = min(providerNet, rejectionCandidate);
            BigDecimal afterRejection = maxZero(scale2(providerNet.subtract(rejectedAmount)));
            if (maximumCompanyShare != null && afterRejection.compareTo(maximumCompanyShare) > 0) {
                limitExceededAmount = scale2(afterRejection.subtract(maximumCompanyShare));
                companyShare = maximumCompanyShare;
            } else {
                limitExceededAmount = ZERO;
                companyShare = afterRejection;
            }
        } else {
            // MODE: AFTER. Order: subtract the rejection -> cap at the ceiling ->
            // THEN discount only what's left, because the discount base must
            // reflect every exclusion that came before it, the ceiling included
            // -- the same rule already applied to the rejection.
            BigDecimal candidateToSubtract = min(providerShare, rejectionCandidate);
            BigDecimal afterRejection = maxZero(scale2(providerShare.subtract(candidateToSubtract)));
            rejectedAmount = candidateToSubtract;

            BigDecimal discountBase;
            if (maximumCompanyShare != null && afterRejection.compareTo(maximumCompanyShare) > 0) {
                limitExceededAmount = scale2(afterRejection.subtract(maximumCompanyShare));
                discountBase = maximumCompanyShare;
            } else {
                limitExceededAmount = ZERO;
                discountBase = afterRejection;
            }
            companyDiscountAmount = scale2(
                    discountBase.multiply(discountPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP));
            companyShare = maxZero(scale2(discountBase.subtract(companyDiscountAmount)));
        }

        BigDecimal refusedAmount = scale2(priceRefused.add(limitRefused).add(rejectedAmount));
        int approvedQuantity = companyShare.compareTo(BigDecimal.ZERO) > 0 ? input.quantity() : 0;

        return new Result(requestedAmount, allowedAmount, patientShare, companyShare,
                refusedAmount, companyDiscountAmount, limitExceededAmount, approvedQuantity);
    }

    private static BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal maxZero(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0 ? ZERO : value;
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal min(BigDecimal a, BigDecimal b) {
        return scale2(a.min(b));
    }
}
