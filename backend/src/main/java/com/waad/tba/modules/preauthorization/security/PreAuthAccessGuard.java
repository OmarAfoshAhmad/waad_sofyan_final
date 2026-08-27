package com.waad.tba.modules.preauthorization.security;

import org.springframework.stereotype.Component;

import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.rbac.permission.PermissionGuard;
import com.waad.tba.security.AuthorizationService;

import lombok.RequiredArgsConstructor;

/** Combines effective pre-authorization capabilities with record ownership. */
@Component("preAuthAccessGuard")
@RequiredArgsConstructor
public class PreAuthAccessGuard {
    private final PermissionGuard permissionGuard;
    private final AuthorizationService authorizationService;
    private final ReviewerProviderIsolationService reviewerIsolationService;
    private final PreAuthorizationRepository preAuthorizationRepository;

    public boolean canReview(Long preAuthId) {
        return hasScopedAccess("PREAUTH_REVIEW", preAuthId);
    }

    public boolean canView(Long preAuthId) {
        return hasScopedAccess("PREAUTH_VIEW", preAuthId);
    }

    public boolean canApprove(Long preAuthId) {
        return hasScopedAccess("PREAUTH_APPROVE", preAuthId);
    }

    private boolean hasScopedAccess(String permission, Long preAuthId) {
        if (preAuthId == null || !permissionGuard.has(permission)) return false;
        var user = authorizationService.getCurrentUser();
        if (user == null) return false;

        return preAuthorizationRepository.findById(preAuthId)
                .map(preAuth -> {
                    Long providerId = preAuth.getProviderId();
                    if (user.getProviderId() != null) {
                        return user.getProviderId().equals(providerId);
                    }
                    if (user.getEmployerId() != null) {
                        return authorizationService.canAccessMember(user, preAuth.getMemberId());
                    }
                    if (reviewerIsolationService.isSubjectToIsolation(user)) {
                        return providerId != null
                                && reviewerIsolationService.getAllowedProviderIds(user).contains(providerId);
                    }
                    return true;
                })
                .orElse(false);
    }
}
