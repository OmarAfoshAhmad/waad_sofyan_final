package com.waad.tba.modules.providercontract.controller;

import com.waad.tba.modules.providercontract.service.ProviderContractPricingItemService;
import com.waad.tba.modules.providercontract.service.ProviderContractService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.security.QueryFilterService;
import com.waad.tba.security.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for a CRITICAL IDOR found while closing the provider &
 * contracts module: /provider-contracts/provider/{providerId}/categories,
 * .../services and .../categories/{categoryId}/services accepted providerId
 * straight from the path with no ownership check, so any PROVIDER_STAFF user
 * could read a competitor's full contracted price list by walking providerId
 * 1..N. The controller now resolves the caller's own providerId via
 * AuthorizationService.resolveProviderScope before delegating.
 */
@ExtendWith(MockitoExtension.class)
class ProviderContractControllerSecurityTest {

    @Mock
    private ProviderContractService contractService;

    @Mock
    private ProviderContractPricingItemService pricingService;

    private AuthorizationService authorizationService;

    @Mock
    private com.waad.tba.modules.rbac.repository.UserRepository userRepository;

    @Mock
    private RoleService roleService;

    private ProviderContractController controller;

    private User providerStaffUser;

    @BeforeEach
    void setUp() {
        providerStaffUser = User.builder().id(1L).username("provider-a-staff")
                .userType("PROVIDER_STAFF").providerId(251L).build();

        QueryFilterService queryFilterService = new QueryFilterService(roleService);
        lenient().when(roleService.isProvider(providerStaffUser)).thenReturn(true);
        authorizationService = new AuthorizationService(userRepository, roleService,
                null, queryFilterService, null);

        controller = new ProviderContractController(contractService, pricingService, authorizationService);

        org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        providerStaffUser.getUsername(), null, java.util.List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        lenient().when(userRepository.findByUsername(providerStaffUser.getUsername()))
                .thenReturn(java.util.Optional.of(providerStaffUser));
    }

    @Test
    void getContractedCategoriesIgnoresForeignProviderIdAndUsesCallersOwnProvider() {
        Long foreignProviderId = 999L;

        controller.getContractedCategories(foreignProviderId);

        verify(pricingService).findCategoriesByProvider(eq(251L));
    }

    @Test
    void getContractedServicesByCategoryIgnoresForeignProviderId() {
        controller.getContractedServicesByCategory(999L, 5L);

        verify(pricingService).findServicesByProviderAndCategory(eq(251L), eq(5L));
    }

    @Test
    void getAllContractedServicesIgnoresForeignProviderId() {
        controller.getAllContractedServices(999L);

        verify(pricingService).findAllServicesByProvider(eq(251L));
    }
}
