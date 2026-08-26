package com.waad.tba.modules.rbac.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.provider.entity.Provider;
import com.waad.tba.modules.provider.repository.ProviderRepository;
import com.waad.tba.modules.employer.entity.Employer;
import com.waad.tba.modules.employer.repository.EmployerRepository;
import com.waad.tba.modules.rbac.dto.UserCreateDto;
import com.waad.tba.modules.rbac.dto.UserResponseDto;
import com.waad.tba.modules.rbac.dto.UserUpdateDto;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.exception.PasswordPolicyViolationException;
import com.waad.tba.modules.rbac.mapper.UserMapper;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.modules.auth.service.SessionManagementService;
import com.waad.tba.security.audit.SecurityAuditEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * User Service - RBAC Hardened
 * 
 * SECURITY HARDENING (2026-01-13):
 * - Role hierarchy enforcement on all write operations
 * - SUPER_ADMIN protection on delete/update
 * - Privilege escalation prevention
 * 
 * @version 2.0 - RBAC Hardening
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserSecurityService securityService;
    private final SessionManagementService sessionManagementService;
    private final ProviderRepository providerRepository;
    private final EmployerRepository employerRepository;

    @Transactional(readOnly = true)
    public List<UserResponseDto> findAll() {
        log.debug("Finding all users");
        return mapUsersWithProviderNames(userRepository.findAll());
    }
    
    public User getByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    @Transactional(readOnly = true)
    public UserResponseDto findById(Long id) {
        log.debug("Finding user by id: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return enrichProviderName(userMapper.toResponseDto(user));
    }

    @Transactional
    public UserResponseDto create(UserCreateDto dto) {
        log.info("Creating new user: {}", dto.getUsername());
        
        // Uniqueness checks
        if (userRepository.existsByUsernameIgnoreCase(dto.getUsername())) {
            throw new IllegalArgumentException("اسم المستخدم '" + dto.getUsername() + "' موجود مسبقاً");
        }
        
        if (userRepository.existsByEmailIgnoreCase(dto.getEmail())) {
            throw new IllegalArgumentException("البريد الإلكتروني '" + dto.getEmail() + "' مسجل مسبقاً");
        }

        // Password policy check (username match)
        if (dto.getPassword().equalsIgnoreCase(dto.getUsername())) {
            throw new PasswordPolicyViolationException("Password cannot be the same as username",
                    java.util.Collections.singletonList("PASSWORD_SAME_AS_USERNAME"));
        }

        User user = userMapper.toEntity(dto);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        String resolvedUserType = resolveUserType(dto.getUserType(), dto.getEmployerId(), dto.getProviderId());
        applyRoleBindings(user, resolvedUserType, dto.getEmployerId(), dto.getProviderId());
        
        User savedUser = userRepository.save(user);
        
        // Send email verification
        securityService.sendEmailVerification(savedUser);
        
        // Audit log
        securityService.auditLog(savedUser.getId(), SecurityAuditEvent.AuditActionType.ACCOUNT_CREATED,
                "User created: " + dto.getUsername(), null, null);
        
        log.info("User created successfully with id: {}", savedUser.getId());
        
        return enrichProviderName(userMapper.toResponseDto(savedUser));
    }

    @Transactional
    public UserResponseDto update(Long id, UserUpdateDto dto) {
        return update(id, dto, null);
    }

    @Transactional
    public UserResponseDto update(Long id, UserUpdateDto dto, String changeReason) {
        log.info("Updating user with id: {}", id);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        // Check username uniqueness if changed
        if (!user.getUsername().equalsIgnoreCase(dto.getUsername()) && userRepository.existsByUsernameIgnoreCase(dto.getUsername())) {
            throw new IllegalArgumentException("اسم المستخدم '" + dto.getUsername() + "' موجود مسبقاً");
        }

        // Check email uniqueness if changed
        if (!user.getEmail().equalsIgnoreCase(dto.getEmail()) && userRepository.existsByEmailIgnoreCase(dto.getEmail())) {
            throw new IllegalArgumentException("البريد الإلكتروني '" + dto.getEmail() + "' مسجل مسبقاً");
        }

        String oldEmail = user.getEmail();
        String oldUsername = user.getUsername();
        String oldUserType = user.getUserType();
        Long oldEmployerId = user.getEmployerId();
        Long oldProviderId = user.getProviderId();
        Boolean oldActive = user.getActive();
        Boolean oldCanViewClaims = user.getCanViewClaims();
        Boolean oldCanViewVisits = user.getCanViewVisits();
        Boolean oldCanViewReports = user.getCanViewReports();
        Boolean oldCanViewMembers = user.getCanViewMembers();
        Boolean oldCanViewBenefitPolicies = user.getCanViewBenefitPolicies();

        String resolvedUserType = resolveUserType(dto.getUserType(), dto.getEmployerId(), dto.getProviderId());

        // PROTECTION: never allow update() to bypass status/demotion protections.
        boolean wasSuperAdmin = "SUPER_ADMIN".equals(oldUserType);
        boolean stillSuperAdmin = "SUPER_ADMIN".equals(resolvedUserType);
        boolean requestedDeactivate = Boolean.FALSE.equals(dto.getActive()) && Boolean.TRUE.equals(oldActive);
        boolean lastActiveSuperAdmin = wasSuperAdmin && userRepository.countByUserTypeAndActiveTrue("SUPER_ADMIN") <= 1;

        if (wasSuperAdmin && !stillSuperAdmin && lastActiveSuperAdmin) {
            log.error("⛔ Attempt to demote the last active SUPER_ADMIN user: id={}, username={}", id, user.getUsername());
            throw new IllegalArgumentException("لا يمكن تغيير دور آخر مستخدم SUPER_ADMIN نشط في النظام");
        }

        if (wasSuperAdmin && requestedDeactivate && lastActiveSuperAdmin) {
            log.error("⛔ Attempt to deactivate the last active SUPER_ADMIN user through update(): id={}, username={}", id, user.getUsername());
            throw new IllegalArgumentException("لا يمكن تعطيل آخر مستخدم SUPER_ADMIN نشط في النظام");
        }

        userMapper.updateEntityFromDto(user, dto);

        applyRoleBindings(user, resolvedUserType, dto.getEmployerId(), dto.getProviderId());
        User updatedUser = userRepository.save(user);

        if (requiresSessionRevocation(oldUserType, updatedUser.getUserType())
                || requiresSessionRevocation(oldEmployerId, updatedUser.getEmployerId())
                || requiresSessionRevocation(oldProviderId, updatedUser.getProviderId())
                || requiresSessionRevocation(oldActive, updatedUser.getActive())
                || requiresSessionRevocation(oldCanViewClaims, updatedUser.getCanViewClaims())
                || requiresSessionRevocation(oldCanViewVisits, updatedUser.getCanViewVisits())
                || requiresSessionRevocation(oldCanViewReports, updatedUser.getCanViewReports())
                || requiresSessionRevocation(oldCanViewMembers, updatedUser.getCanViewMembers())
                || requiresSessionRevocation(oldCanViewBenefitPolicies, updatedUser.getCanViewBenefitPolicies())) {
            sessionManagementService.revokeAll(oldUsername);
        }
        
        // Audit log
        securityService.auditLog(id, SecurityAuditEvent.AuditActionType.ACCOUNT_UPDATED,
                "User updated" + (oldEmail.equals(dto.getEmail()) ? "" : ", email changed")
                        + (changeReason == null || changeReason.isBlank() ? "" : ", reason: " + changeReason.trim()),
                null, null);
        
        log.info("User updated successfully: {}", id);
        return enrichProviderName(userMapper.toResponseDto(updatedUser));
    }

    @Transactional
    public void delete(Long id) {
        log.info("Deleting user with id: {}", id);
        
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        
        boolean isSuperAdmin = "SUPER_ADMIN".equals(user.getUserType());
        
        if (isSuperAdmin) {
            log.error("⛔ Attempt to delete SUPER_ADMIN user: id={}, username={}", id, user.getUsername());
            throw new IllegalArgumentException("Cannot delete SUPER_ADMIN user");
        }
        
        if (Boolean.FALSE.equals(user.getActive())) {
            log.info("User already inactive, treating delete as idempotent soft-delete: {}", id);
        }

        user.setActive(false);
        userRepository.save(user);
        sessionManagementService.revokeAll(user.getUsername());

        // Audit log before logical deletion
        securityService.auditLog(id, SecurityAuditEvent.AuditActionType.ACCOUNT_DELETED,
                "User deleted (soft delete)", null, null);

        log.info("User soft-deleted successfully: {}", id);
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> search(String query) {
        log.debug("Searching users with query: {}", query);
        return mapUsersWithProviderNames(userRepository.searchUsers(query));
    }
    
    /**
     * Find users not assigned to any provider
     * Used in provider management for linking users to providers
     */
    @Transactional(readOnly = true)
    public List<UserResponseDto> findUnassignedProviders() {
        log.debug("Finding users not assigned to any provider");
        return mapUsersWithProviderNames(userRepository.findByProviderIdIsNull());
    }
    
    /**
     * Find users assigned to a specific provider
     * Used in provider management to show account manager
     */
    @Transactional(readOnly = true)
    public List<UserResponseDto> findByProviderId(Long providerId) {
        log.debug("Finding users assigned to provider: {}", providerId);
        return mapUsersWithProviderNames(userRepository.findByProviderId(providerId));
    }

    @Transactional(readOnly = true)
    public Page<UserResponseDto> findAllPaginated(Pageable pageable) {
        log.debug("Finding users with pagination");
        Page<User> users = userRepository.findAll(pageable);
        Map<Long, String> providerNames = loadProviderNames(users.getContent());
        return users.map(user -> enrichProviderName(userMapper.toResponseDto(user), providerNames));
    }

    @Transactional(readOnly = true)
    public Page<UserResponseDto> searchPaginated(String query, Pageable pageable) {
        log.debug("Searching users with pagination, query: {}", query);
        if (query == null || query.isBlank()) {
            return findAllPaginated(pageable);
        }
        Page<User> users = userRepository.searchUsers(query.trim(), pageable);
        Map<Long, String> providerNames = loadProviderNames(users.getContent());
        return users.map(user -> enrichProviderName(userMapper.toResponseDto(user), providerNames));
    }

    @Transactional(readOnly = true)
    public Page<UserResponseDto> searchPaginated(String query, String role, Boolean active, String providerLink, Pageable pageable) {
        log.debug("Searching users with pagination, query: {}, role: {}, active: {}, providerLink: {}", query, role, active, providerLink);
        String normalizedQuery = query == null ? "" : query.trim();
        String normalizedRole = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        String normalizedProviderLink = providerLink == null ? "" : providerLink.trim().toUpperCase(Locale.ROOT);
        Page<User> users = userRepository.searchUsersFiltered(normalizedQuery, normalizedRole, active, normalizedProviderLink, pageable);
        Map<Long, String> providerNames = loadProviderNames(users.getContent());
        return users.map(user -> enrichProviderName(userMapper.toResponseDto(user), providerNames));
    }

    private List<UserResponseDto> mapUsersWithProviderNames(List<User> users) {
        Map<Long, String> providerNames = loadProviderNames(users);
        return users.stream()
                .map(user -> enrichProviderName(userMapper.toResponseDto(user), providerNames))
                .collect(Collectors.toList());
    }

    private UserResponseDto enrichProviderName(UserResponseDto dto) {
        if (dto == null || dto.getProviderId() == null) {
            return dto;
        }
        providerRepository.findById(dto.getProviderId())
                .map(Provider::getName)
                .ifPresent(dto::setProviderName);
        return dto;
    }

    private UserResponseDto enrichProviderName(UserResponseDto dto, Map<Long, String> providerNames) {
        if (dto != null && dto.getProviderId() != null) {
            dto.setProviderName(providerNames.get(dto.getProviderId()));
        }
        return dto;
    }

    private Map<Long, String> loadProviderNames(List<User> users) {
        Set<Long> providerIds = users.stream()
                .map(User::getProviderId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        if (providerIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        return providerRepository.findByIdIn(providerIds).stream()
                .collect(Collectors.toMap(Provider::getId, Provider::getName, (left, right) -> left));
    }

    @Transactional(readOnly = true)
    public User findByUsernameOrEmail(String identifier) {
        return userRepository.findByUsernameOrEmail(identifier, identifier)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with identifier: " + identifier));
    }

    /**
     * Toggle user active status (activate/deactivate)
     * SUPER_ADMIN users cannot be deactivated.
     */
    @Transactional
    public UserResponseDto toggleStatus(Long id) {
        log.info("Toggling status for user: {}", id);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        
        // PROTECTION: SUPER_ADMIN cannot be deactivated
        boolean isSuperAdmin = "SUPER_ADMIN".equals(user.getUserType());
        
        if (isSuperAdmin && Boolean.TRUE.equals(user.getActive())) {
            log.error("⛔ Attempt to deactivate SUPER_ADMIN user: id={}, username={}", id, user.getUsername());
            throw new IllegalArgumentException("لا يمكن تعطيل مستخدم SUPER_ADMIN");
        }
        
        // Toggle the status
        boolean newStatus = !Boolean.TRUE.equals(user.getActive());
        user.setActive(newStatus);
        User savedUser = userRepository.save(user);
        sessionManagementService.revokeAll(user.getUsername());
        
        // Audit log
        SecurityAuditEvent.AuditActionType action = newStatus
                ? SecurityAuditEvent.AuditActionType.ACCOUNT_ACTIVATED
                : SecurityAuditEvent.AuditActionType.ACCOUNT_DEACTIVATED;
        String details = newStatus ? "User activated" : "User deactivated";
        securityService.auditLog(id, action, details, null, null);
        
        log.info("User {} status changed to: {}", id, newStatus ? "ACTIVE" : "INACTIVE");
        return userMapper.toResponseDto(savedUser);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        if (newPassword.equalsIgnoreCase(user.getUsername())) {
            throw new PasswordPolicyViolationException(
                    "Password cannot be the same as username",
                    java.util.Collections.singletonList("PASSWORD_SAME_AS_USERNAME"));
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        // 🔐 Audit logging (single write — administrator-initiated reset, not a self-service change)
        securityService.auditLog(id, SecurityAuditEvent.AuditActionType.PASSWORD_RESET,
                "Password reset by administrator", null, null);
        sessionManagementService.revokeAll(user.getUsername());
    }

    private String resolveUserType(String requestedUserType, Long employerId, Long providerId) {
        if (requestedUserType != null && !requestedUserType.isBlank()) {
            String normalized = requestedUserType.trim().toUpperCase(Locale.ROOT);
            // A bogus/typo'd role string (e.g. "SUPERADMIN") would mint a
            // Spring Security authority matching no @PreAuthorize expression,
            // silently locking the account out of everything with no error
            // at creation time — validate against the fixed role set instead.
            boolean isKnownRole = java.util.Arrays.stream(com.waad.tba.security.rbac.SystemRole.values())
                    .anyMatch(role -> role.name().equals(normalized));
            if (!isKnownRole) {
                throw new IllegalArgumentException("نوع مستخدم غير صالح: " + requestedUserType);
            }
            return normalized;
        }

        if (employerId != null && providerId != null) {
            throw new IllegalArgumentException("User cannot be linked to both employerId and providerId");
        }
        if (employerId != null) {
            return "EMPLOYER_ADMIN";
        }
        if (providerId != null) {
            return "PROVIDER_STAFF";
        }
        return "DATA_ENTRY";
    }

    private void applyRoleBindings(User user, String userType, Long employerId, Long providerId) {
        user.setUserType(userType);

        if ("EMPLOYER_ADMIN".equals(userType) || "DATA_ENTRY".equals(userType)) {
            if (employerId == null) {
                throw new IllegalArgumentException("جهة العمل مطلوبة لهذا الدور");
            }
            Employer employer = employerRepository.findById(employerId)
                    .orElseThrow(() -> new IllegalArgumentException("جهة العمل المحددة غير موجودة"));
            if (!Boolean.TRUE.equals(employer.getActive())) {
                throw new IllegalArgumentException("لا يمكن ربط المستخدم بجهة عمل غير نشطة");
            }
            user.setEmployerId(employerId);
            user.setProviderId(null);
            return;
        }

        if ("PROVIDER_STAFF".equals(userType)) {
            if (providerId == null) {
                throw new IllegalArgumentException("providerId is required for PROVIDER_STAFF");
            }
            user.setProviderId(providerId);
            user.setEmployerId(null);
            return;
        }

        if (employerId != null || providerId != null) {
            throw new IllegalArgumentException("ربط جهة العمل مسموح فقط لمدير جهة العمل ومدخل البيانات، وربط مقدم الخدمة لموظف مقدم الخدمة");
        }

        user.setEmployerId(null);
        user.setProviderId(null);
    }

    private boolean requiresSessionRevocation(Object oldValue, Object newValue) {
        return !java.util.Objects.equals(oldValue, newValue);
    }
}
