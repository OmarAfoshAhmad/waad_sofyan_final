package com.waad.tba.modules.claim.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.permission.PermissionGuard;
import com.waad.tba.security.AuthorizationService;

class ClaimAccessGuardTest {
    private PermissionGuard permissionGuard;
    private AuthorizationService authorizationService;
    private ReviewerProviderIsolationService isolationService;
    private ClaimRepository claimRepository;
    private ClaimAccessGuard guard;
    private User user;

    @BeforeEach
    void setUp() {
        permissionGuard = mock(PermissionGuard.class);
        authorizationService = mock(AuthorizationService.class);
        isolationService = mock(ReviewerProviderIsolationService.class);
        claimRepository = mock(ClaimRepository.class);
        guard = new ClaimAccessGuard(permissionGuard, authorizationService, isolationService, claimRepository);
        user = new User();
        when(authorizationService.getCurrentUser()).thenReturn(user);
    }

    @Test
    void missingCapabilityFailsBeforeClaimLookup() {
        when(permissionGuard.has("CLAIM_VIEW")).thenReturn(false);
        assertThat(guard.canRead(7L)).isFalse();
        verifyNoInteractions(claimRepository);
    }

    @Test
    void providerCapabilityDoesNotBypassClaimOwnership() {
        when(permissionGuard.has("CLAIM_VIEW")).thenReturn(true);
        when(authorizationService.canAccessClaim(user, 7L)).thenReturn(false);
        assertThat(guard.canRead(7L)).isFalse();
    }

    @Test
    void reviewerMustBeAssignedToClaimsProvider() {
        when(permissionGuard.has("CLAIM_REVIEW")).thenReturn(true);
        when(isolationService.isSubjectToIsolation(user)).thenReturn(true);
        when(claimRepository.findById(8L)).thenReturn(Optional.of(Claim.builder().providerId(91L).build()));
        org.mockito.Mockito.doThrow(new AccessDeniedException("not assigned"))
                .when(isolationService).validateClaimAccess(user, 91L);
        assertThat(guard.canReview(8L)).isFalse();
    }

    @Test
    void assignedReviewerStillNeedsClaimAccessDecision() {
        when(permissionGuard.has("CLAIM_REVIEW")).thenReturn(true);
        when(isolationService.isSubjectToIsolation(user)).thenReturn(true);
        when(claimRepository.findById(8L)).thenReturn(Optional.of(Claim.builder().providerId(91L).build()));
        when(authorizationService.canAccessClaim(user, 8L)).thenReturn(true);
        assertThat(guard.canReview(8L)).isTrue();
    }

    @Test
    void createRequiresBothCapabilityAndVisitScope() {
        when(permissionGuard.has("CLAIM_CREATE")).thenReturn(true);
        when(authorizationService.canAccessVisit(user, 44L)).thenReturn(false);
        assertThat(guard.canCreateFromVisit(44L)).isFalse();
    }

    @Test
    void hardDeleteRequiresDangerZoneAndClaimScope() {
        when(permissionGuard.has("DANGER_ZONE_EXECUTE")).thenReturn(true);
        when(authorizationService.canAccessClaim(user, 12L)).thenReturn(true);
        assertThat(guard.canHardDelete(12L)).isTrue();
    }
}
