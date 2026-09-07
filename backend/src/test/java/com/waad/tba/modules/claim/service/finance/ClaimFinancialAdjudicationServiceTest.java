package com.waad.tba.modules.claim.service.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.entity.ClaimLine;

class ClaimFinancialAdjudicationServiceTest {

    @Test
    @DisplayName("Benefit limit excess is provider-side refused amount, not beneficiary co-pay")
    void should_map_limit_excess_to_refused_amount_not_patient_share() throws Exception {
        ClaimFinancialAdjudicationService service =
                new ClaimFinancialAdjudicationService(null, null, null, null, null, null);
        ClaimLine line = ClaimLine.builder().build();
        WaadFinancialEngine.Result financial = new WaadFinancialEngine.Result(
                bd("3200.00"),
                bd("3200.00"),
                bd("0.00"),
                bd("3200.00"),
                WaadFinancialEngine.LimitMode.LIMITED,
                bd("3000.00"),
                bd("3000.00"),
                bd("200.00"),
                bd("3000.00"),
                bd("0.00"),
                75,
                bd("750.00"),
                bd("950.00"),
                bd("2250.00"),
                bd("0.00"),
                bd("0.00"),
                bd("2250.00"),
                bd("0.00"),
                bd("2250.00"));
        MultiLineMultiBucketEngine.LineResult result =
                new MultiLineMultiBucketEngine.LineResult("INDEX:0", financial, List.of());

        Method apply = ClaimFinancialAdjudicationService.class
                .getDeclaredMethod("apply", ClaimLine.class, MultiLineMultiBucketEngine.LineResult.class);
        apply.setAccessible(true);
        apply.invoke(service, line, result);

        Claim claim = Claim.builder()
                .lines(List.of(line))
                .beneficiaryPaidAmount(bd("850.00"))
                .build();
        ClaimFinancialTotals.aggregate(claim);

        assertThat(line.getPatientShare()).isEqualByComparingTo("750.00");
        assertThat(line.getLimitRefused()).isEqualByComparingTo("200.00");
        assertThat(line.getRefusedAmount()).isEqualByComparingTo("200.00");
        assertThat(claim.getPatientCoPay()).isEqualByComparingTo("750.00");
        assertThat(claim.getRefusedAmount()).isEqualByComparingTo("200.00");
        assertThat(claim.getBeneficiaryPaidTowardCopay()).isEqualByComparingTo("750.00");
        assertThat(claim.getBeneficiaryPaidTowardRefusal()).isEqualByComparingTo("100.00");
        assertThat(claim.getProviderRefusalBalance()).isEqualByComparingTo("100.00");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
