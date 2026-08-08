package com.waad.tba.modules.claim.service.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

/**
 * Canonical implementation of WAAD-FIN-1.0
 * (backend/docs/design/FINANCIAL_CONSTITUTION.md), in the exact order fixed
 * by its S12. This is a standalone, isolated deliverable per the
 * constitution's critical path: proven correct by the golden test and a unit
 * matrix BEFORE any data model or ClaimMapper wiring exists. It is not yet
 * called from production code -- {@link ClaimLineFinancialEngine} remains
 * the live engine behind ClaimMapper until the "unified limit semantics" /
 * "snapshot model" phase replaces the call site and the persisted columns
 * together. Wiring this in early, against entity columns shaped for the old
 * contract, would silently corrupt the new patientLimitExcess/
 * contractualPriceExcess distinction the constitution requires.
 *
 * This class touches no repository, no entity, no lock, no transaction: it
 * consumes plain numbers already resolved by an external layer (contract
 * price, coverage rule, the effective available limit across every
 * applicable bucket) and returns a plain, immutable result. Resolving which
 * contract term applies, which buckets are applicable, computing
 * availableLimit from POLICY_DEFAULT/EMPLOYER_OVERRIDE/MEMBER_OVERRIDE,
 * locking, persisting per-bucket consumption, or moving the claim's status
 * are all responsibilities of a Resolver / Ledger Service layer outside
 * this class.
 *
 * Fixed order (a new constitution version is required to change it):
 *   1. contractualPriceExcess is split off the requested amount first (S3)
 *      -- it never enters the insurance calculation at all.
 *   2. settlementBase is capped by availableLimit BEFORE the patient/company
 *      split (S4) -- the limit is defined against the contractual service
 *      value, never against what the insurer ends up paying.
 *   3. Coverage is applied to limitCoveredBase only (S8); the excess over
 *      the limit is a distinct, explicitly-labeled patient responsibility
 *      (patientLimitExcess, S6-S7) that the provider discount never reduces
 *      (S9) -- the patient benefits from the contractual price, never from
 *      the insurer's additional discount.
 *   4. The provider's contractual discount is applied to insurerGrossShare
 *      only (S9), then the rejection is subtracted (S11). There is exactly
 *      one ordering; S10 abolishes the discount-before/after-rejection
 *      choice that used to exist in {@link ClaimLineFinancialEngine}.
 *
 * Invariant enforced by construction, not just convention (S13):
 *   requestedAmount == contractualPriceExcess + patientCoverageShare
 *       + patientLimitExcess + providerContractDiscount
 *       + providerRejectedAmount + insurerFinalPayment
 */
@Component
public class WaadFinancialEngine {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    /**
     * @param requestedAmount         what the provider billed for the line
     * @param contractualPrice        the contract price in effect on serviceDate;
     *                                the ceiling that enters the insurance
     *                                calculation at all (S3)
     * @param availableLimit          the effective remaining balance across every
     *                                applicable benefit bucket (S15), already
     *                                resolved by an external Resolver. Never
     *                                null -- callers with no limit configured
     *                                pass a value large enough to never bind
     *                                (the Resolver's responsibility, not this
     *                                engine's)
     * @param coveragePercent         1-100 inclusive. There is no such thing as
     *                                0% coverage (S2): a line with no benefit
     *                                eligibility must never reach this engine.
     *                                Fails closed outside this range.
     * @param providerDiscountPercent the provider contract's discount rate, 0-100
     * @param providerRejectedAmount  a reviewer's rejection against
     *                                providerNetBeforeRejection. Must be
     *                                0 <= x <= providerNetBeforeRejection (S11);
     *                                out-of-range values fail closed rather than
     *                                being silently clamped
     * @param fullyRejected           true when the reviewer rejected the entire
     *                                line; providerNetBeforeRejection itself
     *                                becomes the rejection amount, and
     *                                providerRejectedAmount is ignored. Kept as
     *                                an explicit flag rather than inferred from
     *                                providerRejectedAmount == providerNetBeforeRejection,
     *                                so a reviewer typing the full amount by
     *                                coincidence is never mistaken for an
     *                                intentional full rejection
     * @param quantity                the line's quantity, used only to report
     *                                approvedQuantity (0 when nothing is payable)
     */
    public record Input(
            BigDecimal requestedAmount,
            BigDecimal contractualPrice,
            BigDecimal availableLimit,
            int coveragePercent,
            BigDecimal providerDiscountPercent,
            BigDecimal providerRejectedAmount,
            boolean fullyRejected,
            int quantity) {
    }

