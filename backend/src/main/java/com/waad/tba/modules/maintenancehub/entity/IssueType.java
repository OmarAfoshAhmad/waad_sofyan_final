package com.waad.tba.modules.maintenancehub.entity;

/**
 * Known issue-type codes. Deliberately a set of String constants, not a Java enum: the
 * ledger's {@code issue_type} column is intentionally unconstrained in the database so
 * a future detector (phase 4+) can register a new type without a schema migration.
 * Constants here exist for compile-time safety at call sites that already know their type.
 */
public final class IssueType {
    private IssueType() {
    }

    public static final String KINSHIP_MISMATCH = "KINSHIP_MISMATCH";
    public static final String DUPLICATE_MEMBER = "DUPLICATE_MEMBER";
    public static final String CARD_NUMBERING = "CARD_NUMBERING";
    public static final String BALANCE_ANOMALY = "BALANCE_ANOMALY";
    public static final String ZERO_APPROVED_CLAIM = "ZERO_APPROVED_CLAIM";
    public static final String UNLEDGERED_CLAIM = "UNLEDGERED_CLAIM";
    public static final String BACKEND_ERROR = "BACKEND_ERROR";
    public static final String FRONTEND_ERROR = "FRONTEND_ERROR";
    public static final String BACKUP_FAILURE = "BACKUP_FAILURE";
    public static final String DB_ANOMALY = "DB_ANOMALY";
}
