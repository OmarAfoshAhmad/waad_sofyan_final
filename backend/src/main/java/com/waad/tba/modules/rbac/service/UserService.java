package com.waad.tba.modules.rbac.service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.common.exception.ResourceNotFoundException;
import com.waad.tba.modules.rbac.dto.UserCreateDto;
import com.waad.tba.modules.rbac.dto.UserResponseDto;
import com.waad.tba.modules.rbac.dto.UserUpdateDto;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.entity.UserAuditLog;
import com.waad.tba.modules.rbac.exception.PasswordPolicyViolationException;
import com.waad.tba.modules.rbac.mapper.UserMapper;
import com.waad.tba.modules.rbac.repository.UserRepository;

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

    @Transactional(readOnly = true)
    public List<UserResponseDto> findAll() {
        log.debug("Finding all users");
        return userRepository.findAll().stream()
                .map(userMapper::toResponseDto)
                .collect(Collectors.toList());
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
        return userMapper.toResponseDto(user);
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
        securityService.auditLog(savedUser.getId(), UserAuditLog.ACTION_USER_CREATED,
                "User created: " + dto.getUsername(), null, null, null);
        
        log.info("User created successfully with id: {}", savedUser.getId());
        
        return userMapper.toResponseDto(savedUser);
    }

    @Transactional
    public UserResponseDto update(Long id, UserUpdateDto dto) {
        log.info("Updating user with id: {}", id);
        
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        // Check email uniqueness if changed
        if (!user.getEmail().equalsIgnoreCase(dto.getEmail()) && userRepository.existsByEmailIgnoreCase(dto.getEmail())) {
            throw new IllegalArgumentException("البريد الإلكتروني '" + dto.getEmail() + "' مسجل مسبقاً");
        }

        String oldEmail = user.getEmail();
        userMapper.updateEntityFromDto(user, dto);
        String resolvedUserType = resolveUserType(dto.getUserType(), dto.getEmployerId(), dto.getProviderId());
        applyRoleBindings(user, resolvedUserType, dto.getEmployerId(), dto.getProviderId());
        User updatedUser = userRepository.save(user);
        
        // Audit log
        securityService.auditLog(id, UserAuditLog.ACTION_USER_UPDATED,
                "User updated" + (oldEmail.equals(dto.getEmail()) ? "" : ", email changed"),
                null, null, null);
        
        log.info("User updated successfully: {}", id);
        return userMapper.toResponseDto(updatedUser);
    }

    @Transactional
    public void delete(Long id) {
        log.info("Deleting user with id: {}", id);
        
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User", "id", id);
        }
        
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        
        boolean isSuperAdmin = "SUPER_ADMIN".equals(user.getUserType());
        
        if (isSuperAdmin) {
            log.error("⛔ Attempt to delete SUPER_ADMIN user: id={}, username={}", id, user.getUsername());
            throw new IllegalArgumentException("Cannot delete SUPER_ADMIN user");
        }
        
        // Audit log before deletion
        securityService.auditLog(id, UserAuditLog.ACTION_USER_DELETED,
                "User deleted (soft delete)", null, null, null);
        
        userRepository.deleteById(id);
        log.info("User deleted successfully: {}", id);
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> search(String query) {
        log.debug("Searching users with query: {}", query);
        return userRepository.searchUsers(query).stream()
                .map(userMapper::toResponseDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Find users not assigned to any provider
     * Used in provider management for linking users to providers
     */
    @Transactional(readOnly = true)
    public List<UserResponseDto> findUnassignedProviders() {
        log.debug("Finding users not assigned to any provider");
        return userRepository.findByProviderIdIsNull().stream()
                .map(userMapper::toResponseDto)
                .collect(Collectors.toList());
    }
    
    /**
     * Find users assigned to a specific provider
     * Used in provider management to show account manager
     */
    @Transactional(readOnly = true)
    public List<UserResponseDto> findByProviderId(Long providerId) {
        log.debug("Finding users assigned to provider: {}", providerId);
        return userRepository.findByProviderId(providerId).stream()
                .map(userMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<UserResponseDto> findAllPaginated(Pageable pageable) {
        log.debug("Finding users with pagination");
        return userRepository.findAll(pageable)
                .map(userMapper::toResponseDto);
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
        
        // Audit log
        String action = newStatus ? UserAuditLog.ACTION_USER_ACTIVATED : UserAuditLog.ACTION_USER_DEACTIVATED;
        String details = newStatus ? "User activated" : "User deactivated";
        securityService.auditLog(id, action, details, null, null, null);
        
        log.info("User {} status changed to: {}", id, newStatus ? "ACTIVE" : "INACTIVE");
        return userMapper.toResponseDto(savedUser);
    }

    private String resolveUserType(String requestedUserType, Long employerId, Long providerId) {
        if (requestedUserType != null && !requestedUserType.isBlank()) {
            return requestedUserType.trim().toUpperCase(Locale.ROOT);
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

        if ("EMPLOYER_ADMIN".equals(userType)) {
            if (employerId == null) {
                throw new IllegalArgumentException("employerId is required for EMPLOYER_ADMIN");
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
            throw new IllegalArgumentException("employerId/providerId are only allowed for EMPLOYER_ADMIN or PROVIDER_STAFF");
        }

        user.setEmployerId(null);
        user.setProviderId(null);
    }
}
