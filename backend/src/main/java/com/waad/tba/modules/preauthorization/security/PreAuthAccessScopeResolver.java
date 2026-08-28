package com.waad.tba.modules.preauthorization.security;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.permission.PermissionGuard;
import com.waad.tba.security.AuthorizationService;

import lombok.RequiredArgsConstructor;

/** Resolves tenant reach; operation permissions remain an independent gate. */
@Component
@RequiredArgsConstructor
public class PreAuthAccessScopeResolver {
    private final PermissionGuard permissionGuard;
    private final AuthorizationService authorizationService;
    private final ReviewerProviderIsolationService reviewerIsolationService;

    public AuthorizedPreAuthScope requireViewScope() {
        if (!permissionGuard.has("PREAUTH_VIEW")) {
            throw new AccessDeniedException("لا تملك صلاحية عرض الموافقات المسبقة");
        }
        PreAuthAccessScope scope = resolve();
        if (scope.isDenied()) throw new AccessDeniedException(scope.reason());
        return new AuthorizedPreAuthScope(scope);
    }

    @Transactional(readOnly = true)
    public PreAuthAccessScope resolve() {
        return resolveFor(authorizationService.getCurrentUser());
    }

    @Transactional(readOnly = true)
    public PreAuthAccessScope resolveFor(User user) {
        if (user == null) return PreAuthAccessScope.denied("المصادقة مطلوبة");
        if (!Boolean.TRUE.equals(user.getActive())) return PreAuthAccessScope.denied("حساب المستخدم غير نشط");
        if (authorizationService.isSuperAdmin(user)) return PreAuthAccessScope.global();
        if (user.getProviderId() != null) return PreAuthAccessScope.providers(Set.of(user.getProviderId()));
        if (user.getEmployerId() != null) return PreAuthAccessScope.employers(Set.of(user.getEmployerId()));
        if (reviewerIsolationService.isSubjectToIsolation(user)) {
            return PreAuthAccessScope.providers(new LinkedHashSet<>(reviewerIsolationService.getAllowedProviderIds(user)));
        }
        if (authorizationService.isReviewer(user)) return PreAuthAccessScope.global();
        return PreAuthAccessScope.denied("لا يمكن تحديد نطاق المستخدم؛ يلزم ربطه بجهة أو منحه نطاقاً عاماً صريحاً");
    }

    @Transactional(readOnly = true)
    public PreAuthAccessScope resolveFor(User user, Long requestedProviderId) {
        PreAuthAccessScope scope = resolveFor(user);
        if (scope.isDenied() || requestedProviderId == null) return scope;
        if (!scope.isGlobal() && !scope.covers(requestedProviderId, null)) {
            return PreAuthAccessScope.denied("مقدم الخدمة المطلوب خارج نطاق المستخدم");
        }
        return PreAuthAccessScope.providers(Set.of(requestedProviderId));
    }
}
