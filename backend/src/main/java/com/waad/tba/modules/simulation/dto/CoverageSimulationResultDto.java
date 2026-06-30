package com.waad.tba.modules.simulation.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CoverageSimulationResultDto {
    private String simulationId;
    private Long contractId;
    private Long policyId;
    private LocalDate effectiveDate;
    private String encounterType;
    private LocalDateTime generatedAt;
    private String generatedBy;

    private String policyName;
    private String policyCode;
    private String contractName;
    private String contractReference;

    private String limitEvaluationMode;

    private AdvancedSimulationSummary summary;
    private SimulationFinancials financials;
    private List<CoverageSimulationItemDto> items;
    private List<CategoryGapAnalysisDto> categoryGapAnalysis;
    private List<String> warnings;

    @Data
    @Builder
    public static class AdvancedSimulationSummary {
        private int totalServices;
        private int coveredExact;
        private int coveredByParent;
        private int coveredDefault;
        private int excludedCategory;
        private int noBenefitRule;
        private int invalidCategory;
        private int contextMismatch;
        private int needsReview;
        private int lowConfidence;
        private int priceZero;
        private int conflictingRules;
        private int preApprovalRequired;
        private int limitApplied;
        private int partialCoverage;
    }

    @Data
    @Builder
    public static class SimulationFinancials {
        private BigDecimal totalRequestedAmount;
        private BigDecimal totalCoveredAmount;
        private BigDecimal totalPatientShare;
        private BigDecimal totalCompanyShare;
    }
}
