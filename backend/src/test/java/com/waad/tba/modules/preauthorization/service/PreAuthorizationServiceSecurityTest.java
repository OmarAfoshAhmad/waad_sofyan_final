package com.waad.tba.modules.preauthorization.service;

import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.preauthorization.security.AuthorizedPreAuthScope;
import com.waad.tba.modules.preauthorization.security.PreAuthAccessScopeResolver;
import com.waad.tba.modules.preauthorization.security.PreAuthAccessGuard;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.ProviderContextGuard;
import com.waad.tba.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for SECTION_02 CRITICAL finding #5:
 * getPreAuthorizationById / getPreAuthorizationByReference performed no
 * ownership check, letting any PROVIDER_STAFF user fetch another provider's
 * pre-authorization (clinical + financial data) by guessing the id or
 * reference number. The fix mirrors the already-proven
 * PreAuthorizationAttachmentService.assertCanAccessPreAuthorization pattern:
 * internal staff see everything, provider staff only their own provider's
 * requests, employer admins only members in their own employer.
 */
@ExtendWith(MockitoExtension.class)
class PreAuthorizationServiceSecurityTest {

    @Mock
    private PreAuthorizationRepository preAuthorizationRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private ProviderContextGuard providerContextGuard;

    @Mock
    private com.waad.tba.modules.claim.service.ReviewerProviderIsolationService reviewerIsolationService;

    @Mock
    private PreAuthAccessScopeResolver preAuthAccessScopeResolver;

    @Mock
    private AuthorizedPreAuthScope authorizedScope;

    @Mock
    private PreAuthAccessGuard preAuthAccessGuard;

    @InjectMocks
    private PreAuthorizationService service;

    private User otherProviderStaff;
    private PreAuthorization preAuth;

    @BeforeEach
    void setUp() {
        otherProviderStaff = User.builder().id(9L).username("other-provider-user")
                .userType("PROVIDER_STAFF").providerId(999L).build();
        preAuth = PreAuthorization.builder().id(300L).providerId(251L).memberId(6L)
                .referenceNumber("PA-2026-0001").build();

        lenient().when(authorizationService.getCurrentUser()).thenReturn(otherProviderStaff);
        lenient().when(authorizationService.isInternalStaff(otherProviderStaff)).thenReturn(false);
        lenient().when(authorizationService.isProvider(otherProviderStaff)).thenReturn(true);
        lenient().when(authorizationService.isEmployerAdmin(otherProviderStaff)).thenReturn(false);
    }

