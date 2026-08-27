package com.waad.tba.modules.providercontract.security;

import org.springframework.stereotype.Component;

import com.waad.tba.modules.providercontract.repository.ProviderContractPricingItemRepository;
import com.waad.tba.modules.providercontract.repository.ProviderContractRepository;
import com.waad.tba.modules.rbac.permission.PermissionGuard;
import com.waad.tba.security.AuthorizationService;

import lombok.RequiredArgsConstructor;

/** Resource-aware method-security bridge for negotiated provider contracts. */
@Component("providerContractAccessGuard")
@RequiredArgsConstructor
public class ProviderContractAccessGuard {
    private final PermissionGuard permissionGuard;
    private final AuthorizationService authorizationService;
    private final ProviderContractRepository contractRepository;
    private final ProviderContractPricingItemRepository pricingItemRepository;

    public boolean canReadGlobal() {
        if (!permissionGuard.has("CONTRACT_VIEW")) return false;
        var user = authorizationService.getCurrentUser();
        return user != null && !authorizationService.isProvider(user);
    }

    public boolean canReadProvider(Long providerId) {
        if (providerId == null || !permissionGuard.has("CONTRACT_VIEW")) return false;
        return canAccessProvider(providerId);
    }

    public boolean canReadContract(Long contractId) {
        if (contractId == null || !permissionGuard.has("CONTRACT_VIEW")) return false;
        return contractRepository.findById(contractId)
                .map(contract -> canAccessProvider(contract.getProvider().getId()))
                .orElse(false);
    }

    public boolean canReadContractCode(String contractCode) {
        if (contractCode == null || contractCode.isBlank() || !permissionGuard.has("CONTRACT_VIEW")) return false;
        return contractRepository.findByContractCodeAndActiveTrue(contractCode)
                .map(contract -> canAccessProvider(contract.getProvider().getId()))
                .orElse(false);
    }

    public boolean canReadPricingItem(Long pricingItemId) {
        if (pricingItemId == null || !permissionGuard.has("CONTRACT_VIEW")) return false;
        return pricingItemRepository.findById(pricingItemId)
                .map(item -> canAccessProvider(item.getContract().getProvider().getId()))
                .orElse(false);
    }

    private boolean canAccessProvider(Long providerId) {
        var user = authorizationService.getCurrentUser();
        return user != null && authorizationService.canAccessProvider(user, providerId);
    }
}
