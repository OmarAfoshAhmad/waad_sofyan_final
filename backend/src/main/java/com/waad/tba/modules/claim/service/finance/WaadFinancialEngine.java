package com.waad.tba.modules.claim.service.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

/**
 * Canonical implementation of WAAD-FIN-1.0
 * (backend/docs/design/FINANCIAL_CONSTITUTION.md), in the exact order fixed
 * by its S12. This is a standalone, isolated deliverable per the
 * constitution's critical path: proven correct by the golden test and a unit
 * matrix before wiring, and now connected to ClaimMapper exclusively through
 * {@link ClaimFinancialAdjudicationService}.
 *
 * finance-02.1 hardening (contract review before the Result shape is frozen
 * into database columns): strict input validation (fail closed, never
 * silently default a missing amount to zero), an explicit
 * {@link LimitMode#UNLIMITED} instead of a "pass a large number" convention,
 * removal of approvedQuantity (a quantity's fate cannot be inferred from
 * insurerFinalPayment alone -- see the class javadoc on why), renaming
 * limitCoveredBase to insideLimit and availableLimit/remainingLimit to
 * bindingAvailableLimit/bindingRemainingLimit (this engine only ever sees
 * the single most-restrictive bucket's balance; per-bucket balances belong
 * in a separate snapshot the Ledger Service maintains, not in this Result),
 * and an explicit invariant check at the end of evaluate() rather than
 * relying on "the arithmetic happens to balance today".
 *
 * This class touches no repository, no entity, no lock, no transaction: it
 * consumes plain numbers already resolved by an external layer (contract
 * price, coverage rule, the single binding available limit across every
 * applicable bucket) and returns a plain, immutable result. Resolving which
 * contract term applies, which buckets are applicable, computing
 * bindingAvailableLimit from POLICY_DEFAULT/EMPLOYER_OVERRIDE/MEMBER_OVERRIDE,
 * locking, persisting per-bucket consumption, or moving the claim's status
 * are all responsibilities of a Resolver / Ledger Service layer outside
 * this class.
 *
 * Fixed order (a new constitution version is required to change it):
 *   1. contractualPriceExcess is split off the requested amount first (S3)
 *      -- it never enters the insurance calculation at all.
 *   2. settlementBase is capped by bindingAvailableLimit BEFORE the
 *      patient/company split (S4) -- the limit is defined against the
 *      contractual service value, never against what the insurer ends up
 *      paying.
 *   3. Coverage is applied to insideLimit only (S8); the excess over the
 *      limit is a distinct, explicitly-labeled patient responsibility
 *      (patientLimitExcess, S6-S7) that the provider discount never reduces
 *      (S9) -- the patient benefits from the contractual price, never from
 *      the insurer's additional discount.
 *   4. The provider's contractual discount is applied to insurerGrossShare
 *      only (S9), then the rejection is subtracted (S11). There is exactly
 *      one ordering; S10 abolishes the discount-before/after-rejection
 *      choice that used to exist in {@link ClaimLineFinancialEngine}.
 *
 * Invariant enforced by construction AND checked explicitly before returning
 * (S13):
 *   requestedAmount == contractualPriceExcess + patientCoverageShare
 *       + patientLimitExcess + providerContractDiscount
 *       + providerRejectedAmount + insurerFinalPayment
 */
@Component
public class WaadFinancialEngine {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal INVARIANT_EPSILON = new BigDecimal("0.01");

    /** No "pass a large number" convention: absence of a limit is a first-class state. */
    public enum LimitMode {
        UNLIMITED,
        LIMITED
    }

    /**
     * @param requestedAmount         what the provider billed for the line; must be > 0
     * @param contractualPrice        the contract price in effect on serviceDate; must be
     *                                > 0 and non-null -- a missing contract price is a
     *                                distinct failure (CONTRACT_PRICE_NOT_FOUND), never
     *                                treated as zero (which would make the entire
     *                                requested amount look like a price excess charged
     *                                to the provider)
     * @param limitMode               UNLIMITED or LIMITED, explicit -- never inferred
     *                                from a sentinel value
     * @param bindingAvailableLimit   required and must be >= 0 when limitMode is LIMITED;
     *                                ignored when UNLIMITED. This is the single most
     *                                restrictive balance across every applicable bucket
     *                                (S15's min(available across buckets)), already
     *                                resolved by an external Resolver -- per-bucket
     *                                balances are a Ledger Service concern, not this
     *                                engine's
     * @param coveragePercent         1-100 inclusive. There is no such thing as
     *                                0% coverage (S2): a line with no benefit
     *                                eligibility must never reach this engine.
     *                                Fails closed outside this range.
     * @param providerDiscountPercent the provider contract's discount rate; must be
     *                                0-100 inclusive and non-null
     * @param providerRejectedAmount  a reviewer's rejection against
     *                                providerNetBeforeRejection; must be >= 0 and
     *                                non-null. Must also be
     *                                <= providerNetBeforeRejection (S11); out-of-range
     *                                values fail closed rather than being silently
     *                                clamped
     * @param fullyRejected           true when the reviewer rejected the entire line;
     *                                providerNetBeforeRejection itself becomes the
     *                                rejection amount, and providerRejectedAmount is
     *                                ignored. Kept as an explicit flag rather than
     *                                inferred from providerRejectedAmount ==
     *                                providerNetBeforeRejection, so a reviewer typing
     *                                the full amount by coincidence is never mistaken
     *                                for an intentional full rejection
     * @param quantity                the line's quantity; must be > 0. Not used to
     *                                derive any output -- a quantity's fate under a
     *                                times/days limit is a benefit-limit-engine
     *                                concern, not this engine's. Validated here only so
     *                                a caller cannot construct a nonsensical zero/negative
     *                                quantity line in the first place
     */
    public record Input(
            BigDecimal requestedAmount,
            BigDecimal contractualPrice,
            LimitMode limitMode,
            BigDecimal bindingAvailableLimit,
            int coveragePercent,
            BigDecimal providerDiscountPercent,
            BigDecimal providerRejectedAmount,
            boolean fullyRejected,
            int quantity) {
    }

