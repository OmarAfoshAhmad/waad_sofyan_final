package com.waad.tba.modules.claim.ruleengine.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.modules.claim.ruleengine.entity.ClaimCoverageRule;
import com.waad.tba.modules.claim.ruleengine.entity.ClaimRuleExecutionAudit;
import com.waad.tba.modules.claim.ruleengine.model.*;
import com.waad.tba.modules.claim.ruleengine.repository.ClaimCoverageRuleRepository;
import com.waad.tba.modules.claim.ruleengine.repository.ClaimRuleExecutionAuditRepository;
import com.waad.tba.modules.claim.ruleengine.rules.FinancialRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialRuleEngineService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final ClaimCoverageRuleRepository ruleRepository;
    private final ClaimRuleExecutionAuditRepository auditRepository;
    private final ObjectMapper objectMapper;
    private final List<FinancialRule> rules;

    @Transactional
    public CoverageComputationResult evaluate(CoverageRuleRequest request) {
        RuleContext context = RuleContext.from(request);
        String correlationId = resolveCorrelationId(request.getCorrelationId());

        List<ClaimCoverageRule> activeRules = loadAndSortRules();
        Map<RuleType, FinancialRule> ruleRegistry = rules.stream()
                .collect(Collectors.toMap(FinancialRule::supportedType, r -> r));

        List<RuleExecutionTrace> traces = new ArrayList<>();
        List<ClaimRuleExecutionAudit> auditRows = new ArrayList<>();
        Set<String> executedRuleNames = new HashSet<>();

        for (ClaimCoverageRule dbRule : activeRules) {
            validateDependencies(dbRule, executedRuleNames);

            FinancialRule executableRule = ruleRegistry.get(dbRule.getType());
            if (executableRule == null) {
                throw new IllegalStateException("No FinancialRule bean registered for type: " + dbRule.getType());
            }

            Map<String, Object> before = context.snapshot();
            long startedNs = System.nanoTime();

            Map<String, Object> configuration = parseConfig(dbRule.getConfiguration());
            RuleResult result = executableRule.evaluate(context, dbRule, configuration);

            if (result.getStatus() == RuleStatus.MODIFY || result.getStatus() == RuleStatus.REJECT) {
                context.apply(result);
            }

            Map<String, Object> after = context.snapshot();
            Map<String, Object> delta = computeDelta(before, after);

            long tookNs = System.nanoTime() - startedNs;
            long tookMs = Math.max(0L, tookNs / 1_000_000L);

            RuleExecutionTrace trace = RuleExecutionTrace.builder()
                    .ruleId(dbRule.getId())
                    .ruleName(dbRule.getName())
                    .ruleType(dbRule.getType())
                    .ruleGroup(dbRule.getRuleGroup())
                    .decision(result.getStatus())
                    .reason(result.getReason())
                    .beforeContext(before)
                    .afterContext(after)
                    .deltaChanges(delta)
                    .correlationId(correlationId)
                    .timestamp(Instant.now().toString())
                    .executionTimeMs(tookMs)
                    .build();
            traces.add(trace);

            auditRows.add(toAuditRow(context.getClaimId(), correlationId, dbRule, trace));
            executedRuleNames.add(dbRule.getName());

            if (result.getStatus() == RuleStatus.REJECT) {
                break;
            }
        }

        auditRepository.saveAll(auditRows);

        CoverageDecisionStatus finalStatus;
        if (context.isTerminalRejected()) {
            finalStatus = CoverageDecisionStatus.REJECTED;
        } else if (context.getCoveredAmount().compareTo(context.getRequestedAmount()) < 0) {
            finalStatus = CoverageDecisionStatus.PARTIAL_APPROVED;
        } else {
            finalStatus = CoverageDecisionStatus.APPROVED;
        }

        BigDecimal usageAmountDelta = BigDecimal.ZERO.setScale(RuleContext.MONEY_SCALE, RuleContext.MONEY_ROUNDING);
        int usageTimesDelta = 0;
        boolean applyUsageDelta = finalStatus != CoverageDecisionStatus.REJECTED
                && context.getCoveredAmount()
                        .compareTo(BigDecimal.ZERO.setScale(RuleContext.MONEY_SCALE, RuleContext.MONEY_ROUNDING)) > 0;

        if (applyUsageDelta) {
            usageTimesDelta = 1;
            usageAmountDelta = context.getCoveredAmount().setScale(RuleContext.MONEY_SCALE, RuleContext.MONEY_ROUNDING);
        }

        return CoverageComputationResult.builder()
                .decisionStatus(finalStatus)
                .reason(context.getRejectionReason())
                .requestedAmount(context.getRequestedAmount())
                .coveredAmount(context.getCoveredAmount())
                .patientShare(context.getPatientShare())
                .applyUsageDelta(applyUsageDelta)
                .usageTimesDelta(usageTimesDelta)
                .usageAmountDelta(usageAmountDelta)
                .trace(traces)
                .build();
    }

    private List<ClaimCoverageRule> loadAndSortRules() {
        return ruleRepository.findByEnabledTrue().stream()
                .sorted(Comparator
                        .comparing((ClaimCoverageRule r) -> r.getRuleGroup().getOrder())
                        .thenComparing(ClaimCoverageRule::getPriority)
                        .thenComparing(ClaimCoverageRule::getId))
                .toList();
    }

    private void validateDependencies(ClaimCoverageRule rule, Set<String> executedRuleNames) {
        List<String> dependencies = parseDependencies(rule.getDependencyRules());
        for (String dependency : dependencies) {
            if (!executedRuleNames.contains(dependency)) {
                throw new IllegalStateException(
                        "Dependency rule not satisfied for '" + rule.getName() + "': " + dependency);
            }
        }
    }

    private List<String> parseDependencies(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid dependency_rules JSON", ex);
        }
    }

    private Map<String, Object> parseConfig(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid rule configuration JSON", ex);
        }
    }

    private Map<String, Object> computeDelta(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> delta = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : after.entrySet()) {
            Object beforeVal = before.get(e.getKey());
            Object afterVal = e.getValue();
            if (!Objects.equals(beforeVal, afterVal)) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("before", beforeVal);
                value.put("after", afterVal);
                delta.put(e.getKey(), value);
            }
        }
        return delta;
    }

    private String resolveCorrelationId(String requestCorrelationId) {
        if (requestCorrelationId != null && !requestCorrelationId.isBlank()) {
            return requestCorrelationId;
        }
        String traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return UUID.randomUUID().toString();
    }

    private ClaimRuleExecutionAudit toAuditRow(Long claimId, String correlationId, ClaimCoverageRule dbRule,
            RuleExecutionTrace trace) {
        return ClaimRuleExecutionAudit.builder()
                .correlationId(correlationId)
                .claimId(claimId)
                .ruleId(dbRule.getId())
                .ruleName(dbRule.getName())
                .ruleType(dbRule.getType())
                .ruleGroup(dbRule.getRuleGroup())
                .decision(trace.getDecision())
                .reason(trace.getReason())
                .beforeContext(writeJson(trace.getBeforeContext()))
                .afterContext(writeJson(trace.getAfterContext()))
                .deltaChanges(writeJson(trace.getDeltaChanges()))
                .executionTimeMs(BigDecimal.valueOf(trace.getExecutionTimeMs())
                        .setScale(3, RoundingMode.HALF_UP))
                .executedAt(LocalDateTime.ofInstant(Instant.parse(trace.getTimestamp()), ZoneOffset.UTC))
                .build();
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj == null ? Map.of() : obj);
        } catch (Exception ex) {
            log.error("Failed to serialize audit payload", ex);
            return "{}";
        }
    }
}
