package com.waad.tba.modules.simulation.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CategoryGapAnalysisDto {
    private String categoryCode;
    private String categoryName;
    private String parentCategoryCode;

    private int totalServices;
    private int coveredServices;
    private int excludedServices;
    private int noRuleServices;
    private int needsReviewServices;
    private int zeroPriceServices;

    private BigDecimal totalRequestedAmount;
    private BigDecimal totalCoveredAmount;

    private double coverageCompletenessPercent;
    private String recommendedAction;
}
