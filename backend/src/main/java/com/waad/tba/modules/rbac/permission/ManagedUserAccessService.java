package com.waad.tba.modules.rbac.permission;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.waad.tba.modules.rbac.dto.UserResponseDto;
import com.waad.tba.modules.rbac.permission.dto.ManagedUserCreateRequest;
import com.waad.tba.modules.rbac.permission.dto.ManagedUserUpdateRequest;
import com.waad.tba.modules.rbac.service.UserService;

import lombok.RequiredArgsConstructor;

/** Atomic boundary for identity, primary role, scope and personal overrides. */
@Service
@RequiredArgsConstructor
public class ManagedUserAccessService {
    private final UserService userService;
    private final PermissionAdministrationService permissionAdministrationService;

    @Transactional
    public UserResponseDto create(ManagedUserCreateRequest request) {
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
        UserResponseDto updated = userService.update(userId, request.user(), request.reason());
        if (request.permissionOverrides() != null && !request.permissionOverrides().isEmpty()) {
            permissionAdministrationService.applyOverrides(userId, request.permissionOverrides());
        }
        return updated;
    }
}
