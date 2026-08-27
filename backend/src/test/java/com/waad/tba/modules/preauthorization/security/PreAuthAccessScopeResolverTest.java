package com.waad.tba.modules.preauthorization.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;

/**
 * S-04. The pre-authorization module had no scope resolver at all, so a role
 * check was the only thing standing between a caller and every provider's
 * requests. These cases pin the rule that matters most: an unestablished scope
 * denies rather than widening.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PreAuthAccessScopeResolverTest {

    @Mock private AuthorizationService authorizationService;
    @InjectMocks private PreAuthAccessScopeResolver resolver;

    private User user(Long employerId, Long providerId) {
        return User.builder()
                .id(1L).username("u").userType("X").active(true)
                .employerId(employerId).providerId(providerId)
                .build();
    }

    @Test
    void noAuthenticatedUserIsDenied() {
        assertThat(resolver.resolveFor(null).isDenied()).isTrue();
    }

    @Test
    void inactiveAccountIsDenied() {
        User inactive = User.builder().id(1L).username("u").userType("SUPER_ADMIN").active(false).build();
        assertThat(resolver.resolveFor(inactive).isDenied()).isTrue();
    }

    @Test
    void superAdminReachesEverything() {
        User u = user(null, null);
        org.mockito.Mockito.when(authorizationService.isSuperAdmin(u)).thenReturn(true);
        assertThat(resolver.resolveFor(u).isGlobal()).isTrue();
    }

    @Test
    void reviewerReachesEveryProviderBecauseThatIsTheWork() {
        User u = user(null, null);
        org.mockito.Mockito.when(authorizationService.isReviewer(u)).thenReturn(true);
        assertThat(resolver.resolveFor(u).isGlobal()).isTrue();
    }

    @Test
    void providerStaffIsBoundedToItsOwnProvider() {
        User u = user(null, 7L);
        org.mockito.Mockito.when(authorizationService.isProvider(u)).thenReturn(true);

        PreAuthAccessScope scope = resolver.resolveFor(u);

        assertThat(scope.covers(7L, null)).isTrue();
        assertThat(scope.covers(8L, null)).as("another provider's request is out of reach").isFalse();
        assertThat(scope.singleProviderId()).contains(7L);
    }

    @Test
    void providerStaffWithoutAProviderIsDeniedNotGlobal() {
        User u = user(null, null);
        org.mockito.Mockito.when(authorizationService.isProvider(u)).thenReturn(true);

        PreAuthAccessScope scope = resolver.resolveFor(u);

        assertThat(scope.isDenied()).isTrue();
        assertThat(scope.isGlobal()).isFalse();
    }

    @Test
    void employerAdminIsBoundedToItsOwnEmployer() {
        User u = user(3L, null);
        org.mockito.Mockito.when(authorizationService.isEmployerAdmin(u)).thenReturn(true);

        PreAuthAccessScope scope = resolver.resolveFor(u);

        assertThat(scope.covers(null, 3L)).isTrue();
        assertThat(scope.covers(null, 4L)).isFalse();
        assertThat(scope.singleProviderId()).as("an employer admin writes as no provider").isEmpty();
    }

    @Test
    void employerAdminWithoutAnEmployerIsDenied() {
        User u = user(null, null);
        org.mockito.Mockito.when(authorizationService.isEmployerAdmin(u)).thenReturn(true);
        assertThat(resolver.resolveFor(u).isDenied()).isTrue();
    }

    @Test
    void unrecognisedRoleWithoutAnyScopeIsDenied() {
        User u = user(null, null);
        PreAuthAccessScope scope = resolver.resolveFor(u);

        assertThat(scope.isDenied()).isTrue();
        assertThat(scope.reason()).contains("نطاق");
    }

    @Test
    void aRequestWithNoProviderIsNobodysRatherThanEveryonesUnderAProviderScope() {
        User u = user(null, 7L);
        org.mockito.Mockito.when(authorizationService.isProvider(u)).thenReturn(true);

        assertThat(resolver.resolveFor(u).covers(null, null))
                .as("a null provider must not slip through a provider-bounded scope")
                .isFalse();
    }

    @Test
    void narrowingToAForeignProviderRefusesRatherThanReturningNothing() {
        User u = user(null, 7L);
        org.mockito.Mockito.when(authorizationService.isProvider(u)).thenReturn(true);

        PreAuthAccessScope scope = resolver.resolveFor(u, 8L);

        assertThat(scope.isDenied())
                .as("refusal, not an empty result that reads as 'no requests exist'")
                .isTrue();
    }

    @Test
    void administratorMayNarrowToAnyProviderExplicitly() {
        User u = user(null, null);
        org.mockito.Mockito.when(authorizationService.isSuperAdmin(u)).thenReturn(true);

        PreAuthAccessScope scope = resolver.resolveFor(u, 8L);

        assertThat(scope.isDenied()).isFalse();
        assertThat(scope.covers(8L, null)).isTrue();
        assertThat(scope.covers(9L, null)).as("narrowed, not still global").isFalse();
    }
}
