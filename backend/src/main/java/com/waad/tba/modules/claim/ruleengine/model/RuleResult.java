package com.waad.tba.modules.claim.ruleengine.model;

import lombok.Builder;
import lombok.Value;

import java.util.LinkedHashMap;
import java.util.Map;

@Value
@Builder
public class RuleResult {
    RuleStatus status;
    String reason;

    Map<String, Object> modifiedValues;

    Map<String, Object> executionImpact;

    public Map<String, Object> getModifiedValues() {
        return modifiedValues == null ? Map.of() : modifiedValues;
    }

    public Map<String, Object> getExecutionImpact() {
        return executionImpact == null ? Map.of() : executionImpact;
    }

    public static RuleResult pass(String reason) {
        return RuleResult.builder()
                .status(RuleStatus.PASS)
                .reason(reason)
                .modifiedValues(new LinkedHashMap<>())
                .executionImpact(new LinkedHashMap<>())
                .build();
    }

    public static RuleResult skip(String reason) {
        return RuleResult.builder()
                .status(RuleStatus.SKIP)
                .reason(reason)
                .modifiedValues(new LinkedHashMap<>())
                .executionImpact(new LinkedHashMap<>())
                .build();
    }

    public static RuleResult reject(String reason, Map<String, Object> modifiedValues) {
        return RuleResult.builder()
                .status(RuleStatus.REJECT)
                .reason(reason)
                .modifiedValues(modifiedValues == null ? new LinkedHashMap<>() : modifiedValues)
                .executionImpact(new LinkedHashMap<>())
                .build();
    }

    public static RuleResult modify(String reason, Map<String, Object> modifiedValues) {
        return RuleResult.builder()
                .status(RuleStatus.MODIFY)
                .reason(reason)
                .modifiedValues(modifiedValues == null ? new LinkedHashMap<>() : modifiedValues)
                .executionImpact(new LinkedHashMap<>())
                .build();
    }
}
