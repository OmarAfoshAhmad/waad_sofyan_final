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

    /**
     * Operations that hand over data in bulk or expose the insurer's money.
     * Reaching a member is not enough for these; they need a grant of their
     * own, so a provider is excluded even under an open network.
     */
    private static final java.util.Set<MemberOperation> PRIVILEGED = java.util.Set.of(
            MemberOperation.EXPORT, MemberOperation.VIEW_FINANCIALS);

    /**
     * Authorises a listing, search or export and returns the scope it must be
     * constrained to. Throws on refusal, so there is no value to ignore.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public AuthorizedMemberScope requireListing(MemberOperation operation, Long requestedEmployerId) {
        MemberAccessDecision decision = forListing(operation, requestedEmployerId);
        if (!decision.allowed()) {
            throw new MemberAccessDeniedException(decision.operation(), decision.reason());
        }
        return new AuthorizedMemberScope(decision.operation(), decision.scope());
    }

    /** Authorises reading one member. Throws on refusal. */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public AuthorizedMemberScope requireMember(MemberOperation operation, Long memberEmployerId) {
        MemberAccessDecision decision = forMember(operation, memberEmployerId);
        if (!decision.allowed()) {
            throw new MemberAccessDeniedException(decision.operation(), decision.reason());
        }
        return new AuthorizedMemberScope(decision.operation(), decision.scope());
    }

    /**
     * The raw decision. Package-private on purpose: production code calls
     * requireListing/requireMember, which cannot be ignored. This exists for
     * diagnostics and for tests that assert WHY something was refused.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    MemberAccessDecision forListing(MemberOperation operation, Long requestedEmployerId) {
        com.waad.tba.modules.rbac.entity.User user = authorizationService.getCurrentUser();
        MemberAccessScope scope = scopeResolver.resolveFor(user, requestedEmployerId);

        if (scope.isDenied()) {
            return MemberAccessDecision.deny(operation, scope, scope.reason());
        }
        if (!featureAllowsReading(user)) {
            return MemberAccessDecision.deny(operation, scope, FEATURE_REFUSAL);
        }
        if (PRIVILEGED.contains(operation) && !mayPerformPrivileged(user)) {
            return MemberAccessDecision.deny(operation, scope, privilegedRefusal(operation));
        }
        return MemberAccessDecision.allow(operation, scope);
    }

    /** The raw decision for one member. Package-private for the same reason. */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    MemberAccessDecision forMember(MemberOperation operation, Long memberEmployerId) {
        com.waad.tba.modules.rbac.entity.User user = authorizationService.getCurrentUser();
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
        if (PRIVILEGED.contains(operation) && !mayPerformPrivileged(user)) {
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
     * A provider treats patients; it does not administer the insurer's book.
     * Bulk extraction and detailed balances stay with the roles that own the
     * commercial relationship.
     */
    private boolean mayPerformPrivileged(com.waad.tba.modules.rbac.entity.User user) {
        // DATA_ENTRY is deliberately absent. The role enters identity,
        // employer, policy and basic eligibility; it does not need consumed
        // and remaining limits, claim history, or a bulk file of members.
        // Granting them widens the role past its name, and with no
        // fine-grained permissions to lean on, refusal is the safe default.
        // A future MEMBER_FINANCIAL_VIEW / MEMBER_EXPORT grant can reopen it
        // deliberately.
        return authorizationService.isSuperAdmin(user)
                || authorizationService.isEmployerAdmin(user);
    }

    private String privilegedRefusal(MemberOperation operation) {
        return operation == MemberOperation.EXPORT
                ? "تصدير بيانات المستفيدين غير مسموح لهذا الدور"
                : "الاطلاع على التفاصيل المالية غير مسموح لهذا الدور";
    }
}
