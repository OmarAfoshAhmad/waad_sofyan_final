package com.waad.tba.modules.member.security;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Decides READ access to member data: listing, searching, viewing a record,
 * viewing its financials, and exporting.
 *
 * It consumes MemberAccessScopeResolver's answer and adds the operation
 * dimension on top. Reach and permission are separate questions: an
 * open-network provider reaches every employer's members, which does not make
 * it entitled to export them or to read their detailed balances.
 */
@Component
@RequiredArgsConstructor
public class MemberQueryAccessPolicy {

    private final MemberAccessScopeResolver scopeResolver;
    private final com.waad.tba.security.AuthorizationService authorizationService;
    private final com.waad.tba.modules.rbac.permission.EffectivePermissionService effectivePermissions;

    /**
     * Authorises a listing, search or export and returns the scope it must be
     * constrained to. Throws on refusal, so there is no value to ignore.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public AuthorizedMemberScope requireListing(MemberOperation operation, Long requestedEmployerId) {
        com.waad.tba.modules.rbac.entity.User user = authorizationService.getCurrentUser();
        MemberAccessDecision decision = forListing(user, operation, requestedEmployerId);
        if (!decision.allowed()) {
            throw new MemberAccessDeniedException(decision.operation(), decision.reason());
        }
        return new AuthorizedMemberScope(decision.operation(), decision.scope(), authorizationService.isProvider(user));
    }

    /** Authorises reading one member. Throws on refusal. */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public AuthorizedMemberScope requireMember(MemberOperation operation, Long memberEmployerId) {
        com.waad.tba.modules.rbac.entity.User user = authorizationService.getCurrentUser();
        MemberAccessDecision decision = forMember(user, operation, memberEmployerId);
        if (!decision.allowed()) {
            throw new MemberAccessDeniedException(decision.operation(), decision.reason());
        }
        return new AuthorizedMemberScope(decision.operation(), decision.scope(), authorizationService.isProvider(user));
    }

    /**
     * The raw decision. Package-private on purpose: production code calls
     * requireListing/requireMember, which cannot be ignored. This exists for
     * diagnostics and for tests that assert WHY something was refused.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    MemberAccessDecision forListing(MemberOperation operation, Long requestedEmployerId) {
        com.waad.tba.modules.rbac.entity.User user = authorizationService.getCurrentUser();
        return forListing(user, operation, requestedEmployerId);
    }

    private MemberAccessDecision forListing(com.waad.tba.modules.rbac.entity.User user,
            MemberOperation operation, Long requestedEmployerId) {
        MemberAccessScope scope = scopeResolver.resolveFor(user, requestedEmployerId);

        if (scope.isDenied()) {
            return MemberAccessDecision.deny(operation, scope, scope.reason());
        }
        if (!featureAllowsReading(user)) {
            return MemberAccessDecision.deny(operation, scope, FEATURE_REFUSAL);
        }
        if (!holdsGrantFor(user, operation)) {
            return MemberAccessDecision.deny(operation, scope, privilegedRefusal(operation));
        }
        return MemberAccessDecision.allow(operation, scope);
    }

    /** The raw decision for one member. Package-private for the same reason. */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    MemberAccessDecision forMember(MemberOperation operation, Long memberEmployerId) {
        com.waad.tba.modules.rbac.entity.User user = authorizationService.getCurrentUser();
        return forMember(user, operation, memberEmployerId);
    }

    private MemberAccessDecision forMember(com.waad.tba.modules.rbac.entity.User user,
            MemberOperation operation, Long memberEmployerId) {
        MemberAccessScope scope = scopeResolver.resolveFor(user);

        if (scope.isDenied()) {
            return MemberAccessDecision.deny(operation, scope, scope.reason());
        }
        if (!featureAllowsReading(user)) {
            return MemberAccessDecision.deny(operation, scope, FEATURE_REFUSAL);
        }
        if (!scope.covers(memberEmployerId)) {
            // The record exists and is not theirs. Answering "not found"
            // would be a lie, and answering with the record is the leak that
            // editing an id in a URL is meant to find.
            return MemberAccessDecision.deny(operation, scope,
                    "المستفيد المطلوب خارج نطاق المستخدم");
        }
        if (!holdsGrantFor(user, operation)) {
            return MemberAccessDecision.deny(operation, scope, privilegedRefusal(operation));
        }
        return MemberAccessDecision.allow(operation, scope);
    }

    private static final String FEATURE_REFUSAL = "الاطلاع على المستفيدين معطّل لهذا الحساب";

    /**
     * The per-account VIEW_MEMBERS toggle, which an employer's contract can
     * switch off.
     *
     * It was previously consulted by the listing, search and count paths only,
     * which meant an employer administrator barred from the list could still
     * open any one of its members by id. Reading it here applies it to every
     * read instead: refusing the collection while serving its elements one at a
     * time is not a restriction, it is a slower list.
     *
     * The underlying check already returns true for every role it does not
     * govern, so this narrows nothing beyond the employer administrators the
     * toggle was written for.
     */
    private boolean featureAllowsReading(com.waad.tba.modules.rbac.entity.User user) {
        return authorizationService.canEmployerViewMembers(user);
    }

    /**
     * Whether this user holds the grant the operation answers to.
     *
     * Unprivileged operations need nothing beyond reach, so they pass here.
     * For the rest the effective permission set decides, which means a
     * role template AND any per-user grant or revocation on top of it -- the
     * revocation matters as much as the grant, since an administrator taking
     * a permission away must close the door the same day.
     */
    private boolean holdsGrantFor(com.waad.tba.modules.rbac.entity.User user, MemberOperation operation) {
        var required = MemberOperationPermissions.requiredFor(operation);
        if (required.isEmpty()) {
            return true;
        }
        return effectivePermissions.resolve(user).contains(required.get());
    }

    private String privilegedRefusal(MemberOperation operation) {
        return switch (operation) {
            case EXPORT -> "تصدير بيانات المستفيدين غير مسموح لهذا الحساب";
            case VIEW_LIMITS, LIST_LIMITS -> "الاطلاع على سقوف المستفيدين غير مسموح لهذا الحساب";
            default -> "الاطلاع على التفاصيل المالية غير مسموح لهذا الحساب";
        };
    }
}
