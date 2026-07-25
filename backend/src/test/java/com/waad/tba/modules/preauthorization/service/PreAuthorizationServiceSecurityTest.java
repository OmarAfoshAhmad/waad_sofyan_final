package com.waad.tba.modules.preauthorization.service;

import com.waad.tba.modules.preauthorization.entity.PreAuthorization;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
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

        assertThrows(AccessDeniedException.class, () -> service.getPreAuthorizationById(300L));
    }

    @Test
    void getByReferenceDeniedWhenPreAuthBelongsToAnotherProvider() {
        when(preAuthorizationRepository.findByReferenceNumberAndActiveTrue("PA-2026-0001"))
                .thenReturn(Optional.of(preAuth));

        assertThrows(AccessDeniedException.class,
                () -> service.getPreAuthorizationByReference("PA-2026-0001"));
    }

    @Test
    void getByIdAllowedWhenPreAuthBelongsToCallersOwnProvider() {
        User ownProviderStaff = User.builder().id(10L).username("owning-provider-user")
                .userType("PROVIDER_STAFF").providerId(251L).build();
        when(authorizationService.getCurrentUser()).thenReturn(ownProviderStaff);
        when(authorizationService.isInternalStaff(ownProviderStaff)).thenReturn(false);
        when(authorizationService.isProvider(ownProviderStaff)).thenReturn(true);
        when(preAuthorizationRepository.findById(300L)).thenReturn(Optional.of(preAuth));

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
}
