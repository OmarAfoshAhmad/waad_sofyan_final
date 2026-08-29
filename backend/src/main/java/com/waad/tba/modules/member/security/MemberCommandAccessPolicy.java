package com.waad.tba.modules.member.security;

import java.util.Collection;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Decides WRITE access to member records.
 *
 * It answers "may this caller do this to this record", and nothing else. The
 * status service still decides whether the transition is commercially and
 * financially valid, and the delete guard still decides whether a record has
 * a footprint. A permission check is not a business rule, and neither
 * substitutes for the other.
 *
 * The employer it judges against must come from the STORED record, never from
 * the request. A caller who may edit their own tenant's member could
 * otherwise send another tenant's id in the body and have the check pass
 * against the value they chose.
 */
@Component
@RequiredArgsConstructor
public class MemberCommandAccessPolicy {

    private final MemberAccessScopeResolver scopeResolver;
    private final com.waad.tba.security.AuthorizationService authorizationService;
    private final com.waad.tba.modules.rbac.permission.EffectivePermissionService effectivePermissions;

    /**
     * Commands that reach across every tenant by definition, so they belong
     * to whoever administers the system rather than to any one employer.
     */
    private static final java.util.Set<MemberOperation> SYSTEM_WIDE = java.util.Set.of(
            MemberOperation.RESET_KINSHIP, MemberOperation.RESOLVE_DUPLICATES);

    /** Authorises one command against the employer that OWNS the record. */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public AuthorizedMemberScope require(MemberOperation operation, Long recordEmployerId) {
        MemberAccessDecision decision = decide(operation, recordEmployerId);
        if (!decision.allowed()) {
            throw new MemberAccessDeniedException(decision.operation(), decision.reason());
        }
        return new AuthorizedMemberScope(decision.operation(), decision.scope());
    }

    /**
     * Authorises a bulk command: every element or none.
     *
     * Skipping the elements a caller may not touch is the dangerous outcome.
     * They are told the operation succeeded and never learn that part of
     * their selection was quietly dropped -- so a bulk terminate appears to
     * have ended forty memberships when it ended thirty.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public AuthorizedMemberScope requireBulk(MemberOperation operation,
            Collection<Long> recordEmployerIds) {

        if (recordEmployerIds == null || recordEmployerIds.isEmpty()) {
            // An empty selection is not a successful no-op: it means the
            // caller's intent was lost somewhere between screen and server.
            throw new MemberAccessDeniedException(operation,
                    "لا توجد عناصر ضمن العملية الجماعية");
        }
        AuthorizedMemberScope authorized = null;
        for (Long employerId : recordEmployerIds) {
            authorized = require(operation, employerId);
        }
        return authorized;
    }

    /**
     * The raw decision. Package-private so production code must go through
     * require/requireBulk, which cannot be called and ignored.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    MemberAccessDecision decide(MemberOperation operation, Long recordEmployerId) {
        com.waad.tba.modules.rbac.entity.User user = authorizationService.getCurrentUser();
        MemberAccessScope scope = scopeResolver.resolveFor(user);

        if (scope.isDenied()) {
            return MemberAccessDecision.deny(operation, scope, scope.reason());
        }

        // Reach first: a command against a record outside the caller's scope
        // is refused whatever their role allows in principle.
        if (!SYSTEM_WIDE.contains(operation) && !scope.covers(recordEmployerId)) {
            return MemberAccessDecision.deny(operation, scope,
                    "المستفيد المطلوب خارج نطاق المستخدم");
        }

        // The permission decides, and only the permission. A role is a default
        // set of them: granting MEMBER_CHANGE_STATUS to one data-entry user
        // must let them change status inside their own employer, and revoking
        // it from an employer administrator must stop them, whatever either
        // account is called.
        //
        // This used to be a ladder of role names, which made the permission
        // catalogue a decoration: the UI drew its buttons from the bits, the
        // endpoints checked the bits, and then this method ignored them and
        // asked what the account was called. A granted permission produced a
        // visible button and a 403.
        var required = MemberOperationPermissions.requiredFor(operation);
        if (required.isEmpty()) {
            return MemberAccessDecision.allow(operation, scope);
        }
        if (!effectivePermissions.resolve(user).contains(required.get())) {
            return MemberAccessDecision.deny(operation, scope, refusalFor(operation));
        }
        return MemberAccessDecision.allow(operation, scope);
    }

    /**
     * Says which grant is missing rather than which role the account is not.
     * The old wording named the role, which told an administrator to change
     * someone's job title when the answer was to grant a permission.
     */
    private String refusalFor(MemberOperation operation) {
        return switch (operation) {
            case HARD_DELETE -> "الحذف النهائي يتطلب صلاحية الحذف النهائي";
            case RESET_KINSHIP, RESOLVE_DUPLICATES ->
                    "عملية على مستوى النظام تتطلب صلاحية العمليات الخطرة";
            case CHANGE_STATUS, TERMINATE, REINSTATE, BULK_OPERATION ->
                    "تغيير حالة المستفيد يتطلب صلاحية تغيير الحالة";
            case REINSTATE_TERMINATED -> "إعادة عضوية منتهية تتطلب صلاحية خاصة";
            case MANAGE_LIMIT_UPLIFT -> "رفع السقف العام استثناءً يتطلب صلاحية خاصة";
            case TRANSFER_EMPLOYER, TRANSFER_DEPENDENT ->
                    "النقل بين جهات العمل يتطلب صلاحية النقل";
            case CREATE_MEMBER, ADD_DEPENDENT -> "إنشاء مستفيد يتطلب صلاحية الإنشاء";
            case IMPORT_PREVIEW, IMPORT_EXECUTE, IMPORT_HISTORY, IMPORT_ROLLBACK ->
                    "استيراد المستفيدين يتطلب صلاحية الاستيراد";
            default -> "تعديل سجل المستفيد يتطلب صلاحية التعديل";
        };
    }
}
