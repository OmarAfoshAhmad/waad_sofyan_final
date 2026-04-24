package com.waad.tba.modules.claim.ruleengine.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class CoverageRuleRequest {
    Long claimId;
    BigDecimal requestedAmount;
    Integer usedTimes;
    Integer timesLimit;
    BigDecimal usedAmount;
    BigDecimal amountLimit;
    BigDecimal coveragePercent;
    String correlationId;
}
