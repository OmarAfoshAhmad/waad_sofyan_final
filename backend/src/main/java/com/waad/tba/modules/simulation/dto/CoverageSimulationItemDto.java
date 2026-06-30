package com.waad.tba.modules.simulation.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class CoverageSimulationItemDto {
    private Long providerServiceId;
    private String serviceName;
    private String providerServiceCode;
    private BigDecimal contractPrice;

    private String sourceMainCategory;
    private String sourceSubCategory;

    private String insuranceCategoryCode;
    private String insuranceCategoryName;
    private String parentCategoryCode;
    private String encounterType;
    private String medicalSpecialty;

    private String medicalMeaningAr;
    private String procedureType;
    private String bodySystem;
    private String explanationAr;

    private Double classificationConfidence;
    private String classificationSource;

    private String coverageStatus;
    private String coverageReason;
    private String recommendedAction;
    private String severity;

    private Long matchedRuleId;
    private String matchedRuleName;
    private String matchedRuleSource;

    private int coveragePercentage;
    private BigDecimal requestedAmount;
    private BigDecimal coveredAmount;
    private BigDecimal patientShare;
    private BigDecimal companyShare;

    private BigDecimal amountLimit;
    private BigDecimal appliedLimit;
    private String limitEvaluationMode;

    private boolean requiresPreApproval;
    private boolean requiresReview;

    private List<String> warnings;
}
