package com.waad.tba.modules.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialConsolidationDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyFinancials {
        @Builder.Default
        private BigDecimal requestedAmount = BigDecimal.ZERO;
        @Builder.Default
        private BigDecimal approvedAmount = BigDecimal.ZERO;
        @Builder.Default
        private BigDecimal rejectedAmount = BigDecimal.ZERO;
        @Builder.Default
        private BigDecimal paidAmount = BigDecimal.ZERO;
        @Builder.Default
        private BigDecimal remainingAmount = BigDecimal.ZERO;
        @Builder.Default
        private BigDecimal companyDiscountAmount = BigDecimal.ZERO;
    }

    private String employerName;
    
    @Builder.Default
    private MonthlyFinancials month1 = new MonthlyFinancials();
    @Builder.Default
    private MonthlyFinancials month2 = new MonthlyFinancials();
    @Builder.Default
    private MonthlyFinancials month3 = new MonthlyFinancials();
    @Builder.Default
    private MonthlyFinancials month4 = new MonthlyFinancials();
    @Builder.Default
    private MonthlyFinancials month5 = new MonthlyFinancials();
    @Builder.Default
    private MonthlyFinancials month6 = new MonthlyFinancials();
    @Builder.Default
    private MonthlyFinancials month7 = new MonthlyFinancials();
    @Builder.Default
    private MonthlyFinancials month8 = new MonthlyFinancials();
    @Builder.Default
    private MonthlyFinancials month9 = new MonthlyFinancials();
    @Builder.Default
    private MonthlyFinancials month10 = new MonthlyFinancials();
    @Builder.Default
    private MonthlyFinancials month11 = new MonthlyFinancials();
    @Builder.Default
    private MonthlyFinancials month12 = new MonthlyFinancials();
    
    @Builder.Default
    private MonthlyFinancials totalAmount = new MonthlyFinancials();
}
