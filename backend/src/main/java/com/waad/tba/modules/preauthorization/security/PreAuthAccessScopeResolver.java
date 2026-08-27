package com.waad.tba.modules.preauthorization.security;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.modules.rbac.permission.PermissionGuard;
import com.waad.tba.security.AuthorizationService;

import lombok.RequiredArgsConstructor;

/** Resolves PREAUTH_VIEW and its tenant scope as one inseparable decision. */
@Component
@RequiredArgsConstructor
public class PreAuthAccessScopeResolver {
    private final PermissionGuard permissionGuard;
    private final AuthorizationService authorizationService;
    private final ReviewerProviderIsolationService reviewerIsolationService;

    public AuthorizedPreAuthScope requireViewScope() {
        PreAuthAccessScope scope = resolve();
        if (scope.isDenied()) {
            throw new AccessDeniedException(scope.reason());
        }
        return new AuthorizedPreAuthScope(scope);
    }

    PreAuthAccessScope resolve() {
        if (!permissionGuard.has("PREAUTH_VIEW")) {
            return PreAuthAccessScope.denied("لا تملك صلاحية عرض الموافقات المسبقة");
        }
        var user = authorizationService.getCurrentUser();
        if (user == null) {
            return PreAuthAccessScope.denied("المصادقة مطلوبة");
        }
        if (user.getProviderId() != null) {
            return PreAuthAccessScope.providers(Set.of(user.getProviderId()));
        }
        if (user.getEmployerId() != null) {
            return PreAuthAccessScope.employers(Set.of(user.getEmployerId()));
        }
        if (reviewerIsolationService.isSubjectToIsolation(user)) {
            return PreAuthAccessScope.providers(new LinkedHashSet<>(
                    reviewerIsolationService.getAllowedProviderIds(user)));
        }
        return PreAuthAccessScope.global();
    }
}
