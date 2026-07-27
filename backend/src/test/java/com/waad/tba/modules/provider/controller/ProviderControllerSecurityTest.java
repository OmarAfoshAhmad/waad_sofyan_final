package com.waad.tba.modules.provider.controller;

import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.modules.provider.dto.ProviderViewDto;
import com.waad.tba.modules.provider.service.ProviderAdminDocumentService;
import com.waad.tba.modules.provider.service.ProviderContractService;
import com.waad.tba.modules.provider.service.ProviderService;
import com.waad.tba.modules.provider.service.ProviderServiceService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for a HIGH IDOR found while closing the provider &
 * contracts module: GET /providers/{id} let any PROVIDER_STAFF user read
 * another provider's full record (contacts, license/tax numbers) since
 * getProvider() had no ownership check, unlike sibling endpoints.
 */
@ExtendWith(MockitoExtension.class)
class ProviderControllerSecurityTest {

    @Mock
    private ProviderService providerService;
    @Mock
    private ProviderServiceService providerServiceService;
    @Mock
    private ProviderContractService providerContractService;
    @Mock
    private ProviderAdminDocumentService providerAdminDocumentService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private ReviewerProviderIsolationService reviewerIsolationService;

    private ProviderController controller;
    private User providerStaffUser;

    @BeforeEach
    void setUp() {
        providerStaffUser = User.builder().id(1L).username("provider-a-staff")
                .userType("PROVIDER_STAFF").providerId(251L).build();
        controller = new ProviderController(providerService, providerServiceService, providerContractService,
                providerAdminDocumentService, authorizationService, reviewerIsolationService);
        lenient().when(authorizationService.getCurrentUser()).thenReturn(providerStaffUser);
        lenient().when(authorizationService.isProvider(providerStaffUser)).thenReturn(true);
    }

    @Test
    void getProviderDeniedForForeignProvider() {
        when(authorizationService.canAccessProvider(providerStaffUser, 999L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> controller.getProvider(999L));

        verify(providerService, never()).getProvider(999L);
    }

    @Test
    void getProviderAllowedForOwnProvider() {
        when(authorizationService.canAccessProvider(providerStaffUser, 251L)).thenReturn(true);
        when(providerService.getProvider(251L)).thenReturn(ProviderViewDto.builder().id(251L).build());

        controller.getProvider(251L);

        verify(providerService).getProvider(251L);
    }
}
