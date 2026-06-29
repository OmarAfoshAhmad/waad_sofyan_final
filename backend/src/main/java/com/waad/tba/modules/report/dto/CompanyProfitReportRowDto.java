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
public class CompanyProfitReportRowDto {
    private String employerName;
    private String providerName;
    private Integer month;
    private BigDecimal totalClaimValue;
    private BigDecimal discountPercent;
    private BigDecimal companyDueValue;
}
