package com.waad.tba.modules.simulation.dto;

import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyCoverageService.CoverageSource;
import com.waad.tba.modules.simulation.enums.SimulationSeverity;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class SimulationDecision {
    private CoverageSource coverageSource;
    private String coverageStatus;
    private String coverageReason;
    private String recommendedAction;
    private SimulationSeverity severity;

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
