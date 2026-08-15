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

    /**
     * Commands that reach across every tenant by definition, so they belong
     * to whoever administers the system rather than to any one employer.
     */
    private static final java.util.Set<MemberOperation> SYSTEM_WIDE = java.util.Set.of(
            MemberOperation.RESET_KINSHIP, MemberOperation.RESOLVE_DUPLICATES);

    /**
     * Entering a record and deciding its fate are different acts. Data entry
     * creates and corrects; ending, restoring or destroying someone's
     * coverage is an administrative decision with financial consequences.
     */
    private static final java.util.Set<MemberOperation> DATA_ENTRY_MAY = java.util.Set.of(
            MemberOperation.CREATE_MEMBER, MemberOperation.EDIT_DEMOGRAPHICS,
            MemberOperation.ADD_DEPENDENT);

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

        if (authorizationService.isSuperAdmin(user)) {
            return MemberAccessDecision.allow(operation, scope);
        }

        if (SYSTEM_WIDE.contains(operation)) {
            return MemberAccessDecision.deny(operation, scope,
                    "عملية على مستوى النظام تتطلب صلاحية مدير النظام");
        }
        if (operation == MemberOperation.HARD_DELETE) {
            // Physical removal destroys the record and everything explaining
            // it. Deactivation is the answer for everyone else.
            return MemberAccessDecision.deny(operation, scope,
                    "الحذف النهائي يتطلب صلاحية مدير النظام");
        }

        if (authorizationService.isEmployerAdmin(user)) {
            return MemberAccessDecision.allow(operation, scope);
        }
        if (authorizationService.isDataEntry(user)) {
            return DATA_ENTRY_MAY.contains(operation)
                    ? MemberAccessDecision.allow(operation, scope)
                    : MemberAccessDecision.deny(operation, scope,
                            "هذه العملية خارج صلاحيات إدخال البيانات");
        }

        // Providers and reviewers read; the member record is not theirs to
        // change. Anything unrecognised lands here too, which is the safe
        // direction for a role this policy has never heard of.
        return MemberAccessDecision.deny(operation, scope,
                "هذا الدور لا يملك صلاحية الكتابة على سجل المستفيد");
    }
}
