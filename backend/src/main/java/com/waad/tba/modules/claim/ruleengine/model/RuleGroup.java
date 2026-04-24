package com.waad.tba.modules.claim.ruleengine.model;

public enum RuleGroup {
    PRE_VALIDATION_RULES(1),
    COVERAGE_CALCULATION_RULES(2),
    LIMIT_ENFORCEMENT_RULES(3),
    POST_PROCESSING_RULES(4);

    private final int order;

    RuleGroup(int order) {
        this.order = order;
    }

    public int getOrder() {
        return order;
    }
}
