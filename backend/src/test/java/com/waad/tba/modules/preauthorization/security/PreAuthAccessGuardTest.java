package com.waad.tba.modules.preauthorization.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.permission.PermissionGuard;
import com.waad.tba.security.AuthorizationService;

class PreAuthAccessGuardTest {
    private PermissionGuard permissionGuard;
    private AuthorizationService authorizationService;
    private ReviewerProviderIsolationService isolationService;
    private PreAuthorizationRepository repository;
    private PreAuthAccessGuard guard;
    private User user;

    @BeforeEach
    void setUp() {
        permissionGuard = mock(PermissionGuard.class);
        authorizationService = mock(AuthorizationService.class);
        isolationService = mock(ReviewerProviderIsolationService.class);
        repository = mock(PreAuthorizationRepository.class);
        guard = new PreAuthAccessGuard(permissionGuard, authorizationService, isolationService, repository);
        user = new User();
        when(authorizationService.getCurrentUser()).thenReturn(user);
    }

    @Test
    void missingCapabilityFailsBeforeRecordLookup() {
        assertThat(guard.canReview(7L)).isFalse();
        verifyNoInteractions(repository);
    }

    @Test
    void providerIsRestrictedToOwnPreauthorization() {
        user.setProviderId(41L);
        when(permissionGuard.has("PREAUTH_REVIEW")).thenReturn(true);
        PreAuthorization preAuth = preAuth(42L, 5L);
        when(repository.findById(7L)).thenReturn(Optional.of(preAuth));

        assertThat(guard.canReview(7L)).isFalse();
    }

    @Test
    void viewCapabilityUsesTheSameRecordOwnershipBoundary() {
        user.setProviderId(42L);
        when(permissionGuard.has("PREAUTH_VIEW")).thenReturn(true);
        PreAuthorization owned = preAuth(42L, 5L);
        when(repository.findById(7L)).thenReturn(Optional.of(owned));

        assertThat(guard.canView(7L)).isTrue();

        PreAuthorization foreign = preAuth(41L, 5L);
        when(repository.findById(7L)).thenReturn(Optional.of(foreign));
        assertThat(guard.canView(7L)).isFalse();
    }

    @Test
    void referenceLookupUsesViewCapabilityAndTheSameOwnershipBoundary() {
        user.setProviderId(42L);
        when(permissionGuard.has("PREAUTH_VIEW")).thenReturn(true);
        PreAuthorization owned = preAuth(42L, 5L);
        when(repository.findByReferenceNumberAndActiveTrue("PA-77"))
                .thenReturn(Optional.of(owned));

        assertThat(guard.canViewReference("PA-77")).isTrue();

        PreAuthorization foreign = preAuth(41L, 5L);
        when(repository.findByReferenceNumberAndActiveTrue("PA-77"))
                .thenReturn(Optional.of(foreign));
        assertThat(guard.canViewReference("PA-77")).isFalse();
    }

    @Test
    void employerScopeIsResolvedThroughTheMember() {
        user.setEmployerId(71L);
        when(permissionGuard.has("PREAUTH_REVIEW")).thenReturn(true);
        PreAuthorization preAuth = preAuth(42L, 5L);
        when(repository.findById(7L)).thenReturn(Optional.of(preAuth));
        when(authorizationService.canAccessMember(user, 5L)).thenReturn(true);

        assertThat(guard.canReview(7L)).isTrue();
    }

    @Test
    void isolatedReviewerOnlyAccessesAssignedProviders() {
        when(permissionGuard.has("PREAUTH_APPROVE")).thenReturn(true);
        PreAuthorization preAuth = preAuth(42L, 5L);
        when(repository.findById(7L)).thenReturn(Optional.of(preAuth));
        when(isolationService.isSubjectToIsolation(user)).thenReturn(true);
        when(isolationService.getAllowedProviderIds(user)).thenReturn(List.of(41L));

        assertThat(guard.canApprove(7L)).isFalse();

        when(isolationService.getAllowedProviderIds(user)).thenReturn(List.of(42L));
        assertThat(guard.canApprove(7L)).isTrue();
    }

    @Test
    void cancelRequiresItsOwnCapabilityAndRecordScope() {
        user.setEmployerId(71L);
        PreAuthorization preAuth = preAuth(42L, 5L);
        when(repository.findById(7L)).thenReturn(Optional.of(preAuth));
        when(authorizationService.canAccessMember(user, 5L)).thenReturn(true);

        assertThat(guard.canCancel(7L)).isFalse();

        when(permissionGuard.has("PREAUTH_CANCEL")).thenReturn(true);
        assertThat(guard.canCancel(7L)).isTrue();
    }

    @Test
    void deleteCapabilityDoesNotBypassProviderOwnership() {
        user.setProviderId(41L);
        when(permissionGuard.has("PREAUTH_DELETE")).thenReturn(true);
        PreAuthorization foreign = preAuth(42L, 5L);
        when(repository.findById(7L)).thenReturn(Optional.of(foreign));

        assertThat(guard.canDelete(7L)).isFalse();

        PreAuthorization owned = preAuth(41L, 5L);
        when(repository.findById(7L)).thenReturn(Optional.of(owned));
        assertThat(guard.canDelete(7L)).isTrue();
    }

    private PreAuthorization preAuth(Long providerId, Long memberId) {
        PreAuthorization preAuth = mock(PreAuthorization.class);
        when(preAuth.getProviderId()).thenReturn(providerId);
        when(preAuth.getMemberId()).thenReturn(memberId);
        return preAuth;
    }
}
