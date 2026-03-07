package com.waad.tba.modules.claim.service;

import com.waad.tba.modules.claim.dto.ReviewerProviderOptionDto;
import com.waad.tba.modules.claim.repository.MedicalReviewerProviderRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Reviewer-Provider Isolation Service.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * MEDICAL REVIEWER ISOLATION ENFORCEMENT
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * This service provides the core isolation logic for medical reviewers.
 * It determines:
 * 1. Whether a user is subject to reviewer isolation
 * 2. Which providers a reviewer has access to
 * 3. Whether a reviewer can access a specific claim
 * 
 * Bypass Rules:
 * - SUPER_ADMIN: Full access to all claims (bypass isolation)
 * - ADMIN: Full access to all claims (bypass isolation)
 * - MEDICAL_REVIEWER: Subject to isolation (sees only assigned providers)
 * - Other roles: Not affected by reviewer isolation
 * 
 * Usage Pattern:
 * ```java
 * // In ClaimService - List claims
 * if (isolationService.isSubjectToIsolation(currentUser)) {
 * List<Long> allowedProviders =
 * isolationService.getAllowedProviderIds(currentUser);
 * return claimRepository.findByProviderIdIn(allowedProviders);
 * } else {
 * return claimRepository.findAll();
 * }
 * 
 * // In ClaimService - Approve claim
 * isolationService.validateReviewerAccess(currentUser, claim.getProviderId());
 * // Proceed with approval...
 * ```
 * 
 * @since Medical Reviewer Isolation Phase (2026-02-12)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewerProviderIsolationService {

    private final MedicalReviewerProviderRepository medicalReviewerProviderRepository;
    private final AuthorizationService authorizationService;

    // Role names for bypass logic
    private static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_MEDICAL_REVIEWER = "MEDICAL_REVIEWER";

    /**
     * Check if current user is subject to reviewer isolation.
     * 
     * Only MEDICAL_REVIEWER role is subject to isolation.
     * SUPER_ADMIN and ADMIN bypass isolation (see all claims).
     * 
     * @param user The user to check
     * @return true if user is a medical reviewer (subject to isolation)
     */
    public boolean isSubjectToIsolation(User user) {
        if (user == null || user.getUserType() == null) {
            return false;
        }

        String userRole = user.getUserType();

        // Admins bypass isolation
        if (ROLE_SUPER_ADMIN.equals(userRole) || ROLE_ADMIN.equals(userRole)) {
            log.debug("User {} has admin role - bypassing reviewer isolation", user.getId());
            return false;
        }

        // Medical reviewers are subject to isolation
        boolean isReviewer = ROLE_MEDICAL_REVIEWER.equals(userRole);
        if (isReviewer) {
            log.debug("User {} is medical reviewer - subject to isolation", user.getId());
        }

        return isReviewer;
    }

    /**
     * Get list of provider IDs that a reviewer has access to.
     * 
     * CRITICAL: This is the core isolation enforcement method.
     * Returns empty list if reviewer has no assignments (can't see any claims).
     * 
     * @param user The medical reviewer user
     * @return List of provider IDs this reviewer can access
     * @throws IllegalStateException if user is not a medical reviewer
     */
    public List<Long> getAllowedProviderIds(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        if (!isSubjectToIsolation(user)) {
            throw new IllegalStateException(
                    "User " + user.getId() + " is not subject to reviewer isolation. " +
                            "This method should only be called for MEDICAL_REVIEWER role.");
        }

        List<Long> providerIds = medicalReviewerProviderRepository
                .findProviderIdsByReviewerId(user.getId());

        log.info("Reviewer {} has access to {} providers: {}",
                user.getId(), providerIds.size(), providerIds);

        return providerIds;
    }

    /**
     * Validate that a reviewer has access to a specific provider.
     * 
     * DEFENSIVE VALIDATION: Use this in service methods to prevent unauthorized
     * access.
     * 
     * Example usage:
     * ```java
     * public void approveClaim(Long claimId) {
     * Claim claim = claimRepository.findById(claimId);
     * User currentUser = authorizationService.getCurrentUser();
     * 
     * // Defensive validation
     * isolationService.validateReviewerAccess(currentUser, claim.getProviderId());
     * 
     * // Proceed with approval...
     * }
     * ```
     * 
     * @param user       The user attempting access
     * @param providerId The provider ID of the claim
     * @throws AccessDeniedException if reviewer doesn't have access to this
     *                               provider
     */
    public void validateReviewerAccess(User user, Long providerId) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        if (providerId == null) {
            throw new IllegalArgumentException("Provider ID cannot be null");
        }

        // Admin roles bypass validation
        if (!isSubjectToIsolation(user)) {
            log.debug("User {} bypasses reviewer isolation validation", user.getId());
            return;
        }

        // Check if reviewer has access to this provider
        boolean hasAccess = medicalReviewerProviderRepository
                .existsByReviewerIdAndProviderIdAndActiveTrue(user.getId(), providerId);

        if (!hasAccess) {
            log.warn("⚠️ ISOLATION VIOLATION: Reviewer {} attempted to access provider {} (NOT ASSIGNED)",
                    user.getId(), providerId);
            throw new AccessDeniedException(
                    String.format("Medical reviewer %d does not have access to provider %d. " +
                            "Reviewers can only access claims from assigned providers.",
                            user.getId(), providerId));
        }

        log.debug("✅ Reviewer {} has valid access to provider {}", user.getId(), providerId);
    }

    /**
     * Validate that a reviewer has access to a claim.
     * 
     * Convenience method that extracts providerId from claim.
     * 
     * @param user       The user attempting access
     * @param providerId The provider ID from the claim
     * @throws AccessDeniedException if reviewer doesn't have access
     */
    public void validateClaimAccess(User user, Long providerId) {
        validateReviewerAccess(user, providerId);
    }

    /**
     * Check if a reviewer has any provider assignments.
     * 
     * Used to determine if a reviewer can see any claims at all.
     * 
     * @param userId The reviewer user ID
     * @return true if reviewer has at least one active assignment
     */
    public boolean hasAnyProviderAssignments(Long userId) {
        long count = medicalReviewerProviderRepository.countByReviewerIdAndActiveTrue(userId);
        return count > 0;
    }

    /**
     * Get current user and check if subject to isolation.
     * 
     * Convenience method combining getCurrentUser() and isSubjectToIsolation().
     * 
     * @return true if current user is a medical reviewer
     */
    public boolean isCurrentUserSubjectToIsolation() {
        User currentUser = authorizationService.getCurrentUser();
        return isSubjectToIsolation(currentUser);
    }

    /**
     * Get allowed provider IDs for current user.
     * 
     * Convenience method for current user context.
     * 
     * @return List of provider IDs current reviewer can access
     * @throws IllegalStateException if current user is not a medical reviewer
     */
    public List<Long> getAllowedProviderIdsForCurrentUser() {
        User currentUser = authorizationService.getCurrentUser();
        return getAllowedProviderIds(currentUser);
    }

    /**
     * Get assigned providers for reviewer with id and display name.
     *
     * @param user reviewer user
     * @return provider options for UI selection
     */
    public List<ReviewerProviderOptionDto> getAssignedProviders(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        if (!isSubjectToIsolation(user)) {
            throw new IllegalStateException(
                    "User " + user.getId() + " is not subject to reviewer isolation. " +
                            "This method should only be called for MEDICAL_REVIEWER role.");
        }

        return medicalReviewerProviderRepository.findByReviewerIdAndActiveTrue(user.getId())
                .stream()
                .map(mapping -> new ReviewerProviderOptionDto(
                        mapping.getProvider().getId(),
                        mapping.getProvider().getName()))
                .collect(Collectors.toList());
    }

    /**
     * Get assigned providers for current reviewer user.
     *
     * @return provider options for current user
     */
    public List<ReviewerProviderOptionDto> getAssignedProvidersForCurrentUser() {
        User currentUser = authorizationService.getCurrentUser();
        return getAssignedProviders(currentUser);
    }
}