    /**
     * Field names mirror the constitution's S12/S13 vocabulary exactly, so a
     * reviewer can check this class against the document line by line.
     * insideLimit/bindingAvailableLimit/bindingRemainingLimit are null when
     * limitMode is UNLIMITED -- "not applicable", not zero (zero means an
     * exhausted limit, the opposite of unlimited).
     */
    public record Result(
            BigDecimal requestedAmount,
            BigDecimal contractualPrice,
            BigDecimal contractualPriceExcess,
            BigDecimal settlementBase,
            LimitMode limitMode,
            BigDecimal bindingAvailableLimit,
            BigDecimal insideLimit,
            BigDecimal patientLimitExcess,
            BigDecimal limitConsumption,
            BigDecimal bindingRemainingLimit,
            int coveragePercent,
            BigDecimal patientCoverageShare,
            BigDecimal patientTotalResponsibility,
            BigDecimal insurerGrossShare,
            BigDecimal providerDiscountPercent,
            BigDecimal providerContractDiscount,
            BigDecimal providerNetBeforeRejection,
            BigDecimal providerRejectedAmount,
            BigDecimal insurerFinalPayment) {
    }

    public Result evaluate(Input input) {
        if (input == null) {
            throw new IllegalArgumentException("FINANCIAL_CONSTITUTION: Input must not be null");
        }
        validate(input);

        BigDecimal requestedAmount = scale2(input.requestedAmount());
        BigDecimal contractualPrice = scale2(input.contractualPrice());
        BigDecimal discountPercent = input.providerDiscountPercent();
        BigDecimal providerRejectedAmount = scale2(input.providerRejectedAmount());

        // S3: the settlement base is capped at the contractual price; any
        // excess is the provider's alone and never enters the insurance math.
        BigDecimal contractualPriceExcess = maxZero(scale2(requestedAmount.subtract(contractualPrice)));
        BigDecimal settlementBase = scale2(requestedAmount.min(contractualPrice));

        // S4: the limit caps settlementBase BEFORE the patient/company split --
        // never companyShare/insurerFinalPayment after it. UNLIMITED is a
        // first-class state, not a sentinel large number.
        boolean limited = input.limitMode() == LimitMode.LIMITED;
        BigDecimal bindingAvailableLimit = limited ? scale2(input.bindingAvailableLimit()) : null;
        BigDecimal insideLimit;
        BigDecimal patientLimitExcess;
        BigDecimal limitConsumption;
        BigDecimal bindingRemainingLimit;
        if (limited) {
            insideLimit = scale2(settlementBase.min(bindingAvailableLimit));
            patientLimitExcess = maxZero(scale2(settlementBase.subtract(bindingAvailableLimit)));
            limitConsumption = insideLimit;
            bindingRemainingLimit = maxZero(scale2(bindingAvailableLimit.subtract(limitConsumption)));
        } else {
            insideLimit = settlementBase;
            patientLimitExcess = ZERO;
            limitConsumption = null;
            bindingRemainingLimit = null;
        }

        // S8: coverage percent applies to insideLimit only.
        BigDecimal patientRate = BigDecimal.valueOf(100 - input.coveragePercent());
        BigDecimal patientCoverageShare = scale2(
                insideLimit.multiply(patientRate).divide(HUNDRED, 2, RoundingMode.HALF_UP));
        BigDecimal insurerGrossShare = maxZero(scale2(insideLimit.subtract(patientCoverageShare)));

        BigDecimal patientTotalResponsibility = scale2(patientCoverageShare.add(patientLimitExcess));

        // Canonical settlement order: explicit provider rejection is removed from
        // the insurer share before the contractual discount is calculated. This
        // prevents the insurer from taking a discount on an amount it has already
        // refused and keeps settlement, claim and provider-account figures identical.
        BigDecimal rejectionCandidate = input.fullyRejected() ? insurerGrossShare : providerRejectedAmount;
        if (rejectionCandidate.compareTo(insurerGrossShare) > 0) {
            throw new IllegalArgumentException(
                    "FINANCIAL_CONSTITUTION S11/S24: providerRejectedAmount (" + rejectionCandidate
                            + ") exceeds insurerGrossShare (" + insurerGrossShare
                            + "). Fail closed -- do not clamp silently.");
        }
        BigDecimal appliedRejection = scale2(rejectionCandidate);
        // Kept under the historical column/result name for schema compatibility;
        // its canonical meaning is now the discount base after rejection.
        BigDecimal providerNetBeforeRejection = maxZero(scale2(insurerGrossShare.subtract(appliedRejection)));
        BigDecimal providerContractDiscount = scale2(
                providerNetBeforeRejection.multiply(discountPercent)
                        .divide(HUNDRED, 2, RoundingMode.HALF_UP));
        BigDecimal insurerFinalPayment = maxZero(
                scale2(providerNetBeforeRejection.subtract(providerContractDiscount)));

        // S13: the invariant is checked explicitly, not just assumed correct
        // by construction -- a future rounding change or new component must
        // not be able to silently break it.
        BigDecimal reconstructed = contractualPriceExcess
                .add(patientCoverageShare)
                .add(patientLimitExcess)
                .add(providerContractDiscount)
                .add(appliedRejection)
                .add(insurerFinalPayment);
        if (reconstructed.subtract(requestedAmount).abs().compareTo(INVARIANT_EPSILON) > 0) {
            throw new IllegalStateException(
                    "FINANCIAL_INVARIANT_VIOLATION: requestedAmount=" + requestedAmount
                            + " but components reconstruct to " + reconstructed
                            + " (diff=" + reconstructed.subtract(requestedAmount).abs() + ")");
        }

        return new Result(
                requestedAmount,
                contractualPrice,
                contractualPriceExcess,
                settlementBase,
                input.limitMode(),
                bindingAvailableLimit,
                insideLimit,
                patientLimitExcess,
                limitConsumption,
                bindingRemainingLimit,
                input.coveragePercent(),
                patientCoverageShare,
                patientTotalResponsibility,
                insurerGrossShare,
                scale2(discountPercent),
                providerContractDiscount,
                providerNetBeforeRejection,
                appliedRejection,
                insurerFinalPayment);
    }

