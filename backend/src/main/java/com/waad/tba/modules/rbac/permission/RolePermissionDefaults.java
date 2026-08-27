package com.waad.tba.modules.rbac.permission;

import java.util.EnumSet;
import java.util.Set;

import com.waad.tba.security.rbac.SystemRole;

/** Bootstrap templates only. Runtime templates are persisted in the database. */
public final class RolePermissionDefaults {
    private RolePermissionDefaults() {}

    private static final Set<SystemPermission> REVIEWER = EnumSet.of(
            SystemPermission.MEMBER_VIEW, SystemPermission.MEMBER_FINANCIAL_VIEW,
            SystemPermission.MEMBER_LIMIT_VIEW,
            SystemPermission.EMPLOYER_VIEW,
            SystemPermission.CLAIM_VIEW, SystemPermission.CLAIM_REVIEW,
            SystemPermission.PREAUTH_VIEW, SystemPermission.PREAUTH_REVIEW,
            SystemPermission.PROVIDER_VIEW, SystemPermission.CONTRACT_VIEW,
            SystemPermission.BENEFIT_POLICY_VIEW);

    public static Set<SystemPermission> forRole(SystemRole role) {
        if (role == null) return Set.of();
        return switch (role) {
            case SUPER_ADMIN -> EnumSet.allOf(SystemPermission.class);
            case DATA_ENTRY -> EnumSet.of(SystemPermission.MEMBER_VIEW,
                    SystemPermission.MEMBER_CREATE, SystemPermission.MEMBER_EDIT_IDENTITY,
                    SystemPermission.MEMBER_IMPORT, SystemPermission.MEMBER_LIMIT_VIEW,
                    SystemPermission.EMPLOYER_VIEW,
                    SystemPermission.BENEFIT_POLICY_VIEW);
            case EMPLOYER_ADMIN -> EnumSet.of(SystemPermission.MEMBER_VIEW,
                    SystemPermission.MEMBER_CREATE, SystemPermission.MEMBER_EDIT_IDENTITY,
                    SystemPermission.MEMBER_CHANGE_STATUS, SystemPermission.MEMBER_TRANSFER_EMPLOYER,
                    SystemPermission.MEMBER_EXPORT, SystemPermission.MEMBER_LIMIT_VIEW,
                    SystemPermission.EMPLOYER_VIEW,
                    SystemPermission.CLAIM_VIEW, SystemPermission.PREAUTH_VIEW,
                    SystemPermission.OPERATIONAL_REPORT_VIEW,
                    SystemPermission.BENEFIT_POLICY_VIEW);
            case PROVIDER_STAFF -> EnumSet.of(SystemPermission.MEMBER_VIEW,
                    SystemPermission.MEMBER_LIMIT_VIEW, SystemPermission.EMPLOYER_VIEW,
                    SystemPermission.CLAIM_VIEW, SystemPermission.CLAIM_CREATE,
                    SystemPermission.PREAUTH_VIEW, SystemPermission.PREAUTH_CREATE,
                    SystemPermission.PREAUTH_DELETE,
                    SystemPermission.CONTRACT_VIEW);
            case MEDICAL_REVIEWER -> EnumSet.copyOf(REVIEWER);
            case MEDICAL_REVIEW_HEAD -> with(REVIEWER,
                    SystemPermission.CLAIM_APPROVE, SystemPermission.PREAUTH_APPROVE,
                    SystemPermission.PREAUTH_CANCEL);
            case INSURANCE_MANAGER -> with(REVIEWER,
                    SystemPermission.CLAIM_APPROVE, SystemPermission.CLAIM_REVERSE,
                    SystemPermission.PREAUTH_APPROVE, SystemPermission.PREAUTH_CANCEL,
                    SystemPermission.SETTLEMENT_VIEW,
                    SystemPermission.FINANCIAL_REPORT_VIEW);
            case ACCOUNTANT -> EnumSet.of(SystemPermission.SETTLEMENT_VIEW,
                    SystemPermission.SETTLEMENT_MANAGE, SystemPermission.FINANCIAL_REPORT_VIEW,
                    SystemPermission.CLAIM_VIEW, SystemPermission.CONTRACT_VIEW,
                    SystemPermission.EMPLOYER_VIEW);
            case FINANCE_VIEWER -> EnumSet.of(SystemPermission.SETTLEMENT_VIEW,
                    SystemPermission.FINANCIAL_REPORT_VIEW, SystemPermission.CLAIM_VIEW,
                    SystemPermission.CONTRACT_VIEW, SystemPermission.EMPLOYER_VIEW);
        };
    }

    private static Set<SystemPermission> with(Set<SystemPermission> base, SystemPermission... extras) {
        EnumSet<SystemPermission> result = EnumSet.copyOf(base);
        result.addAll(Set.of(extras));
        return result;
    }
}
