package com.waad.tba.modules.rbac.permission.dto;

import java.util.List;

import com.waad.tba.modules.rbac.dto.UserCreateDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record ManagedUserCreateRequest(
        @NotNull @Valid UserCreateDto user,
        List<@Valid PermissionOverrideRequest> permissionOverrides) {
}
