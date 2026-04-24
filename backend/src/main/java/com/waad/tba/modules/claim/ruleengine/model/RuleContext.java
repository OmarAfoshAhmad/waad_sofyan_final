package com.waad.tba.modules.claim.ruleengine.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

public class RuleContext {

    public static final int MONEY_SCALE = 2;
    public static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    private final Long claimId;
    private final String correlationId;

    private BigDecimal requestedAmount;
    private int usedTimes;
    private Integer timesLimit;
    private BigDecimal usedAmount;
    private BigDecimal amountLimit;
    private BigDecimal coveragePercent;

    private BigDecimal coveredAmount;
    private BigDecimal patientShare;
    private boolean terminalRejected;
    private String rejectionReason;

    private RuleContext(
            Long claimId,
            String correlationId,
            BigDecimal requestedAmount,
            int usedTimes,
            Integer timesLimit,
            BigDecimal usedAmount,
            BigDecimal amountLimit,
            BigDecimal coveragePercent) {
        this.claimId = claimId;
        this.correlationId = correlationId;
        this.requestedAmount = money(requestedAmount);
        this.usedTimes = Math.max(0, usedTimes);
        this.timesLimit = timesLimit;
        this.usedAmount = money(usedAmount);
        this.amountLimit = amountLimit == null ? null : money(amountLimit);
        this.coveragePercent = coveragePercent == null ? null : coveragePercent.setScale(4, MONEY_ROUNDING);
        this.coveredAmount = this.requestedAmount;
        this.patientShare = BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
        this.terminalRejected = false;
    }

    public static RuleContext from(CoverageRuleRequest request) {
        return new RuleContext(
                request.getClaimId(),
                request.getCorrelationId(),
                request.getRequestedAmount(),
                request.getUsedTimes() == null ? 0 : request.getUsedTimes(),
                request.getTimesLimit(),
                request.getUsedAmount() == null ? BigDecimal.ZERO : request.getUsedAmount(),
                request.getAmountLimit(),
                request.getCoveragePercent());
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("claimId", claimId);
        m.put("requestedAmount", requestedAmount);
        m.put("usedTimes", usedTimes);
        m.put("timesLimit", timesLimit);
        m.put("usedAmount", usedAmount);
        m.put("amountLimit", amountLimit);
        m.put("coveragePercent", coveragePercent);
        m.put("coveredAmount", coveredAmount);
        m.put("patientShare", patientShare);
        m.put("terminalRejected", terminalRejected);
        m.put("rejectionReason", rejectionReason);
        m.put("correlationId", correlationId);
        return m;
    }

    public void apply(RuleResult result) {
        if (result == null || result.getModifiedValues() == null) {
            return;
        }

        for (Map.Entry<String, Object> e : result.getModifiedValues().entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();

            switch (key) {
                case "coveragePercent" ->
                    this.coveragePercent = value == null ? null : toDecimal(value).setScale(4, MONEY_ROUNDING);
                case "coveredAmount" -> this.coveredAmount = money(toDecimal(value));
                case "patientShare" -> this.patientShare = money(toDecimal(value));
                case "rejectionReason" -> this.rejectionReason = value == null ? null : String.valueOf(value);
                case "terminalRejected" ->
                    this.terminalRejected = value != null && Boolean.parseBoolean(String.valueOf(value));
                default -> {
                    // Controlled mutation only; ignore unknown keys.
                }
            }
        }

        if (coveredAmount == null) {
            coveredAmount = BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
        }
        if (patientShare == null) {
            patientShare = money(requestedAmount.subtract(coveredAmount));
        }
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    public Long getClaimId() {
        return claimId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public int getUsedTimes() {
        return usedTimes;
    }

    public Integer getTimesLimit() {
        return timesLimit;
    }

    public BigDecimal getUsedAmount() {
        return usedAmount;
    }

    public BigDecimal getAmountLimit() {
        return amountLimit;
    }

    public BigDecimal getCoveragePercent() {
        return coveragePercent;
    }

    public BigDecimal getCoveredAmount() {
        return coveredAmount;
    }

    public BigDecimal getPatientShare() {
        return patientShare;
    }

    public boolean isTerminalRejected() {
        return terminalRejected;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }
}
