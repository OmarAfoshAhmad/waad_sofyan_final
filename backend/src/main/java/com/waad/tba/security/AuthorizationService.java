package com.waad.tba.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Authorization Service - Facade
 *
 * Delegates to specialized authorization services:
 * - RoleService: role checking (SUPER_ADMIN, EMPLOYER_ADMIN, etc.)
 * - DataAccessService: data-level access control
 * - QueryFilterService: query filtering by employer/provider
 * - FeatureToggleService: employer-specific feature flags
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final DataAccessService dataAccessService;
    private final QueryFilterService queryFilterService;
    private final FeatureToggleService featureToggleService;

    // =============================================================================================
    // CORE UTILITY METHODS
    // =============================================================================================

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("⚠️ No authenticated user found in security context");
            return null;
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username).orElse(null);
    }

    public User requireCurrentUser() {
        User user = getCurrentUser();
        if (user == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return user;
    }

    public Long resolveEmployerScope(User user, Long requestedEmployerId) {
        return queryFilterService.resolveEmployerScope(user, requestedEmployerId);
    }

    public Long resolveProviderScope(User user, Long requestedProviderId) {
        return queryFilterService.resolveProviderScope(user, requestedProviderId);
    }

    // =============================================================================================
    // ROLE CHECK METHODS (RBAC)
    // =============================================================================================

    public boolean isSuperAdmin(User user) {
        return roleService.isSuperAdmin(user);
    }

    public boolean isEmployerAdmin(User user) {
        return roleService.isEmployerAdmin(user);
    }

    public boolean isProvider(User user) {
        return roleService.isProvider(user);
    }

    public boolean isReviewer(User user) {
        return roleService.isReviewer(user);
    }

    public boolean isDataEntry(User user) {
        return roleService.isDataEntry(user);
    }

    public boolean canAccessInternalOperations(User user) {
        return roleService.canAccessInternalOperations(user);
    }

    public boolean isFinancialUser(User user) {
        return roleService.isFinancialUser(user);
    }

    public boolean isInternalStaff(User user) {
        return roleService.isInternalStaff(user);
    }

    // =============================================================================================
    // DATA-LEVEL ACCESS CONTROL METHODS
    // =============================================================================================

    public boolean canAccessMember(User user, Long memberId) {
        return dataAccessService.canAccessMember(user, memberId);
    }

    public boolean canAccessClaim(User user, Long claimId) {
        return dataAccessService.canAccessClaim(user, claimId);
    }

    public boolean canAccessVisit(User user, Long visitId) {
        return dataAccessService.canAccessVisit(user, visitId);
    }

    public boolean canAccessProvider(User user, Long providerId) {
        return dataAccessService.canAccessProvider(user, providerId);
    }

    public boolean canAccessProvider(Long providerId) {
        User currentUser = getCurrentUser();
        return canAccessProvider(currentUser, providerId);
    }

    public boolean canModifyClaim(User user, Long claimId) {
        return dataAccessService.canModifyClaim(user, claimId);
    }

    // =============================================================================================
    // QUERY FILTERING METHODS (FOR SERVICE LAYER)
    // =============================================================================================

    public Long getEmployerFilterForUser(User user) {
        return queryFilterService.getEmployerFilterForUser(user);
    }

    public Long getProviderFilterForUser(User user) {
        return queryFilterService.getProviderFilterForUser(user);
    }

    // =============================================================================================
    // FEATURE TOGGLE METHODS (EMPLOYER-SPECIFIC PERMISSIONS)
    // =============================================================================================

    public boolean canEmployerViewMembers(User user) {
        return featureToggleService.canEmployerViewMembers(user);
    }

    public boolean canEmployerViewBenefitPolicies(User user) {
        return featureToggleService.canEmployerViewBenefitPolicies(user);
    }
}
