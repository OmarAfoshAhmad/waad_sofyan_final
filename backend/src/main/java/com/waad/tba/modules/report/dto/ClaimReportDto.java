package com.waad.tba.modules.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimReportDto {
    private String reportDate;
    private String companyName;
    private String companyLogoBase64;
    private List<ClaimStatementReportDto> groupedClaims;

    // Summary info for the cover page
    private String batchCode;
    private String providerName;
    private int claimCount;

    // Grand Totals at the end of the report
    private BigDecimal grandTotalGross;
    private BigDecimal grandTotalNet;
    private BigDecimal grandTotalRejected;
    private BigDecimal grandTotalPatientShare; // New field for Summary Page
    private BigDecimal grandTotalExpectedNet;
    private BigDecimal grandTotalNetDifference;
    private boolean grandTotalInconsistent;

    // Configuration from Settings
    private String reportTitle;
    private String primaryColor;
    private String introText;
    private String footerNote;
    private String sigRightTop;
    private String sigRightBottom;
    private String sigLeftTop;
    private String sigLeftBottom;
}
