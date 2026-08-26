package com.waad.tba.modules.rbac.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.rbac.permission.EffectivePermissionService.EffectivePermissionSnapshot;
import com.waad.tba.modules.rbac.permission.PermissionAdministrationService;
import com.waad.tba.modules.rbac.permission.PermissionAdministrationService.PermissionDefinition;
import com.waad.tba.modules.rbac.permission.PermissionAdministrationService.RoleTemplate;
import com.waad.tba.modules.rbac.permission.ManagedUserAccessService;
import com.waad.tba.modules.rbac.permission.dto.PermissionOverrideRequest;
import com.waad.tba.modules.rbac.permission.dto.ManagedUserCreateRequest;
import com.waad.tba.modules.rbac.permission.dto.RoleTemplateUpdateRequest;
import com.waad.tba.modules.rbac.dto.UserResponseDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/access-control")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN') or @permissionGuard.has('ROLE_PERMISSION_MANAGE')")
public class PermissionAdministrationController {
    private final PermissionAdministrationService service;
    private final ManagedUserAccessService managedUserAccessService;

    @org.springframework.web.bind.annotation.PostMapping("/users")
    public ResponseEntity<ApiResponse<UserResponseDto>> createManagedUser(
            @Valid @RequestBody ManagedUserCreateRequest request) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.success("تم إنشاء المستخدم وتطبيق صلاحياته ذرياً",
                        managedUserAccessService.create(request)));
    }

    @GetMapping("/permissions")
    public ResponseEntity<ApiResponse<List<PermissionDefinition>>> catalogue() {
        return ResponseEntity.ok(ApiResponse.success(service.catalogue()));
    }

    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<RoleTemplate>>> roles() {
        return ResponseEntity.ok(ApiResponse.success(service.roleTemplates()));
    }

    @PutMapping("/roles/{roleCode}/permissions")
    public ResponseEntity<ApiResponse<RoleTemplate>> updateRole(
            @PathVariable String roleCode,
            @Valid @RequestBody RoleTemplateUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "تم تحديث قالب الدور وسحب جلسات المستخدمين المتأثرين",
                service.replaceRolePermissions(roleCode, request)));
    }

    @GetMapping("/users/{userId}/effective-permissions")
    public ResponseEntity<ApiResponse<EffectivePermissionSnapshot>> effective(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(service.effectiveFor(userId)));
    }

    @PutMapping("/users/{userId}/permission-overrides")
    public ResponseEntity<ApiResponse<EffectivePermissionSnapshot>> update(
            @PathVariable Long userId,
            @Valid @RequestBody List<@Valid PermissionOverrideRequest> commands) {
        return ResponseEntity.ok(ApiResponse.success("تم تحديث الصلاحيات وسحب الجلسات النشطة",
                service.applyOverrides(userId, commands)));
    }
}
