package com.waad.tba.modules.maintenancehub.entity;

public final class MaintenanceOperationType {
    private MaintenanceOperationType() {
    }

    public static final String ISSUE_DETECTED = "ISSUE_DETECTED";
    public static final String ISSUE_REOPENED = "ISSUE_REOPENED";
    public static final String ISSUE_ASSIGNED = "ISSUE_ASSIGNED";
    public static final String ISSUE_RESOLVED = "ISSUE_RESOLVED";
    public static final String ISSUE_IGNORED = "ISSUE_IGNORED";
}
