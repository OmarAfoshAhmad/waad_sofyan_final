package com.waad.tba.modules.rbac.permission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PermissionOverrideRequest(
        @NotBlank String permissionCode,
        @NotNull OverrideMode mode,
        @NotBlank @Size(min = 3, max = 500) String reason) {
    public enum OverrideMode { INHERIT, GRANT, REVOKE }
}