    private void validate(Input input) {
        requireNonNull(input.requestedAmount(), "requestedAmount");
        requirePositive(input.requestedAmount(), "requestedAmount");

        // CONTRACT_PRICE_NOT_FOUND: a missing contract price is a distinct
        // failure, never defaulted to zero (which would make the whole
        // requested amount look like a price excess on the provider).
        if (input.contractualPrice() == null) {
            throw new IllegalArgumentException("CONTRACT_PRICE_NOT_FOUND: contractualPrice is required");
        }
        requirePositive(input.contractualPrice(), "contractualPrice");

        if (input.limitMode() == null) {
            throw new IllegalArgumentException("FINANCIAL_CONSTITUTION S4: limitMode must not be null");
        }
        if (input.limitMode() == LimitMode.LIMITED) {
            requireNonNull(input.bindingAvailableLimit(), "bindingAvailableLimit (required when limitMode=LIMITED)");
            if (input.bindingAvailableLimit().signum() < 0) {
                throw new IllegalArgumentException(
                        "FINANCIAL_CONSTITUTION S5: bindingAvailableLimit must be >= 0, got "
                                + input.bindingAvailableLimit());
            }
        }

        if (input.coveragePercent() < 1 || input.coveragePercent() > 100) {
            throw new IllegalArgumentException(
                    "FINANCIAL_CONSTITUTION S2/S24: coveragePercent must satisfy "
                            + "0 < coveragePercent <= 100 (a line with no coverage must never "
                            + "reach this engine); got " + input.coveragePercent());
        }

        requireNonNull(input.providerDiscountPercent(), "providerDiscountPercent");
        if (input.providerDiscountPercent().signum() < 0
                || input.providerDiscountPercent().compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException(
                    "FINANCIAL_CONSTITUTION S24: providerDiscountPercent must satisfy "
                            + "0 <= providerDiscountPercent <= 100, got " + input.providerDiscountPercent());
        }

        requireNonNull(input.providerRejectedAmount(), "providerRejectedAmount");
        if (input.providerRejectedAmount().signum() < 0) {
            throw new IllegalArgumentException(
                    "FINANCIAL_CONSTITUTION S24: providerRejectedAmount must be >= 0, got "
                            + input.providerRejectedAmount());
        }

        if (input.quantity() <= 0) {
            throw new IllegalArgumentException(
                    "FINANCIAL_CONSTITUTION: quantity must be > 0, got " + input.quantity());
        }
    }

    private static void requireNonNull(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "FINANCIAL_CONSTITUTION: " + fieldName + " must not be null -- "
                            + "missing financial amounts are never defaulted to zero");
        }
    }

    private static void requirePositive(BigDecimal value, String fieldName) {
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(
                    "FINANCIAL_CONSTITUTION: " + fieldName + " must be > 0, got " + value);
        }
    }

    private static BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal maxZero(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0 ? ZERO : value;
    }
}
