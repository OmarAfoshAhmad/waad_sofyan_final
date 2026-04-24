package com.waad.tba.modules.claim.dto;

import com.waad.tba.modules.claim.entity.ClaimStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimResponseDto {
    private Long id;
    private Long memberId;
    private String memberFullName;
    private String memberCode;
    private Long providerId;
    private String providerName;
    private LocalDate serviceDate;
    private ClaimStatus status;
    private String diagnosisCode;
    private String diagnosisDescription;
    private String doctorName;
    private String complaint;
    private String reviewerComment;

    // Financials
    private BigDecimal requestedAmount;
    private BigDecimal approvedAmount;
    private BigDecimal refusedAmount;
    private BigDecimal patientCoPay;
    private BigDecimal netProviderAmount;

    // Lines
    private List<ClaimLineDto> lines;

    private Boolean manualCategoryEnabled;
    private String primaryCategoryCode;
    private Boolean fullCoverage;
}
