package com.waad.tba.modules.preauthorization.security;

import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;

import lombok.RequiredArgsConstructor;

/**
 * Computes WHICH pre-authorizations a caller may reach. Nothing else.
 *
 * The pre-authorization module had no equivalent of MemberAccessScopeResolver,
 * which is why the S-01 escalation chain stopped at member data but not here:
 * a role check alone answers "may this kind of user do this kind of thing",
 * never "to whose records".
 *
 * Deliberately narrow, like its member counterpart. It does not know about
 * inboxes, drafts or approvals; those are separate policies that consume this
 * one answer.
 */
@Component
@RequiredArgsConstructor
public class PreAuthAccessScopeResolver {

    private final AuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public PreAuthAccessScope resolve() {
        return resolveFor(authorizationService.getCurrentUser());
    }

    @Transactional(readOnly = true)
    public PreAuthAccessScope resolveFor(User user) {
        if (user == null) {
            return PreAuthAccessScope.denied("لا يوجد مستخدم مصادق عليه");
        }
        if (!Boolean.TRUE.equals(user.getActive())) {
            return PreAuthAccessScope.denied("حساب المستخدم غير نشط");
        }

        // Granted, not inferred.
        if (authorizationService.isSuperAdmin(user)) {
            return PreAuthAccessScope.global();
        }

        // The TPA's own reviewers work across every provider by definition:
        // deciding a request means comparing it against the whole book, and a
        // reviewer bounded to one employer could not staff the queue at all.
        // This is a narrower grant than it looks -- the review endpoints
        // already require PREAUTH_REVIEW/PREAUTH_APPROVE on top of it, and
        // scope never grants an operation.
        if (authorizationService.isReviewer(user)) {
            return PreAuthAccessScope.global();
        }

        if (authorizationService.isProvider(user)) {
            // A provider account with no provider is a misconfigured account,
            // not a system-wide one.
            return user.getProviderId() == null
                    ? PreAuthAccessScope.denied("مستخدم مقدم خدمة بلا مرفق مرتبط")
                    : PreAuthAccessScope.providers(Set.of(user.getProviderId()));
        }

        if (authorizationService.isEmployerAdmin(user)) {
            return user.getEmployerId() == null
                    ? PreAuthAccessScope.denied("مدير جهة عمل بلا جهة مرتبطة")
                    : PreAuthAccessScope.employers(Set.of(user.getEmployerId()));
        }

        // Data entry, finance and anything this resolver has never heard of.
        // Their role says what they may do, not whose records they may do it
        // to; treating "no employer" as "all employers" is exactly the
        // conflation this class exists to remove.
        if (user.getEmployerId() != null) {
            return PreAuthAccessScope.employers(Set.of(user.getEmployerId()));
        }
        return PreAuthAccessScope.denied(
                "لا يمكن تحديد نطاق المستخدم؛ يلزم ربطه بجهة عمل أو مقدم خدمة");
    }

    /**
     * Narrows to the provider the caller asked for, and refuses if it is
     * outside their scope.
     *
     * A REFUSAL, never an empty result. Quietly returning "no providers"
     * renders as a screen saying this provider has no requests, which is a
     * different and false statement from "you may not look here".
     */
    @Transactional(readOnly = true)
    public PreAuthAccessScope resolveFor(User user, Long requestedProviderId) {
        PreAuthAccessScope scope = resolveFor(user);
        if (scope.isDenied() || requestedProviderId == null) {
            return scope;
        }
        if (!scope.covers(requestedProviderId, null) && !scope.isGlobal()) {
            return PreAuthAccessScope.denied("مقدم الخدمة المطلوب خارج نطاق المستخدم");
        }
        return PreAuthAccessScope.providers(Set.of(requestedProviderId));
    }
}
