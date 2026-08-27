package com.waad.tba.modules.rbac.permission;

import org.springframework.stereotype.Component;

import com.waad.tba.modules.rbac.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** Spring method-security bridge: @PreAuthorize("@permissionGuard.has('MEMBER_VIEW')"). */
@Component("permissionGuard")
@RequiredArgsConstructor
public class PermissionGuard {
    private final UserRepository userRepository;
    private final EffectivePermissionService effectivePermissionService;

    public boolean has(String permissionCode) {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return false;
        return userRepository.findByUsername(authentication.getName())
                .map(user -> effectivePermissionService.resolve(user).contains(SystemPermission.parse(permissionCode)))
                .orElse(false);
    }
}
