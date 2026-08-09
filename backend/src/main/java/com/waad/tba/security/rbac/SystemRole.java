package com.waad.tba.security.rbac;

/**
 * Static Role Definitions — Authorization Simplification (Phase 5)
 *
 * This enum is the SINGLE SOURCE OF TRUTH for all roles in the system.
 * Dynamic RBAC (permissions table, role_permissions table) has been removed.
 * All authorization is now role-based via @PreAuthorize("hasRole('...')").
 *
 * ROLES:
 *   SUPER_ADMIN      — Full system access, unrestricted
 *   MEDICAL_REVIEWER — Review/approve claims and pre-authorizations
 *   MEDICAL_REVIEW_HEAD — Reviewer access plus department-head decisions
 *   ACCOUNTANT       — Financial operations (settlements, provider accounts)
 *   PROVIDER_STAFF   — Provider portal access (visits, claims submission)
 *   EMPLOYER_ADMIN   — Employer data access (members, claims for their company)
 *   DATA_ENTRY       — Create/edit operational data (members, visits, claims)
 *   FINANCE_VIEWER   — Read-only financial data access
 *
 * @author TBA WAAD System
 * @version 2.0 — Static role-based authorization
 */
public enum SystemRole {

    SUPER_ADMIN("مدير النظام", "System Super Administrator"),
    MEDICAL_REVIEWER("مراجع طبي", "Medical Claims Reviewer"),
    MEDICAL_REVIEW_HEAD("رئيس قسم المراجعين", "Medical Review Department Head"),
    ACCOUNTANT("محاسب", "Financial Accountant"),
    PROVIDER_STAFF("موظف مقدم خدمة", "Healthcare Provider Staff"),
    EMPLOYER_ADMIN("مدير صاحب العمل", "Employer Administrator"),
    DATA_ENTRY("مدخل بيانات", "Data Entry Operator"),
    FINANCE_VIEWER("مشاهد مالي", "Finance Viewer (Read-Only)");

    private final String displayNameAr;
    private final String displayNameEn;

    SystemRole(String displayNameAr, String displayNameEn) {
        this.displayNameAr = displayNameAr;
        this.displayNameEn = displayNameEn;
    }

    public String getDisplayNameAr() {
        return displayNameAr;
    }

    public String getDisplayNameEn() {
        return displayNameEn;
    }

    /**
     * Parse role from string (case-insensitive, handles ROLE_ prefix).
     */
    public static SystemRole fromString(String roleName) {
        if (roleName == null || roleName.isBlank()) return null;
        String clean = roleName.trim().toUpperCase();
        if (clean.startsWith("ROLE_")) clean = clean.substring(5);
        try {
            return SystemRole.valueOf(clean);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Get all role names as strings.
     */
    public static String[] getAllRoleNames() {
        SystemRole[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].name();
        }
        return names;
    }
}