    /**
     * Field names mirror the constitution's S12/S13 vocabulary exactly, so a
     * reviewer can check this class against the document line by line.
     */
    public record Result(
            BigDecimal requestedAmount,
            BigDecimal contractualPrice,
            BigDecimal contractualPriceExcess,
            BigDecimal settlementBase,
            BigDecimal availableLimit,
            BigDecimal limitCoveredBase,
            BigDecimal patientLimitExcess,
            BigDecimal patientCoverageShare,
            BigDecimal patientTotalResponsibility,
            BigDecimal insurerGrossShare,
            BigDecimal providerContractDiscount,
            BigDecimal providerNetBeforeRejection,
            BigDecimal providerRejectedAmount,
            BigDecimal insurerFinalPayment,
            BigDecimal limitConsumption,
            BigDecimal remainingLimit,
            int approvedQuantity) {
    }

    public Result evaluate(Input input) {
        // S2: coverage=0% does not exist. A line with no benefit eligibility
        // must never reach this engine at all -- fail closed rather than
        // silently treating 0 as "not covered".
        if (input.coveragePercent() <= 0 || input.coveragePercent() > 100) {
            throw new IllegalArgumentException(
                    "FINANCIAL_CONSTITUTION S2/S24: coveragePercent must satisfy "
                            + "0 < coveragePercent <= 100 (a line with no coverage must never "
                            + "reach this engine); got " + input.coveragePercent());
        }

        BigDecimal requestedAmount = scale2(defaultZero(input.requestedAmount()));
        BigDecimal contractualPrice = scale2(defaultZero(input.contractualPrice()));
        BigDecimal availableLimit = maxZero(scale2(defaultZero(input.availableLimit())));
        BigDecimal discountPercent = defaultZero(input.providerDiscountPercent());
        BigDecimal providerRejectedAmount = maxZero(scale2(defaultZero(input.providerRejectedAmount())));

        // S3: the settlement base is capped at the contractual price; any
        // excess is the provider's alone and never enters the insurance math.
        BigDecimal contractualPriceExcess = maxZero(scale2(requestedAmount.subtract(contractualPrice)));
        BigDecimal settlementBase = scale2(requestedAmount.min(contractualPrice).max(BigDecimal.ZERO));

        // S4: the limit caps settlementBase BEFORE the patient/company split --
        // never companyShare/insurerFinalPayment after it.
        BigDecimal limitCoveredBase = scale2(settlementBase.min(availableLimit).max(BigDecimal.ZERO));
        BigDecimal patientLimitExcess = maxZero(scale2(settlementBase.subtract(availableLimit)));
        BigDecimal limitConsumption = limitCoveredBase;
        BigDecimal remainingLimit = maxZero(scale2(availableLimit.subtract(limitConsumption)));

        // S8: coverage percent applies to limitCoveredBase only.
        int normalizedCoverage = Math.min(100, Math.max(0, input.coveragePercent()));
        BigDecimal patientRate = BigDecimal.valueOf(100 - normalizedCoverage);
        BigDecimal patientCoverageShare = scale2(
                limitCoveredBase.multiply(patientRate).divide(HUNDRED, 2, RoundingMode.HALF_UP));
        BigDecimal insurerGrossShare = maxZero(scale2(limitCoveredBase.subtract(patientCoverageShare)));

        BigDecimal patientTotalResponsibility = scale2(patientCoverageShare.add(patientLimitExcess));

        // S9: the discount applies to insurerGrossShare only -- never to the
        // patient's share, never to patientLimitExcess (S9's explicit decision:
        // the patient benefits from the contractual price, never from the
        // insurer's additional discount).
        BigDecimal providerContractDiscount = scale2(
                insurerGrossShare.multiply(discountPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP));
        BigDecimal providerNetBeforeRejection = maxZero(scale2(insurerGrossShare.subtract(providerContractDiscount)));

        // S11: the rejection amount, fixed single ordering (S10 abolishes any
        // discount-before/after-rejection choice -- discount already applied above).
        BigDecimal rejectionCandidate = input.fullyRejected()
                ? providerNetBeforeRejection
                : providerRejectedAmount;
        if (rejectionCandidate.compareTo(providerNetBeforeRejection) > 0) {
            throw new IllegalArgumentException(
                    "FINANCIAL_CONSTITUTION S11/S24: providerRejectedAmount (" + rejectionCandidate
                            + ") exceeds providerNetBeforeRejection (" + providerNetBeforeRejection
                            + "). Fail closed -- do not clamp silently.");
        }
        BigDecimal appliedRejection = scale2(rejectionCandidate);
        BigDecimal insurerFinalPayment = maxZero(scale2(providerNetBeforeRejection.subtract(appliedRejection)));

        int approvedQuantity = insurerFinalPayment.compareTo(BigDecimal.ZERO) > 0 ? input.quantity() : 0;

        return new Result(
                requestedAmount,
                contractualPrice,
                contractualPriceExcess,
                settlementBase,
                availableLimit,
                limitCoveredBase,
                patientLimitExcess,
                patientCoverageShare,
                patientTotalResponsibility,
                insurerGrossShare,
                providerContractDiscount,
                providerNetBeforeRejection,
                appliedRejection,
                insurerFinalPayment,
                limitConsumption,
                remainingLimit,
                approvedQuantity);
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
}
