package com.waad.tba.modules.claim.ruleengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waad.tba.modules.claim.ruleengine.entity.ClaimCoverageRule;
import com.waad.tba.modules.claim.ruleengine.entity.ClaimRuleExecutionAudit;
import com.waad.tba.modules.claim.ruleengine.model.*;
import com.waad.tba.modules.claim.ruleengine.repository.ClaimCoverageRuleRepository;
import com.waad.tba.modules.claim.ruleengine.repository.ClaimRuleExecutionAuditRepository;
import com.waad.tba.modules.claim.ruleengine.rules.AmountLimitRule;
import com.waad.tba.modules.claim.ruleengine.rules.CoveragePercentRule;
import com.waad.tba.modules.claim.ruleengine.rules.TimesLimitRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialRuleEngineServiceTest {

    @Mock
    private ClaimCoverageRuleRepository ruleRepository;

    @Mock
    private ClaimRuleExecutionAuditRepository auditRepository;

    private FinancialRuleEngineService engineService;

    @BeforeEach
    void setUp() {
        lenient().when(auditRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        engineService = new FinancialRuleEngineService(
                ruleRepository,
                auditRepository,
                new ObjectMapper(),
                List.of(new TimesLimitRule(), new CoveragePercentRule(), new AmountLimitRule()));
    }

    @Test
    @DisplayName("hard stop must reject immediately when times limit is exceeded")
    void shouldHardStopWhenTimesLimitExceeded() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(
                rule("Times Limit Rule", RuleType.TIMES_LIMIT_RULE, RuleGroup.PRE_VALIDATION_RULES, 10, "[]"),
                rule("Coverage Percent Rule", RuleType.COVERAGE_PERCENT_RULE, RuleGroup.COVERAGE_CALCULATION_RULES, 20,
                        "[]")));

        CoverageComputationResult result = engineService.evaluate(CoverageRuleRequest.builder()
                .claimId(1L)
                .requestedAmount(new BigDecimal("100.00"))
                .usedTimes(5)
                .timesLimit(5)
                .usedAmount(BigDecimal.ZERO)
                .amountLimit(new BigDecimal("1000.00"))
                .coveragePercent(new BigDecimal("80.00"))
                .correlationId("CORR-1")
                .build());

        assertEquals(CoverageDecisionStatus.REJECTED, result.getDecisionStatus());
        assertEquals(new BigDecimal("0.00"), result.getCoveredAmount());
        assertEquals(new BigDecimal("100.00"), result.getPatientShare());
        assertFalse(result.isApplyUsageDelta());
        assertEquals(1, result.getTrace().size());
        assertEquals(RuleType.TIMES_LIMIT_RULE, result.getTrace().get(0).getRuleType());
    }

    @Test
    @DisplayName("amount limit must apply soft cap after coverage percent")
    void shouldApplyPartialCapWhenAmountLimitExceeded() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(
                rule("Times Limit Rule", RuleType.TIMES_LIMIT_RULE, RuleGroup.PRE_VALIDATION_RULES, 10, "[]"),
                rule("Coverage Percent Rule", RuleType.COVERAGE_PERCENT_RULE, RuleGroup.COVERAGE_CALCULATION_RULES, 20,
                        "[]"),
                rule("Amount Limit Rule", RuleType.AMOUNT_LIMIT_RULE, RuleGroup.LIMIT_ENFORCEMENT_RULES, 30,
                        "[\"Coverage Percent Rule\"]")));

        CoverageComputationResult result = engineService.evaluate(CoverageRuleRequest.builder()
                .claimId(2L)
                .requestedAmount(new BigDecimal("200.00"))
                .usedTimes(1)
                .timesLimit(5)
                .usedAmount(new BigDecimal("900.00"))
                .amountLimit(new BigDecimal("1000.00"))
                .coveragePercent(new BigDecimal("80.00"))
                .correlationId("CORR-2")
                .build());

        assertEquals(CoverageDecisionStatus.PARTIAL_APPROVED, result.getDecisionStatus());
        assertEquals(new BigDecimal("100.00"), result.getCoveredAmount());
        assertEquals(new BigDecimal("100.00"), result.getPatientShare());
        assertTrue(result.isApplyUsageDelta());
        assertEquals(1, result.getUsageTimesDelta());
        assertEquals(new BigDecimal("100.00"), result.getUsageAmountDelta());
    }

    @Test
    @DisplayName("rule ordering must execute by group before priority")
    void shouldRespectRuleOrderingByGroupThenPriority() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(
                rule("Amount Limit Rule", RuleType.AMOUNT_LIMIT_RULE, RuleGroup.LIMIT_ENFORCEMENT_RULES, 1, "[]"),
                rule("Times Limit Rule", RuleType.TIMES_LIMIT_RULE, RuleGroup.PRE_VALIDATION_RULES, 99, "[]"),
                rule("Coverage Percent Rule", RuleType.COVERAGE_PERCENT_RULE, RuleGroup.COVERAGE_CALCULATION_RULES, 5,
                        "[]")));

        CoverageComputationResult result = engineService.evaluate(CoverageRuleRequest.builder()
                .claimId(3L)
                .requestedAmount(new BigDecimal("100.00"))
                .usedTimes(0)
                .timesLimit(3)
                .usedAmount(BigDecimal.ZERO)
                .amountLimit(new BigDecimal("1000.00"))
                .coveragePercent(new BigDecimal("80.00"))
                .correlationId("CORR-3")
                .build());

        assertEquals(RuleType.TIMES_LIMIT_RULE, result.getTrace().get(0).getRuleType());
        assertEquals(RuleType.COVERAGE_PERCENT_RULE, result.getTrace().get(1).getRuleType());
        assertEquals(RuleType.AMOUNT_LIMIT_RULE, result.getTrace().get(2).getRuleType());
    }

    @Test
    @DisplayName("engine must fail when dependency rule is missing")
    void shouldFailWhenDependencyNotSatisfied() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(
                rule("Amount Limit Rule", RuleType.AMOUNT_LIMIT_RULE, RuleGroup.LIMIT_ENFORCEMENT_RULES, 30,
                        "[\"Coverage Percent Rule\"]")));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> engineService.evaluate(CoverageRuleRequest.builder()
                        .claimId(4L)
                        .requestedAmount(new BigDecimal("100.00"))
                        .usedTimes(0)
                        .timesLimit(3)
                        .usedAmount(BigDecimal.ZERO)
                        .amountLimit(new BigDecimal("500.00"))
                        .coveragePercent(new BigDecimal("80.00"))
                        .correlationId("CORR-4")
                        .build()));

        assertTrue(ex.getMessage().contains("Dependency rule not satisfied"));
    }

    @Test
    @DisplayName("rejected or zero-covered results must not produce usage deltas")
    void shouldNotCreateUsageDeltaForRejectedOrZeroCoveredResults() {
        when(ruleRepository.findByEnabledTrue()).thenReturn(List.of(
                rule("Times Limit Rule", RuleType.TIMES_LIMIT_RULE, RuleGroup.PRE_VALIDATION_RULES, 10, "[]"),
                rule("Coverage Percent Rule", RuleType.COVERAGE_PERCENT_RULE, RuleGroup.COVERAGE_CALCULATION_RULES, 20,
                        "[]"),
                rule("Amount Limit Rule", RuleType.AMOUNT_LIMIT_RULE, RuleGroup.LIMIT_ENFORCEMENT_RULES, 30, "[]")));

        CoverageComputationResult result = engineService.evaluate(CoverageRuleRequest.builder()
                .claimId(5L)
                .requestedAmount(new BigDecimal("100.00"))
                .usedTimes(0)
                .timesLimit(3)
                .usedAmount(new BigDecimal("100.00"))
                .amountLimit(new BigDecimal("100.00"))
                .coveragePercent(new BigDecimal("80.00"))
                .correlationId("CORR-5")
                .build());

        assertEquals(CoverageDecisionStatus.PARTIAL_APPROVED, result.getDecisionStatus());
        assertEquals(new BigDecimal("0.00"), result.getCoveredAmount());
        assertFalse(result.isApplyUsageDelta());
        assertEquals(0, result.getUsageTimesDelta());
        assertEquals(new BigDecimal("0.00"), result.getUsageAmountDelta());
    }

    private ClaimCoverageRule rule(String name, RuleType type, RuleGroup group, int priority, String depsJson) {
        return ClaimCoverageRule.builder()
                .id((long) Math.abs(name.hashCode()))
                .name(name)
                .type(type)
                .priority(priority)
                .enabled(true)
                .ruleGroup(group)
                .dependencyRules(depsJson)
                .configuration("{}")
                .build();
    }
}
