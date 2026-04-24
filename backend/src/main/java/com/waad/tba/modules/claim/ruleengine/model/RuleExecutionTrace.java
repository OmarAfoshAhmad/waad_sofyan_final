package com.waad.tba.modules.claim.ruleengine.model;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class RuleExecutionTrace {
    Long ruleId;
    String ruleName;
    RuleType ruleType;
    RuleGroup ruleGroup;
    RuleStatus decision;
    String reason;
    Map<String, Object> beforeContext;
    Map<String, Object> afterContext;
    Map<String, Object> deltaChanges;
    String correlationId;
    String timestamp;
    long executionTimeMs;
}
