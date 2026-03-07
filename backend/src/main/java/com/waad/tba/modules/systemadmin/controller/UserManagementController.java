package com.waad.tba.modules.systemadmin.controller;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.systemadmin.dto.UserViewDto;
import com.waad.tba.modules.systemadmin.service.UserManagementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * User Management Controller
 * Phase 2 - System Administration
 * 
 * REST API for user account management (SUPER_ADMIN only)
 * Base path: /api/admin/user-management
 * 
 * NOTE: This controller manages ONLY user account operations (status, password).
 * For User CRUD operations, use /api/admin/users (UserController).
 */
@RestController
@RequestMapping("/api/v1/admin/user-management")
@Tag(name = "User Management", description = "User account management: status, password (SUPER_ADMIN only)")
@Slf4j
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserManagementController {

    private final UserManagementService userManagementService;

    /**
     * PUT /api/admin/user-management/{id}/toggle
     * Toggle user active status
     */
    @PutMapping("/{id}/toggle")
    @Operation(summary = "Toggle user status", description = "Activate or deactivate a user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "User status toggled successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - SUPER_ADMIN only")
    })
    public ApiResponse<UserViewDto> toggleUserStatus(@PathVariable Long id,
                                                     @RequestParam(name = "active") Boolean active,
                                                     Authentication authentication) {
        log.info("PUT /api/admin/user-management/{}/toggle?active={}", id, active);
        String updatedBy = authentication.getName();
        UserViewDto toggled = userManagementService.toggleUserStatus(id, active, updatedBy);
        return ApiResponse.success("User status toggled successfully", toggled);
    }

    /**
     * PUT /api/admin/user-management/{id}/reset-password
     * Reset user password
     */
    @PutMapping("/{id}/reset-password")
    @Operation(summary = "Reset user password", description = "Reset password for a user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Password reset successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - SUPER_ADMIN only")
    })
    public ApiResponse<Void> resetPassword(@PathVariable Long id,
                                           @RequestBody Map<String, String> payload,
                                           Authentication authentication) {
        log.info("PUT /api/admin/user-management/{}/reset-password", id);
        String newPassword = payload.get("newPassword");
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return ApiResponse.error("New password is required");
        }
        String updatedBy = authentication.getName();
        userManagementService.resetUserPassword(id, newPassword, updatedBy);
        return ApiResponse.success("Password reset successfully", null);
    }

}
