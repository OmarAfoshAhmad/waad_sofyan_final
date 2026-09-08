package com.waad.tba.modules.claim.service.finance;

import static org.assertj.core.api.Assertions.assertThat;

import com.waad.tba.modules.claim.entity.Claim;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ClaimFinancialTotalsTest {

    @Test
    void beneficiaryDirectPaymentIsAdditionalShareThatReducesProviderRefusal() {
        Claim claim = claim("750.00", "200.00", "500.00");

        ClaimFinancialTotals.applyBeneficiaryDirectPaymentSettlement(claim);

        assertThat(claim.getBeneficiaryPaidAmount()).isEqualByComparingTo("500.00");
        assertThat(claim.getBeneficiaryPaidTowardCopay()).isEqualByComparingTo("0.00");
        assertThat(claim.getBeneficiaryPaidTowardRefusal()).isEqualByComparingTo("200.00");
        assertThat(claim.getProviderRefusalBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void beneficiaryDirectPaymentPartialAmountReducesProviderRefusalBalance() {
        Claim claim = claim("1000.00", "576.00", "144.00");

        ClaimFinancialTotals.applyBeneficiaryDirectPaymentSettlement(claim);

        assertThat(claim.getBeneficiaryPaidTowardCopay()).isEqualByComparingTo("0.00");
        assertThat(claim.getBeneficiaryPaidTowardRefusal()).isEqualByComparingTo("144.00");
        assertThat(claim.getProviderRefusalBalance()).isEqualByComparingTo("432.00");
    }

    @Test
    void beneficiaryDirectPaymentMayExceedRefusalWithoutChangingProviderBalanceBelowZero() {
        Claim claim = claim("750.00", "200.00", "1000.00");

        ClaimFinancialTotals.applyBeneficiaryDirectPaymentSettlement(claim);

        assertThat(claim.getBeneficiaryPaidAmount()).isEqualByComparingTo("1000.00");
        assertThat(claim.getBeneficiaryPaidTowardCopay()).isEqualByComparingTo("0.00");
        assertThat(claim.getBeneficiaryPaidTowardRefusal()).isEqualByComparingTo("200.00");
        assertThat(claim.getProviderRefusalBalance()).isEqualByComparingTo("0.00");
    }

    private Claim claim(String patientCopay, String refused, String paid) {
        return Claim.builder()
                .patientCoPay(new BigDecimal(patientCopay))
                .refusedAmount(new BigDecimal(refused))
                .beneficiaryPaidAmount(new BigDecimal(paid))
                .build();
    }
}
