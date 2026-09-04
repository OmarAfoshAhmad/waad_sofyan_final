package com.waad.tba.modules.claim.service.finance.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-008 P0: fixes the reference formulas and their expected numbers BEFORE
 * any engine code is unified or the CLAIM_WIDE_LIMIT behavior type is built.
 *
 * These are pure-arithmetic golden fixtures, not integration tests against a
 * live engine -- CLAIM_WIDE_LIMIT does not exist in the codebase yet
 * (confirmed: zero hits for "claimWide|claim_wide|wholeClaim" in src/main).
 * Their job is narrower and non-negotiable: prove the formulas in ADR-008 /
 * V2 §35 are internally consistent and reconcile to the cent, so that when
 * P3/P4 build the real engine, "does it match this fixture" is a yes/no
 * question instead of a fresh derivation.
 *
 * Do not edit the expected numbers without a new, explicitly approved
 * business decision -- per the closure protocol, changing a golden number is
 * itself a decision that needs sign-off, not a test fix.
 */
class CoverageDecisionGoldenFormulaTest {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static BigDecimal money(String v) {
        return new BigDecimal(v).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * ADR-008 §3 / V2 §35 "before the coverage decision":
     * Insurance Basis = Contract Allowed - Pre-Adjudication Beneficiary Payment
     */
    private static BigDecimal insuranceBasis(BigDecimal contractAllowed, BigDecimal preAdjudicationPayment) {
        return contractAllowed.subtract(preAdjudicationPayment).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * V2 §35 "after applying limits":
     * Allowed Eligible = MIN(Insurance Basis, every applicable remaining limit)
     * Only finite remainders participate; a null remainder means "no limit
     * at that level" and must never be substituted with zero.
     */
    private static BigDecimal allowedEligible(BigDecimal insuranceBasis, BigDecimal... remainders) {
        BigDecimal result = insuranceBasis;
        for (BigDecimal remainder : remainders) {
            if (remainder != null) {
                result = result.min(remainder);
            }
        }
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    private record Split(BigDecimal company, BigDecimal copay, BigDecimal nonCovered) {}

    /**
     * V2 §35 financial result:
     * company = allowedEligible * coverage%
     * copay = allowedEligible - company
     * nonCovered = insuranceBasis - allowedEligible
     * Invariant: insuranceBasis == company + copay + nonCovered, always.
     */
    private static Split split(BigDecimal insuranceBasis, BigDecimal allowedEligible, int coveragePercent) {
        BigDecimal company = allowedEligible.multiply(BigDecimal.valueOf(coveragePercent))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal copay = allowedEligible.subtract(company).setScale(2, RoundingMode.HALF_UP);
        BigDecimal nonCovered = insuranceBasis.subtract(allowedEligible).setScale(2, RoundingMode.HALF_UP);
        return new Split(company, copay, nonCovered);
    }

    @Test
    @DisplayName("CATEGORY_RULED: OUTPATIENT/CAT-PHYSIO — no claim-wide limit, single category remainder binds")
    void categoryRuledBindsOnTheCategoryRemainderOnly() {
        BigDecimal basis = insuranceBasis(money("500.00"), BigDecimal.ZERO);
        BigDecimal allowed = allowedEligible(basis, money("500.00") /* category remaining, well above request */);
        Split s = split(basis, allowed, 75);

        assertThat(s.company()).isEqualByComparingTo("375.00");
        assertThat(s.copay()).isEqualByComparingTo("125.00");
        assertThat(s.nonCovered()).isEqualByComparingTo("0.00");
        assertThat(s.company().add(s.copay()).add(s.nonCovered())).isEqualByComparingTo(basis);
    }

    @Test
    @DisplayName("CLAIM_WIDE_LIMIT: MATERNITY — canonical V2 §11 example, category sublimits ignored")
    void claimWideLimitMaternityCanonicalExample() {
        BigDecimal basis = insuranceBasis(money("4500.00"), BigDecimal.ZERO);
        // The whole claim shares ONE remainder (the maternity context limit).
        // No per-line category limit ever enters this MIN().
        BigDecimal allowed = allowedEligible(basis, money("4000.00"));
        Split s = split(basis, allowed, 75);

        assertThat(allowed).isEqualByComparingTo("4000.00");
        assertThat(s.company()).isEqualByComparingTo("3000.00");
        assertThat(s.copay()).isEqualByComparingTo("1000.00");
        assertThat(s.nonCovered()).isEqualByComparingTo("500.00");
        assertThat(s.company().add(s.copay()).add(s.nonCovered())).isEqualByComparingTo(basis);
    }

    @Test
    @DisplayName("ADR-008 §3: Non-Covered is not silently owned by the member")
    void nonCoveredHasNoAutomaticOwner() {
        // This is the formula-level pin of the ADR-008 §3 decision itself:
        // splitting a claim never assigns nonCovered to any party field.
        // Whoever settles it (if anyone) is a separate, later decision --
        // this fixture only proves the coverage split does not do it.
        BigDecimal basis = insuranceBasis(money("1000.00"), BigDecimal.ZERO);
        BigDecimal allowed = allowedEligible(basis, money("600.00"));
        Split s = split(basis, allowed, 75);

        assertThat(s.nonCovered()).isEqualByComparingTo("400.00");
        // The Split record has exactly three fields: company, copay,
        // nonCovered. There is no fourth "memberLiability" field to check --
        // that is the point. Reconciliation still holds:
        assertThat(s.company().add(s.copay()).add(s.nonCovered())).isEqualByComparingTo(basis);
    }

    @Test
    @DisplayName("Pre-adjudication beneficiary payment reduces the insurance basis before any limit is applied")
    void preAdjudicationPaymentReducesBasisBeforeLimits() {
        BigDecimal contractAllowed = money("1000.00");
        BigDecimal outsideInsurancePayment = money("200.00");
        BigDecimal basis = insuranceBasis(contractAllowed, outsideInsurancePayment);

        assertThat(basis).isEqualByComparingTo("800.00");

        BigDecimal allowed = allowedEligible(basis, money("800.00"));
        Split s = split(basis, allowed, 100);

        assertThat(s.company()).isEqualByComparingTo("800.00");
        assertThat(s.copay()).isEqualByComparingTo("0.00");
        assertThat(s.nonCovered()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("times limit: partial acceptance is min(requested, remaining), never a full refusal when divisible")
    void timesLimitPartialAcceptanceFormula() {
        int remainingVisits = 2;
        int requestedVisits = 3;
        BigDecimal sessionPrice = money("100.00");

        int allowedVisits = Math.min(requestedVisits, remainingVisits);
        BigDecimal allowedEligible = sessionPrice.multiply(BigDecimal.valueOf(allowedVisits));
        BigDecimal insuranceBasisForRequest = sessionPrice.multiply(BigDecimal.valueOf(requestedVisits));
        BigDecimal nonCovered = insuranceBasisForRequest.subtract(allowedEligible);

        assertThat(allowedVisits).isEqualTo(2);
        assertThat(allowedEligible).isEqualByComparingTo("200.00");
        assertThat(nonCovered).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("reconciliation invariant holds across a sweep of limit positions: below, at, partial, exhausted")
    void reconciliationHoldsAcrossAllFourLimitPositions() {
        record Case(String name, String requested, String remaining) {}
        Case[] cases = {
                new Case("below limit", "300.00", "1000.00"),
                new Case("exactly at limit", "1000.00", "1000.00"),
                new Case("partially exceeds", "1500.00", "1000.00"),
                new Case("fully exhausted", "500.00", "0.00")
        };

        for (Case c : cases) {
            BigDecimal basis = insuranceBasis(money(c.requested()), BigDecimal.ZERO);
            BigDecimal allowed = allowedEligible(basis, money(c.remaining()));
            Split s = split(basis, allowed, 80);

            assertThat(s.company().add(s.copay()).add(s.nonCovered()))
                    .as(c.name())
                    .isEqualByComparingTo(basis);
            assertThat(allowed).as(c.name()).isLessThanOrEqualTo(money(c.remaining()).max(BigDecimal.ZERO));
            assertThat(s.nonCovered()).as(c.name() + " never negative").isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }
    }
}
