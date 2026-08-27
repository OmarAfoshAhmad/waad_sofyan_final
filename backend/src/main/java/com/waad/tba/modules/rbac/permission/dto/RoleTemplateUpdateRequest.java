package com.waad.tba.modules.rbac.permission.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoleTemplateUpdateRequest(
        @NotNull List<@NotBlank String> permissionCodes,
        @NotBlank @Size(min = 3, max = 500) String reason) {
}
