package com.waad.tba.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.waad.tba.modules.claim.entity.Claim;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.modules.visit.entity.Visit;
import com.waad.tba.modules.visit.repository.VisitRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ================================================================================================
 * TBA-WAAD Authorization Service - SIMPLIFIED SECURITY MODEL
 * ================================================================================================
 * 
 * CRITICAL BUSINESS RULES (DO NOT MODIFY):
 * 
 * 1. There is ONLY ONE insurance company in the system.
 * 2. Insurance companies are NOT a security boundary.
 * 3. Companies table is for SYSTEM-LEVEL settings only (branding, features).
 * 4. Employers are the ONLY data-level security boundary.
 * 
 * ================================================================================================
 * AUTHORIZATION MODEL (Static 7-Role System):
 * ================================================================================================
 * 
 * SUPER_ADMIN:
 *   - Bypasses ALL authorization checks immediately.
 *   - Can access ALL data without any restrictions.
 * 
 * ACCOUNTANT:
 *   - Full data access for financial operations.
 * 
 * EMPLOYER_ADMIN:
 *   - Restricted STRICTLY by their employerId.
 *   - Can ONLY access data belonging to their employer.
 * 
 * PROVIDER_STAFF:
 *   - Restricted by their providerId.
 * 
 * MEDICAL_REVIEWER:
 *   - Can access claims for review purposes.
 * 
 * DATA_ENTRY:
 *   - Basic data entry operations.
 * 
 * FINANCE_VIEWER:
 *   - Read-only financial access.
 * 
 * ================================================================================================
 * KEY PRINCIPLES:
 * ================================================================================================
 * 
 * 1. RBAC ≠ Data Filtering:
 *    - RBAC (permissions) decides WHAT modules a user can access.
 *    - Data filtering decides WHICH rows they can see.
 *    - These are two SEPARATE concerns.
 * 
 * 2. SUPER_ADMIN is GOD MODE:
 *    - Always returns TRUE for all checks.
 *    - Always returns NULL for filters (no filtering).
 * 
 * 3. EMPLOYER_ADMIN is the ONLY role with data-level restrictions:
 *    - Filter query: WHERE employer_id = user.employerId
 * 
 * 4. Company filtering has been REMOVED:
 *    - No more companyId checks.
 *    - No more insuranceCompanyId filtering.
 * 
 * ================================================================================================
 * @author TBA WAAD System
 * @version 2.0 - SIMPLIFIED MODEL
 * ================================================================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthorizationService {

    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final ClaimRepository claimRepository;
    private final VisitRepository visitRepository;

    // =============================================================================================
    // CORE UTILITY METHODS
    // =============================================================================================

    /**
     * Get the currently authenticated user from the security context.
     * 
     * @return Current authenticated User, or null if not authenticated
     */
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("⚠️ No authenticated user found in security context");
            return null;
        }

        String username = authentication.getName();
        return userRepository.findByUsername(username).orElse(null);
    }

    // =============================================================================================
    // ROLE CHECK METHODS (RBAC)
    // =============================================================================================

    /**
     * Check if user has SUPER_ADMIN role.
     * SUPER_ADMIN bypasses ALL authorization checks.
     * 
     * @param user User to check
     * @return true if user is SUPER_ADMIN
     */
    public boolean isSuperAdmin(User user) {
        if (user == null || user.getUserType() == null) {
            return false;
        }
        return "SUPER_ADMIN".equals(user.getUserType());
    }

    /**
     * Check if user has admin-level access (SUPER_ADMIN or ACCOUNTANT).
     * These roles have full data access.
     * 
     * @param user User to check
     * @return true if user has admin-level access
     */
    public boolean isInsuranceAdmin(User user) {
        if (user == null || user.getUserType() == null) {
            return false;
        }
        return "SUPER_ADMIN".equals(user.getUserType()) || "ACCOUNTANT".equals(user.getUserType());
    }

    /**
     * Check if user has EMPLOYER_ADMIN role.
     * EMPLOYER_ADMIN is restricted by their employerId.
     * 
     * @param user User to check
     * @return true if user is EMPLOYER_ADMIN
     */
    public boolean isEmployerAdmin(User user) {
        if (user == null || user.getUserType() == null) {
            return false;
        }
        return "EMPLOYER_ADMIN".equals(user.getUserType());
    }

    /**
     * Check if user has PROVIDER_STAFF role.
     * 
     * @param user User to check
     * @return true if user is PROVIDER_STAFF
     */
    public boolean isProvider(User user) {
        if (user == null || user.getUserType() == null) {
            return false;
        }
        return "PROVIDER_STAFF".equals(user.getUserType());
    }

    /**
     * Check if user has MEDICAL_REVIEWER role.
     * 
     * @param user User to check
     * @return true if user is MEDICAL_REVIEWER
     */
    public boolean isReviewer(User user) {
        if (user == null || user.getUserType() == null) {
            return false;
        }
        return "MEDICAL_REVIEWER".equals(user.getUserType());
    }

    /**
     * Check if user has DATA_ENTRY role.
     * 
     * @param user User to check
     * @return true if user is DATA_ENTRY
     */
    public boolean isDataEntry(User user) {
        if (user == null || user.getUserType() == null) {
            return false;
        }
        return "DATA_ENTRY".equals(user.getUserType());
    }

    /**
     * Check if user is internal staff (Admin, Reviewer, or Data Entry).
     * These users typically bypass provider-facing feature restrictions.
     */
    public boolean isInternalStaff(User user) {
        return isSuperAdmin(user) || isInsuranceAdmin(user) || isReviewer(user) || isDataEntry(user);
    }

    // =============================================================================================
    // DATA-LEVEL ACCESS CONTROL METHODS
    // =============================================================================================

    /**
     * Check if user can access a specific member.
     * 
     * AUTHORIZATION RULES:
     * - SUPER_ADMIN: ✅ Full access (always TRUE)
     * - INSURANCE_ADMIN: ✅ Full access (always TRUE)
     * - EMPLOYER_ADMIN: ✅ Only if member.employerId == user.employerId
     * - Others: ❌ No access
     * 
     * @param user Current user
     * @param memberId ID of the member to access
     * @return true if user can access the member
     */
    public boolean canAccessMember(User user, Long memberId) {
        if (user == null || memberId == null) {
            log.warn("❌ canAccessMember: DENIED - null user or memberId");
            return false;
        }

        // SUPER_ADMIN bypasses all checks
        if (isSuperAdmin(user)) {
            log.debug("✅ canAccessMember: ALLOWED - user={} is SUPER_ADMIN", user.getUsername());
            return true;
        }

        // INSURANCE_ADMIN has full access
        if (isInsuranceAdmin(user)) {
            log.debug("✅ canAccessMember: ALLOWED - user={} is INSURANCE_ADMIN", user.getUsername());
            return true;
        }

        Optional<Member> memberOpt = memberRepository.findById(memberId);
        if (memberOpt.isEmpty()) {
            log.warn("❌ canAccessMember: DENIED - member {} not found", memberId);
            return false;
        }

        Member member = memberOpt.get();

        // EMPLOYER_ADMIN: Check employer match
        if (isEmployerAdmin(user)) {
            if (user.getEmployerId() == null) {
                log.warn("❌ canAccessMember: DENIED - EMPLOYER_ADMIN user {} has no employerId", user.getUsername());
                return false;
            }
            if (member.getEmployer() == null || !user.getEmployerId().equals(member.getEmployer().getId())) {
                log.warn("❌ canAccessMember: DENIED - user {} attempted to access member {} from different employer", 
                        user.getUsername(), memberId);
                return false;
            }
            log.debug("✅ canAccessMember: ALLOWED - user={} employer matches", user.getUsername());
            return true;
        }

        log.warn("❌ canAccessMember: DENIED - user {} has no valid role for member access", user.getUsername());
        return false;
    }

    /**
     * Check if user can access a specific claim.
     * 
     * AUTHORIZATION RULES:
     * - SUPER_ADMIN: ✅ Full access (always TRUE)
     * - INSURANCE_ADMIN: ✅ Full access (always TRUE)
     * - REVIEWER: ✅ Full access for review purposes
     * - EMPLOYER_ADMIN: ✅ Only if claim.member.employerId == user.employerId
     * - PROVIDER: ✅ Can access claims (provider-specific logic TBD)
     * - Others: ❌ No access
     * 
     * @param user Current user
     * @param claimId ID of the claim to access
     * @return true if user can access the claim
     */
    public boolean canAccessClaim(User user, Long claimId) {
        if (user == null || claimId == null) {
            log.warn("❌ canAccessClaim: DENIED - null user or claimId");
            return false;
        }

        // SUPER_ADMIN bypasses all checks
        if (isSuperAdmin(user)) {
            log.debug("✅ canAccessClaim: ALLOWED - user={} is SUPER_ADMIN", user.getUsername());
            return true;
        }

        // INSURANCE_ADMIN has full access
        if (isInsuranceAdmin(user)) {
            log.debug("✅ canAccessClaim: ALLOWED - user={} is INSURANCE_ADMIN", user.getUsername());
            return true;
        }

        // REVIEWER can access all claims for review
        if (isReviewer(user)) {
            log.debug("✅ canAccessClaim: ALLOWED - user={} is REVIEWER", user.getUsername());
            return true;
        }

        Optional<Claim> claimOpt = claimRepository.findById(claimId);
        if (claimOpt.isEmpty()) {
            log.warn("❌ canAccessClaim: DENIED - claim {} not found", claimId);
            return false;
        }

        Claim claim = claimOpt.get();

        // PROVIDER: Can access claims (TODO: implement createdBy check)
        if (isProvider(user)) {
            log.debug("✅ canAccessClaim: ALLOWED - user={} is PROVIDER (TODO: add createdBy check)", user.getUsername());
            return true;
        }

        // EMPLOYER_ADMIN: Check if claim's member belongs to their employer
        if (isEmployerAdmin(user)) {
            if (user.getEmployerId() == null) {
                log.warn("❌ canAccessClaim: DENIED - EMPLOYER_ADMIN user {} has no employerId", user.getUsername());
                return false;
            }
            if (claim.getMember() == null || claim.getMember().getEmployer() == null ||
                !user.getEmployerId().equals(claim.getMember().getEmployer().getId())) {
                log.warn("❌ canAccessClaim: DENIED - user {} attempted to access claim {} from different employer", 
                        user.getUsername(), claimId);
                return false;
            }
            log.debug("✅ canAccessClaim: ALLOWED - user={} employer matches", user.getUsername());
            return true;
        }

        log.warn("❌ canAccessClaim: DENIED - user {} has no valid role for claim access", user.getUsername());
        return false;
    }

    /**
     * Check if user can access a specific visit.
     * 
     * AUTHORIZATION RULES:
     * - SUPER_ADMIN: ✅ Full access (always TRUE)
     * - INSURANCE_ADMIN: ✅ Full access (always TRUE)
     * - EMPLOYER_ADMIN: ✅ Only if visit.member.employerId == user.employerId
     * - Others: ❌ No access
     * 
     * @param user Current user
     * @param visitId ID of the visit to access
     * @return true if user can access the visit
     */
    public boolean canAccessVisit(User user, Long visitId) {
        if (user == null || visitId == null) {
            log.warn("❌ canAccessVisit: DENIED - null user or visitId");
            return false;
        }

        // SUPER_ADMIN bypasses all checks
        if (isSuperAdmin(user)) {
            log.debug("✅ canAccessVisit: ALLOWED - user={} is SUPER_ADMIN", user.getUsername());
            return true;
        }

        // INSURANCE_ADMIN has full access
        if (isInsuranceAdmin(user)) {
            log.debug("✅ canAccessVisit: ALLOWED - user={} is INSURANCE_ADMIN", user.getUsername());
            return true;
        }

        Optional<Visit> visitOpt = visitRepository.findById(visitId);
        if (visitOpt.isEmpty()) {
            log.warn("❌ canAccessVisit: DENIED - visit {} not found", visitId);
            return false;
        }

        Visit visit = visitOpt.get();

        // EMPLOYER_ADMIN: Check if visit's member belongs to their employer
        if (isEmployerAdmin(user)) {
            if (user.getEmployerId() == null) {
                log.warn("❌ canAccessVisit: DENIED - EMPLOYER_ADMIN user {} has no employerId", user.getEmployerId());
                return false;
            }
            if (visit.getMember() == null || visit.getMember().getEmployer() == null ||
                !user.getEmployerId().equals(visit.getMember().getEmployer().getId())) {
                log.warn("❌ canAccessVisit: DENIED - user {} attempted to access visit {} from different employer", 
                        user.getUsername(), visitId);
                return false;
            }
            log.debug("✅ canAccessVisit: ALLOWED - user={} employer matches", user.getUsername());
            return true;
        }

        log.warn("❌ canAccessVisit: DENIED - user {} has no valid role for visit access", user.getUsername());
        return false;
    }

    // =============================================================================================
    // QUERY FILTERING METHODS (FOR SERVICE LAYER)
    // =============================================================================================

    /**
     * Get employer filter for queries.
     * 
     * USE THIS IN SERVICE LAYER TO FILTER QUERIES BY EMPLOYER.
     * 
     * FILTERING LOGIC:
     * - SUPER_ADMIN: NULL (no filter - sees everything)
     * - INSURANCE_ADMIN: NULL (no filter - sees everything)
     * - EMPLOYER_ADMIN: user.employerId (filter by their employer)
     * - Others: NULL (no filter - controlled by other means)
     * 
     * USAGE IN SERVICE:
     * <pre>
     * Long employerFilter = authorizationService.getEmployerFilterForUser(currentUser);
     * if (employerFilter != null) {
     *     return repository.findByEmployerId(employerFilter);
     * } else {
     *     return repository.findAll();
     * }
     * </pre>
     * 
     * @param user Current user
     * @return employerId to filter by, or NULL if no filtering needed
     */
    public Long getEmployerFilterForUser(User user) {
        if (user == null) {
            log.warn("⚠️ getEmployerFilterForUser: user is null, returning null filter");
            return null;
        }

        // SUPER_ADMIN sees ALL data - no filter
        if (isSuperAdmin(user)) {
            log.debug("🔓 getEmployerFilterForUser: user={} is SUPER_ADMIN - NO FILTER", user.getUsername());
            return null;
        }

        // INSURANCE_ADMIN sees ALL data - no filter
        if (isInsuranceAdmin(user)) {
            log.debug("🔓 getEmployerFilterForUser: user={} is INSURANCE_ADMIN - NO FILTER", user.getUsername());
            return null;
        }

        // EMPLOYER_ADMIN sees only THEIR employer's data
        if (isEmployerAdmin(user)) {
            Long employerId = user.getEmployerId();
            if (employerId == null) {
                log.warn("⚠️ getEmployerFilterForUser: EMPLOYER_ADMIN user={} has no employerId!", user.getUsername());
            } else {
                log.debug("🔒 getEmployerFilterForUser: user={} filtered by employerId={}", user.getUsername(), employerId);
            }
            return employerId;
        }

        // Other roles: no filtering (for now)
        log.debug("🔓 getEmployerFilterForUser: user={} has other role - NO FILTER", user.getUsername());
        return null;
    }

    /**
     * Get provider filter for current user.
     * Used to filter visits and claims by provider.
     * 
     * AUTHORIZATION MODEL:
     * - SUPER_ADMIN: NO FILTER (sees all providers)
     * - INSURANCE_ADMIN: NO FILTER (sees all providers)
     * - PROVIDER: FILTER by user.providerId (sees only their provider's data)
     * - Others: NO FILTER
     * 
     * USAGE IN SERVICE:
     * <pre>
     * Long providerFilter = authorizationService.getProviderFilterForUser(currentUser);
     * if (providerFilter != null) {
     *     return repository.findByProviderId(providerFilter);
     * } else {
     *     return repository.findAll();
     * }
     * </pre>
     * 
     * @param user Current user
     * @return providerId to filter by, or NULL if no filtering needed
     */
    public Long getProviderFilterForUser(User user) {
        if (user == null) {
            log.warn("⚠️ getProviderFilterForUser: user is null, returning null filter");
            return null;
        }

        // SUPER_ADMIN sees ALL data - no filter
        if (isSuperAdmin(user)) {
            log.debug("🔓 getProviderFilterForUser: user={} is SUPER_ADMIN - NO FILTER", user.getUsername());
            return null;
        }

        // INSURANCE_ADMIN sees ALL data - no filter
        if (isInsuranceAdmin(user)) {
            log.debug("🔓 getProviderFilterForUser: user={} is INSURANCE_ADMIN - NO FILTER", user.getUsername());
            return null;
        }

        // PROVIDER sees only THEIR provider's data
        if (isProvider(user)) {
            Long providerId = user.getProviderId();
            if (providerId == null) {
                log.warn("⚠️ getProviderFilterForUser: PROVIDER user={} has no providerId!", user.getUsername());
            } else {
                log.debug("🔒 getProviderFilterForUser: user={} filtered by providerId={}", user.getUsername(), providerId);
            }
            return providerId;
        }

        // Other roles: no filtering
        log.debug("🔓 getProviderFilterForUser: user={} has other role - NO FILTER", user.getUsername());
        return null;
    }

    /**
     * Check if user can modify a claim (approve/reject).
     * 
     * AUTHORIZATION RULES:
     * - SUPER_ADMIN: ✅ Can modify
     * - INSURANCE_ADMIN: ✅ Can modify
     * - REVIEWER: ✅ Can modify
     * - Others: ❌ Cannot modify
     * 
     * @param user Current user
     * @param claimId ID of the claim to modify
     * @return true if user can modify the claim
     */
    public boolean canModifyClaim(User user, Long claimId) {
        if (user == null || claimId == null) {
            log.warn("❌ canModifyClaim: DENIED - null user or claimId");
            return false;
        }

        // SUPER_ADMIN can modify
        if (isSuperAdmin(user)) {
            log.debug("✅ canModifyClaim: ALLOWED - user={} is SUPER_ADMIN", user.getUsername());
            return true;
        }

        // INSURANCE_ADMIN can modify
        if (isInsuranceAdmin(user)) {
            log.debug("✅ canModifyClaim: ALLOWED - user={} is INSURANCE_ADMIN", user.getUsername());
            return true;
        }

        // REVIEWER can modify (status/review only, enforced by endpoint separation)
        if (isReviewer(user)) {
            log.debug("✅ canModifyClaim: ALLOWED - user={} is REVIEWER", user.getUsername());
            return true;
        }
        
        // PROVIDER can modify IF claim is in editable status
        if (isProvider(user)) {
            Optional<Claim> claimOpt = claimRepository.findById(claimId);
            if (claimOpt.isPresent()) {
                Claim claim = claimOpt.get();
                // Check if provider owns this claim
                if (claim.getProviderId().equals(user.getProviderId())) {
                    // Check if claim status allows editing
                    if (claim.getStatus().allowsEdit()) {
                        log.debug("✅ canModifyClaim: ALLOWED - user={} is PROVIDER, claim status={} allows edit",
                                user.getUsername(), claim.getStatus());
                        return true;
                    } else {
                        log.warn("❌ canModifyClaim: DENIED - claim status={} does not allow editing",
                                claim.getStatus());
                        return false;
                    }
                } else {
                    log.warn("❌ canModifyClaim: DENIED - provider {} does not own claim {}",
                            user.getProviderId(), claimId);
                    return false;
                }
            }
        }
        
        // EMPLOYER_ADMIN can modify (if enabled for their employer)
        if (isEmployerAdmin(user)) {
            Optional<Claim> claimOpt = claimRepository.findById(claimId);
            if (claimOpt.isPresent()) {
                Claim claim = claimOpt.get();
                // Check employer ownership via member
                if (claim.getMember() != null && 
                    claim.getMember().getEmployer() != null &&
                    claim.getMember().getEmployer().getId().equals(user.getEmployerId())) {
                    // Check if claim status allows editing
                    if (claim.getStatus().allowsEdit()) {
                        log.debug("✅ canModifyClaim: ALLOWED - user={} is EMPLOYER_ADMIN, status={} allows edit",
                                user.getUsername(), claim.getStatus());
                        return true;
                    }
                }
            }
        }

        log.warn("❌ canModifyClaim: DENIED - user {} cannot modify claim {}", user.getUsername(), claimId);
        return false;
    }

    // =============================================================================================
    // FEATURE TOGGLE METHODS (EMPLOYER-SPECIFIC PERMISSIONS)
    // =============================================================================================
    //
    // These methods check permission flags.
    //
    // KEY POINT: Non-employer users (SUPER_ADMIN, INSURANCE_ADMIN) always pass these checks.
    // =============================================================================================

    /**
     * Check if EMPLOYER_ADMIN user can view members based on user permission.
     * 
     * SECURITY (2026-01-16):
     * - SUPER_ADMIN/INSURANCE_ADMIN: Always allowed
     * - EMPLOYER_ADMIN: Based on canViewMembers field in User entity
     * - Others: Always allowed (controlled by RBAC)
     * 
     * @param user Current user
     * @return true if user can view members
     */
    public boolean canEmployerViewMembers(User user) {
        if (user == null) {
            log.warn("⚠️ FeatureCheck: user=null feature=VIEW_MEMBERS result=DENIED (null user)");
            return false;
        }

        // SUPER_ADMIN and INSURANCE_ADMIN bypass feature flags
        if (isSuperAdmin(user) || isInsuranceAdmin(user)) {
            log.debug("✅ FeatureCheck: user={} feature=VIEW_MEMBERS result=ALLOWED (admin role)", user.getUsername());
            return true;
        }

        // Non-employer users: always allow (controlled by RBAC)
        if (!isEmployerAdmin(user)) {
            log.debug("✅ FeatureCheck: user={} feature=VIEW_MEMBERS result=ALLOWED (not EMPLOYER_ADMIN)", 
                user.getUsername());
            return true;
        }

        // EMPLOYER_ADMIN: check feature toggle
        if (user.getEmployerId() == null) {
            log.warn("❌ FeatureCheck: user={} feature=VIEW_MEMBERS result=DENIED (no employerId)", 
                user.getUsername());
            return false;
        }

        // VIEW_MEMBERS is controlled by canViewMembers field in User entity
        // Default to true if the field is null (backward compatibility)
        Boolean canViewMembers = user.getCanViewMembers();
        boolean result = canViewMembers == null || canViewMembers;
        
        log.info("🔧 FeatureCheck: employerId={} user={} feature=VIEW_MEMBERS result={}", 
            user.getEmployerId(), user.getUsername(), result ? "ALLOWED" : "DENIED");
        
        return result;
    }

    /**
     * Check if EMPLOYER_ADMIN user can view benefit policies based on feature toggle.
     * 
     * SECURITY (2026-01-16):
     * - SUPER_ADMIN/INSURANCE_ADMIN: Always allowed
     * - EMPLOYER_ADMIN: Based on canViewBenefitPolicies feature flag in User entity
     * - Others: Always allowed (controlled by RBAC)
     * 
     * @param user Current user
     * @return true if user can view benefit policies
     */
    public boolean canEmployerViewBenefitPolicies(User user) {
        if (user == null) {
            log.warn("⚠️ FeatureCheck: user=null feature=VIEW_BENEFIT_POLICIES result=DENIED (null user)");
            return false;
        }

        // SUPER_ADMIN and INSURANCE_ADMIN bypass feature flags
        if (isSuperAdmin(user) || isInsuranceAdmin(user)) {
            log.debug("✅ FeatureCheck: user={} feature=VIEW_BENEFIT_POLICIES result=ALLOWED (admin role)", user.getUsername());
            return true;
        }

        // Non-employer users: always allow (controlled by RBAC)
        if (!isEmployerAdmin(user)) {
            log.debug("✅ FeatureCheck: user={} feature=VIEW_BENEFIT_POLICIES result=ALLOWED (not EMPLOYER_ADMIN)", 
                user.getUsername());
            return true;
        }

        // EMPLOYER_ADMIN: check feature toggle
        if (user.getEmployerId() == null) {
            log.warn("❌ FeatureCheck: user={} feature=VIEW_BENEFIT_POLICIES result=DENIED (no employerId)", 
                user.getUsername());
            return false;
        }

        // VIEW_BENEFIT_POLICIES is controlled by canViewBenefitPolicies field in User entity
        // Default to true if the field is null (backward compatibility)
        Boolean canViewBenefitPolicies = user.getCanViewBenefitPolicies();
        boolean result = canViewBenefitPolicies == null || canViewBenefitPolicies;
        
        log.info("🔧 FeatureCheck: employerId={} user={} feature=VIEW_BENEFIT_POLICIES result={}", 
            user.getEmployerId(), user.getUsername(), result ? "ALLOWED" : "DENIED");
        
        return result;
    }

    /**
     * Check if user can access a specific provider.
     * 
     * AUTHORIZATION RULES:
     * - SUPER_ADMIN: ✅ Full access
     * - INSURANCE_ADMIN: ✅ Full access
     * - PROVIDER: ✅ Only if user.providerId == providerId
     * - Others: ❌ No access
     * 
     * @param user Current user
     * @param providerId ID of the provider to access
     * @return true if user can access the provider
     */
    public boolean canAccessProvider(User user, Long providerId) {
        if (user == null || providerId == null) {
            log.warn("❌ canAccessProvider: DENIED - null user or providerId");
            return false;
        }

        // SUPER_ADMIN bypasses all checks
        if (isSuperAdmin(user)) {
            log.debug("✅ canAccessProvider: ALLOWED - user={} is SUPER_ADMIN", user.getUsername());
            return true;
        }

        // INSURANCE_ADMIN has full access
        if (isInsuranceAdmin(user)) {
            log.debug("✅ canAccessProvider: ALLOWED - user={} is INSURANCE_ADMIN", user.getUsername());
            return true;
        }

        // PROVIDER: Check provider match
        if (isProvider(user)) {
            if (user.getProviderId() == null) {
                log.warn("❌ canAccessProvider: DENIED - PROVIDER user {} has no providerId", user.getUsername());
                return false;
            }
            if (!user.getProviderId().equals(providerId)) {
                log.warn("❌ canAccessProvider: DENIED - user {} (provider={}) attempted to access provider {}", 
                        user.getUsername(), user.getProviderId(), providerId);
                return false;
            }
            log.debug("✅ canAccessProvider: ALLOWED - user={} provider matches", user.getUsername());
            return true;
        }

        log.warn("❌ canAccessProvider: DENIED - user {} has no valid role for provider access", user.getUsername());
        return false;
    }

    /**
     * Check if CURRENT user can access a specific provider.
     * Convenience method for SpEL security expressions.
     * 
     * Usage: @PreAuthorize("@authorizationService.canAccessProvider(#id)")
     * 
     * @param providerId ID of the provider to access
     * @return true if current user can access the provider
     */
    public boolean canAccessProvider(Long providerId) {
        User currentUser = getCurrentUser();
        return canAccessProvider(currentUser, providerId);
    }
}
