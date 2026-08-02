package com.waad.tba.modules.audit.enums;

/**
 * Supported audit actions for medical claim platform traceability.
 */
public enum AuditAction {
    VIEW,
    STATUS_CHANGE,
    RECALCULATION,
    MANUAL_OVERRIDE,
    APPROVED,
    REJECTED,
    CREATED,
    UPDATED,
    DELETED,
    RESTORED,
    ACTIVATED,
    SUSPENDED,
    TERMINATED,
    IMPORTED,
    EXPORTED,
    CLAIM_VOIDED,
    SIMULATION_EXECUTED
}
