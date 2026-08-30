package com.waad.tba.modules.audit.enums;

/**
 * Domain entities covered by audit logging.
 */
public enum EntityType {
    CLAIM,
    CLAIM_LINE,
    PREAUTHORIZATION,
    SETTLEMENT,
    MEMBER,
    VISIT,
    PROVIDER,
    PROVIDER_CONTRACT,
    MEDICAL_REVIEWER_PROVIDER,
    FEATURE_FLAG,
    EMPLOYER,
    EMPLOYER_CONTRACT,
    BENEFIT_POLICY,
    PRICE_LIST,
    MEDICAL_DICTIONARY,
    SYSTEM_SETTING,
    USER_SESSION,
    SIMULATION_RUN
}
