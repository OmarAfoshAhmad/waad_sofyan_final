package com.waad.tba.modules.rbac.permission;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import com.waad.tba.modules.rbac.dto.UserResponseDto;
import com.waad.tba.modules.rbac.permission.dto.ManagedUserCreateRequest;
import com.waad.tba.modules.rbac.permission.dto.ManagedUserUpdateRequest;
import com.waad.tba.modules.rbac.service.UserService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.rbac.repository.UserRepository;
import com.waad.tba.security.rbac.SystemRole;

import lombok.RequiredArgsConstructor;

/** Atomic boundary for identity, primary role, scope and personal overrides. */
@Service
@RequiredArgsConstructor
public class ManagedUserAccessService {
    private final UserService userService;
    private final PermissionAdministrationService permissionAdministrationService;
    private final EffectivePermissionService effectivePermissionService;
    private final UserRepository userRepository;

    @Transactional
    public UserResponseDto create(ManagedUserCreateRequest request) {
        assertMayCreate(request.user().getUserType());
        UserResponseDto created = userService.create(request.user());
        if (request.permissionOverrides() != null && !request.permissionOverrides().isEmpty()) {
            permissionAdministrationService.applyOverrides(created.getId(), request.permissionOverrides());
        }
        return created;
    }

    /**
     * The edit screen must never persist a new role/scope and then fail while
     * applying its permission exceptions. Both parts are one security decision.
     */
    @Transactional
    public UserResponseDto update(Long userId, ManagedUserUpdateRequest request) {
        assertMayUpdate(userId, request.user().getUserType());
        UserResponseDto updated = userService.update(userId, request.user(), request.reason());
        if (request.permissionOverrides() != null && !request.permissionOverrides().isEmpty()) {
            permissionAdministrationService.applyOverrides(userId, request.permissionOverrides());
        }
        return updated;
    }

    @Transactional(readOnly = true)
    public void assertMayCreate(String requestedRole) {
        assertMayManage(requestedRole, null);
    }

    @Transactional(readOnly = true)
    public void assertMayUpdate(Long targetUserId, String requestedRole) {
        assertMayManage(requestedRole, targetUserId);
    }

    private void assertMayManage(String requestedRole, Long targetUserId) {
        User actor = requireActor();
        var actorPermissions = effectivePermissionService.resolve(actor);
        if (!actorPermissions.contains(SystemPermission.USER_MANAGE)) {
            throw new AccessDeniedException("لا تملك صلاحية إدارة المستخدمين");
        }
        SystemRole role = SystemRole.fromString(requestedRole);
        if (role == null) throw new IllegalArgumentException("الدور غير معروف: " + requestedRole);
        if (!actorPermissions.containsAll(effectivePermissionService.resolveRole(role))) {
            throw new AccessDeniedException("لا يمكن إنشاء أو إسناد دور يتضمن صلاحيات لا يملكها المفوّض");
        }
        if (targetUserId != null) {
            User target = userRepository.findById(targetUserId)
                    .orElseThrow(() -> new com.waad.tba.common.exception.ResourceNotFoundException("User", "id", targetUserId));
            if (actor.getId().equals(target.getId())) {
                throw new IllegalArgumentException("لا يمكن تعديل الدور والنطاق والصلاحيات للحساب المستخدم حالياً");
            }
            if (!actorPermissions.containsAll(effectivePermissionService.resolve(target))) {
                throw new AccessDeniedException("لا يمكن تعديل مستخدم يملك صلاحيات أعلى من المفوّض");
            }
        }
    }

    private User requireActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user no longer exists"));
    }
}
