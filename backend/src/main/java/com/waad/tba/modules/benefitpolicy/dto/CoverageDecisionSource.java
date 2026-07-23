package com.waad.tba.modules.benefitpolicy.dto;

public enum CoverageDecisionSource {
    EXACT_CATEGORY_RULE,
    PARENT_CATEGORY_RULE,
    NO_BENEFIT_RULE,
    INVALID_CATEGORY,
    EXCLUDED_CATEGORY,
    CONTEXT_MISMATCH,
    LOW_CONFIDENCE,
    PRICE_ZERO
}
