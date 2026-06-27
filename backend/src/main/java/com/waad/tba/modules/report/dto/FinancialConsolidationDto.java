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
    private String employerName;
    @Builder.Default
    private BigDecimal month1 = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal month2 = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal month3 = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal month4 = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal month5 = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal month6 = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal month7 = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal month8 = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal month9 = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal month10 = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal month11 = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal month12 = BigDecimal.ZERO;
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;
}
