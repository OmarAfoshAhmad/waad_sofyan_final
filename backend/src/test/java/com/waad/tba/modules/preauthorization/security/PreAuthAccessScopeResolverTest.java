package com.waad.tba.modules.preauthorization.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.permission.PermissionGuard;
import com.waad.tba.security.AuthorizationService;

class PreAuthAccessScopeResolverTest {
    private PermissionGuard permissionGuard;
    private AuthorizationService authorizationService;
    private ReviewerProviderIsolationService isolationService;
    private PreAuthAccessScopeResolver resolver;
    private User user;

    @BeforeEach
    void setUp() {
        permissionGuard = mock(PermissionGuard.class);
        authorizationService = mock(AuthorizationService.class);
        isolationService = mock(ReviewerProviderIsolationService.class);
        resolver = new PreAuthAccessScopeResolver(permissionGuard, authorizationService, isolationService);
        user = new User();
        when(authorizationService.getCurrentUser()).thenReturn(user);
    }

    @Test
    void missingViewCapabilityIsDeniedBeforeResolvingTheUser() {
        assertThat(resolver.resolve().kind()).isEqualTo(PreAuthAccessScope.Kind.DENIED);
        assertThatThrownBy(resolver::requireViewScope).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(authorizationService, isolationService);
    }

    @Test
    void providerIsRestrictedToItsOwnProvider() {
        allowView();
        user.setProviderId(41L);

        AuthorizedPreAuthScope scope = resolver.requireViewScope();

        assertThat(scope.kind()).isEqualTo(PreAuthAccessScope.Kind.PROVIDERS);
        assertThat(scope.ids()).containsExactly(41L);
    }

    @Test
    void employerIsRestrictedToItsOwnEmployer() {
        allowView();
        user.setEmployerId(71L);

        AuthorizedPreAuthScope scope = resolver.requireViewScope();

        assertThat(scope.kind()).isEqualTo(PreAuthAccessScope.Kind.EMPLOYERS);
        assertThat(scope.ids()).containsExactly(71L);
    }

    @Test
    void isolatedReviewerIsRestrictedToAssignedProviders() {
        allowView();
        when(isolationService.isSubjectToIsolation(user)).thenReturn(true);
        when(isolationService.getAllowedProviderIds(user)).thenReturn(List.of(42L, 41L));

        AuthorizedPreAuthScope scope = resolver.requireViewScope();

        assertThat(scope.kind()).isEqualTo(PreAuthAccessScope.Kind.PROVIDERS);
        assertThat(scope.ids()).containsExactlyInAnyOrder(41L, 42L);
    }

    @Test
    void isolatedReviewerWithoutAssignmentsIsDeniedInsteadOfReceivingGlobalData() {
        allowView();
        when(isolationService.isSubjectToIsolation(user)).thenReturn(true);
        when(isolationService.getAllowedProviderIds(user)).thenReturn(List.of());

        assertThat(resolver.resolve().kind()).isEqualTo(PreAuthAccessScope.Kind.DENIED);
        assertThatThrownBy(resolver::requireViewScope).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unlinkedInternalUserWithCapabilityReceivesExplicitGlobalScope() {
        allowView();

        AuthorizedPreAuthScope scope = resolver.requireViewScope();

        assertThat(scope.isGlobal()).isTrue();
        assertThat(scope.ids()).isEmpty();
    }

    private void allowView() {
        when(permissionGuard.has("PREAUTH_VIEW")).thenReturn(true);
    }
}
