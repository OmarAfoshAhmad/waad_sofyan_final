package com.waad.tba.modules.providercontract.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.providercontract.entity.ProviderContract;
import com.waad.tba.modules.providercontract.entity.ProviderContractPricingItem;
import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.permission.PermissionGuard;
import com.waad.tba.security.AuthorizationService;

class ProviderContractAccessGuardTest {
    private PermissionGuard permissionGuard;
    private AuthorizationService authorizationService;
    private ProviderContractRepository contractRepository;
    private ProviderContractPricingItemRepository pricingItemRepository;
    private ProviderContractAccessGuard guard;
    private User user;

    @BeforeEach
    void setUp() {
        permissionGuard = mock(PermissionGuard.class);
        authorizationService = mock(AuthorizationService.class);
        contractRepository = mock(ProviderContractRepository.class);
        pricingItemRepository = mock(ProviderContractPricingItemRepository.class);
        guard = new ProviderContractAccessGuard(permissionGuard, authorizationService,
                contractRepository, pricingItemRepository);
        user = new User();
        when(authorizationService.getCurrentUser()).thenReturn(user);
    }

    @Test
    void missingCapabilityFailsBeforeLookingUpContract() {
        when(permissionGuard.has("CONTRACT_VIEW")).thenReturn(false);
        assertThat(guard.canReadContract(7L)).isFalse();
        verifyNoInteractions(contractRepository);
    }

    @Test
    void providerUserCannotUseGlobalContractEndpoints() {
        when(permissionGuard.has("CONTRACT_VIEW")).thenReturn(true);
        when(authorizationService.isProvider(user)).thenReturn(true);
        assertThat(guard.canReadGlobal()).isFalse();
    }

    @Test
    void internalUserWithCapabilityCanUseGlobalContractEndpoints() {
        when(permissionGuard.has("CONTRACT_VIEW")).thenReturn(true);
        when(authorizationService.isProvider(user)).thenReturn(false);
        assertThat(guard.canReadGlobal()).isTrue();
    }

    @Test
    void contractAccessUsesOwningProviderScope() {
        when(permissionGuard.has("CONTRACT_VIEW")).thenReturn(true);
        Provider provider = Provider.builder().id(31L).build();
        ProviderContract contract = ProviderContract.builder().provider(provider).build();
        when(contractRepository.findById(9L)).thenReturn(Optional.of(contract));
        when(authorizationService.canAccessProvider(user, 31L)).thenReturn(false);
        assertThat(guard.canReadContract(9L)).isFalse();
    }

    @Test
    void providerScopedReadAllowsOnlyAuthorizedProvider() {
        when(permissionGuard.has("CONTRACT_VIEW")).thenReturn(true);
        when(authorizationService.canAccessProvider(user, 44L)).thenReturn(true);
        assertThat(guard.canReadProvider(44L)).isTrue();
    }

    @Test
    void pricingItemAccessTraversesContractProvider() {
        when(permissionGuard.has("CONTRACT_VIEW")).thenReturn(true);
        Provider provider = Provider.builder().id(51L).build();
        ProviderContract contract = ProviderContract.builder().provider(provider).build();
        ProviderContractPricingItem item = ProviderContractPricingItem.builder().contract(contract).build();
        when(pricingItemRepository.findById(12L)).thenReturn(Optional.of(item));
        when(authorizationService.canAccessProvider(user, 51L)).thenReturn(true);
        assertThat(guard.canReadPricingItem(12L)).isTrue();
    }
}
