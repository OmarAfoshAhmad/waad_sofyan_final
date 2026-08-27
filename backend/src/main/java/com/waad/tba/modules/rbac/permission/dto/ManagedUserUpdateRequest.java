package com.waad.tba.modules.rbac.permission.dto;

import java.util.List;

import com.waad.tba.modules.rbac.dto.UserUpdateDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

/** One atomic command for identity, role, scope and personal permissions. */
public record ManagedUserUpdateRequest(
        @NotNull @Valid UserUpdateDto user,
        List<@Valid PermissionOverrideRequest> permissionOverrides,
        @NotBlank String reason) {
}
