package com.waad.tba.modules.claim.ruleengine.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class CoverageComputationResult {
    CoverageDecisionStatus decisionStatus;
    String reason;
    BigDecimal requestedAmount;
    BigDecimal coveredAmount;
    BigDecimal patientShare;
    boolean applyUsageDelta;
    int usageTimesDelta;
    BigDecimal usageAmountDelta;
    List<RuleExecutionTrace> trace;
}
