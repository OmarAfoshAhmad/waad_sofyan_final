package com.waad.tba.modules.claim.security;

import org.springframework.stereotype.Component;

import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.modules.rbac.permission.PermissionGuard;
import com.waad.tba.security.AuthorizationService;

import lombok.RequiredArgsConstructor;

/** Combines effective capabilities with claim, visit, and reviewer-provider scope. */
@Component("claimAccessGuard")
@RequiredArgsConstructor
public class ClaimAccessGuard {
    private final PermissionGuard permissionGuard;
    private final AuthorizationService authorizationService;
    private final ReviewerProviderIsolationService reviewerIsolationService;
    private final ClaimRepository claimRepository;

    public boolean canCreateFromVisit(Long visitId) {
        if (visitId == null || !permissionGuard.has("CLAIM_CREATE")) return false;
        var user = authorizationService.getCurrentUser();
        return user != null && authorizationService.canAccessVisit(user, visitId);
    }

    public boolean canRead(Long claimId) {
        return hasScopedClaimAccess("CLAIM_VIEW", claimId);
    }

    public boolean canEdit(Long claimId) {
        if (claimId == null || !permissionGuard.has("CLAIM_CREATE")) return false;
        var user = authorizationService.getCurrentUser();
        if (user == null || !hasReviewerScope(user, claimId)) return false;
        return authorizationService.canModifyClaim(user, claimId);
    }

    public boolean canReview(Long claimId) {
        return hasScopedClaimAccess("CLAIM_REVIEW", claimId);
    }

    public boolean canApprove(Long claimId) {
        return hasScopedClaimAccess("CLAIM_APPROVE", claimId);
    }

    public boolean canReverse(Long claimId) {
        return hasScopedClaimAccess("CLAIM_REVERSE", claimId);
    }

    public boolean canHardDelete(Long claimId) {
        return hasScopedClaimAccess("DANGER_ZONE_EXECUTE", claimId);
    }

    public boolean canReadVisit(Long visitId) {
        if (visitId == null || !permissionGuard.has("CLAIM_VIEW")) return false;
        var user = authorizationService.getCurrentUser();
        return user != null && authorizationService.canAccessVisit(user, visitId);
    }

    private boolean hasScopedClaimAccess(String permission, Long claimId) {
        if (claimId == null || !permissionGuard.has(permission)) return false;
        var user = authorizationService.getCurrentUser();
        return user != null
                && hasReviewerScope(user, claimId)
                && authorizationService.canAccessClaim(user, claimId);
    }

    private boolean hasReviewerScope(com.waad.tba.modules.rbac.entity.User user, Long claimId) {
        if (!reviewerIsolationService.isSubjectToIsolation(user)) return true;
        return claimRepository.findById(claimId)
                .map(claim -> {
                    try {
                        reviewerIsolationService.validateClaimAccess(user, claim.getProviderId());
                        return true;
                    } catch (org.springframework.security.access.AccessDeniedException ex) {
                        return false;
                    }
                })
                .orElse(false);
    }
}
