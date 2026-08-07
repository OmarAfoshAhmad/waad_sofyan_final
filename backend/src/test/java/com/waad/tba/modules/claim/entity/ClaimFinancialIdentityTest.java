package com.waad.tba.modules.claim.entity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * Pure unit tests (no Spring context, no DB) for the financial identity that
 * Claim.onUpdate() -> validateFinancialIdentity() enforces on every save:
 *
 *   requestedAmount == patientCoPay + refusedAmount + companyDiscountAmount + netProviderAmount
 *
 * This is the constraint the settlement screen (ProviderAccountsList) and the
 * two report screens (company profit report, financial consolidation matrix)
 * all rely on implicitly by trusting companyDiscountAmount/netProviderAmount
 * as authoritative. These tests exist to make that constraint explicit and to
 * catch any future change to Claim's financial fields that would silently
 * break the identity — same package as Claim so the protected onUpdate() is
 * reachable directly, without going through a full JPA lifecycle.
 */
class ClaimFinancialIdentityTest {

    private Claim.ClaimBuilder balancedClaim() {
        return Claim.builder()
                .status(ClaimStatus.APPROVED)
                .requestedAmount(new BigDecimal("1000.00"))
                .patientCoPay(new BigDecimal("100.00"))
                .refusedAmount(new BigDecimal("200.00"))
                .companyDiscountAmount(new BigDecimal("63.00"))
                .approvedAmount(new BigDecimal("637.00"))
                .netProviderAmount(new BigDecimal("637.00"));
    }

    @Test
    void balancedSnapshotPassesValidation() {
        Claim claim = balancedClaim().build();

        assertThatCode(claim::onUpdate).doesNotThrowAnyException();
    }

    @Test
    void mismatchedSnapshotIsRejected() {
        // netProviderAmount inflated by 50 with nothing else adjusted -- the
        // exact shape of bug this test guards against: a UI or mapper change
        // that reports a provider share not backed by the persisted identity.
        Claim claim = balancedClaim()
                .netProviderAmount(new BigDecimal("687.00"))
                .approvedAmount(new BigDecimal("687.00"))
                .build();

        assertThatThrownBy(claim::onUpdate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Financial snapshot mismatch");
    }

    @Test
    void negativeCompanyDiscountIsRejected() {
        Claim claim = balancedClaim()
                .companyDiscountAmount(new BigDecimal("-1.00"))
                .build();

        assertThatThrownBy(claim::onUpdate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("negative component");
    }

    @Test
    void toleranceBoundaryOfFiveCentsIsAccepted() {
        Claim claim = balancedClaim()
                .netProviderAmount(new BigDecimal("637.04"))
                .approvedAmount(new BigDecimal("637.04"))
                .build();

        assertThatCode(claim::onUpdate).doesNotThrowAnyException();
    }

    @Test
    void justOverToleranceIsRejected() {
        Claim claim = balancedClaim()
                .netProviderAmount(new BigDecimal("637.06"))
                .approvedAmount(new BigDecimal("637.06"))
                .build();

        assertThatThrownBy(claim::onUpdate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Financial snapshot mismatch");
    }

    @Test
    void zeroDiscountFullyApprovedClaimPassesValidation() {
        Claim claim = Claim.builder()
                .status(ClaimStatus.APPROVED)
                .requestedAmount(new BigDecimal("500.00"))
                .patientCoPay(BigDecimal.ZERO)
                .refusedAmount(BigDecimal.ZERO)
                .companyDiscountAmount(BigDecimal.ZERO)
                .approvedAmount(new BigDecimal("500.00"))
                .netProviderAmount(new BigDecimal("500.00"))
                .build();

        assertThatCode(claim::onUpdate).doesNotThrowAnyException();
    }
}