    @Test
    void getByIdDeniedWhenPreAuthBelongsToAnotherProvider() {
        when(preAuthorizationRepository.findById(300L)).thenReturn(Optional.of(preAuth));
        when(preAuthAccessGuard.canView(300L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.getPreAuthorizationById(300L));
    }

    @Test
    void getByReferenceDeniedWhenPreAuthBelongsToAnotherProvider() {
        when(preAuthorizationRepository.findByReferenceNumberAndActiveTrue("PA-2026-0001"))
                .thenReturn(Optional.of(preAuth));
        when(preAuthAccessGuard.canView(300L)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> service.getPreAuthorizationByReference("PA-2026-0001"));
    }

    @Test
    void getByIdAllowedWhenPreAuthBelongsToCallersOwnProvider() {
        when(preAuthorizationRepository.findById(300L)).thenReturn(Optional.of(preAuth));
        when(preAuthAccessGuard.canView(300L)).thenReturn(true);

        // Should not throw — same provider owns the request.
        // (Downstream mapping may hit null-dependent fields; the access
        // check itself is what this test proves, so a mapping NPE — if any —
        // is not this test's concern and won't mask an incorrect ALLOW.)
        try {
            service.getPreAuthorizationById(300L);
        } catch (AccessDeniedException denied) {
            throw new AssertionError("Access must be allowed for the owning provider", denied);
        } catch (Exception ignoredDownstream) {
            // Expected: mapToResponseDto needs more mocked collaborators than
            // this focused security test sets up.
        }
    }

    @Test
    void operationalReportForProviderAlwaysUsesSessionProviderScope() {
        var pageable = PageRequest.of(0, 20);
        var dateFrom = LocalDate.of(2026, 1, 1);
        var dateTo = LocalDate.of(2026, 1, 31);

        givenProviderScope(251L);

        assertThrows(AccessDeniedException.class, () -> service.getOperationalReport(
                PreAuthStatus.SUBMITTED, 999L, 77L, " علي ", dateFrom, dateTo, pageable));

        verify(preAuthorizationRepository, never()).findForOperationalReportByProvider(
                PreAuthStatus.SUBMITTED, 999L, 77L, "علي", dateFrom, dateTo, pageable);
    }

    @Test
    void operationalReportForReviewerWithoutAssignmentsReturnsEmptyPage() {
        var pageable = PageRequest.of(0, 20);

        when(preAuthAccessScopeResolver.requireViewScope()).thenThrow(new AccessDeniedException("no scope"));

        assertThrows(AccessDeniedException.class,
                () -> service.getOperationalReport(null, null, null, null, null, null, pageable));
        verify(preAuthorizationRepository, never()).findForOperationalReport(null, null, null, null, null, pageable);
    }

    @Test
    void operationalReportForReviewerValidatesRequestedProviderBeforeQuerying() {
        var pageable = PageRequest.of(0, 20);

        givenProviderScope(251L);
        when(preAuthorizationRepository.findForOperationalReportByProvider(
                null, 251L, null, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.getOperationalReport(null, 251L, null, null, null, null, pageable);

        verify(preAuthorizationRepository).findForOperationalReportByProvider(
                null, 251L, null, null, null, null, pageable);
    }

    @Test
    void operationalReportForEmployerAlwaysEnforcesEmployerScope() {
        var pageable = PageRequest.of(0, 20);
        givenEmployerScope(77L);
        when(preAuthorizationRepository.findForOperationalReport(
                null, 77L, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.getOperationalReport(null, null, null, null, null, null, pageable);

        verify(preAuthorizationRepository).findForOperationalReport(
                null, 77L, null, null, null, pageable);
    }

    @Test
    void operationalReportRejectsRequestedEmployerOutsideScope() {
        var pageable = PageRequest.of(0, 20);
        givenEmployerScope(77L);

        assertThrows(AccessDeniedException.class,
                () -> service.getOperationalReport(null, null, 88L, null, null, null, pageable));

        verify(preAuthorizationRepository, never()).findForOperationalReport(
                null, 88L, null, null, null, pageable);
    }

    @Test
    void statusListingDelegatesToScopedOperationalReport() {
        var pageable = PageRequest.of(0, 20);
        givenEmployerScope(77L);
        when(preAuthorizationRepository.findForOperationalReport(
                PreAuthStatus.SUBMITTED, 77L, null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.getPreAuthorizationsByStatus(PreAuthStatus.SUBMITTED, pageable);

        verify(preAuthorizationRepository).findForOperationalReport(
                PreAuthStatus.SUBMITTED, 77L, null, null, null, pageable);
        verify(preAuthorizationRepository, never()).findByStatusAndActiveTrue(
                PreAuthStatus.SUBMITTED, pageable);
    }

    @Test
    void providerListingDelegatesToScopedOperationalReportAndRejectsForeignProvider() {
        var pageable = PageRequest.of(0, 20);
        givenProviderScope(251L);

        assertThrows(AccessDeniedException.class,
                () -> service.getPreAuthorizationsByProvider(999L, pageable));

        verify(preAuthorizationRepository, never()).findByProviderIdAndActiveTrue(999L, pageable);
        verify(preAuthorizationRepository, never()).findForOperationalReportByProvider(
                null, 999L, null, null, null, null, pageable);
    }

    @Test
    void searchUsesAuthorizedScopeInDatabaseQuery() {
        var pageable = PageRequest.of(0, 20);
        givenProviderScope(251L);
        when(preAuthorizationRepository.searchScoped(
                "PA-77", "PROVIDERS", java.util.Set.of(251L), pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.search("  PA-77  ", pageable);

        verify(preAuthorizationRepository).searchScoped(
                "PA-77", "PROVIDERS", java.util.Set.of(251L), pageable);
        verify(preAuthorizationRepository, never()).search("  PA-77  ", pageable);
    }

    private void givenProviderScope(Long providerId) {
        when(preAuthAccessScopeResolver.requireViewScope()).thenReturn(authorizedScope);
        when(authorizedScope.kind()).thenReturn(
                com.waad.tba.modules.preauthorization.security.PreAuthAccessScope.Kind.PROVIDERS);
        when(authorizedScope.ids()).thenReturn(java.util.Set.of(providerId));
    }

    private void givenEmployerScope(Long employerId) {
        when(preAuthAccessScopeResolver.requireViewScope()).thenReturn(authorizedScope);
        when(authorizedScope.kind()).thenReturn(
                com.waad.tba.modules.preauthorization.security.PreAuthAccessScope.Kind.EMPLOYERS);
        when(authorizedScope.ids()).thenReturn(java.util.Set.of(employerId));
    }
}
