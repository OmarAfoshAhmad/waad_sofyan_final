package com.waad.tba.modules.systemadmin.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.systemadmin.dto.ModuleAccessDto;
import com.waad.tba.modules.systemadmin.service.ModuleAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Module Access Controller
 * Phase 2 - System Administration
 * 
 * REST API for module access control (SUPER_ADMIN only)
 * Base path: /api/admin/modules
 */
@RestController
@RequestMapping("/api/v1/admin/modules")
@Tag(name = "Module Access", description = "Module access configuration (SUPER_ADMIN only)")
@Slf4j
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ModuleAccessController {

        private final ModuleAccessService moduleAccessService;

        /**
         * GET /api/admin/modules
         * Get all modules
         */
        @GetMapping
        @Operation(summary = "Get all modules", description = "Retrieve all module access configurations")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Modules retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - SUPER_ADMIN only")
        })
        public ApiResponse<List<ModuleAccessDto>> getAllModules() {
                log.info("GET /api/admin/modules");
                List<ModuleAccessDto> modules = moduleAccessService.getAllModules();
                return ApiResponse.success("Modules retrieved successfully", modules);
        }

        /**
         * GET /api/admin/modules/active
         * Get active modules
         */
        @GetMapping("/active")
        @Operation(summary = "Get active modules", description = "Retrieve only active modules")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Active modules retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - SUPER_ADMIN only")
        })
        public ApiResponse<List<ModuleAccessDto>> getActiveModules() {
                log.info("GET /api/admin/modules/active");
                List<ModuleAccessDto> modules = moduleAccessService.getActiveModules();
                return ApiResponse.success("Active modules retrieved successfully", modules);
        }

        /**
         * GET /api/admin/modules/{id}
         * Get module by ID
         */
        @GetMapping("/{id:\\d+}")
        @Operation(summary = "Get module by ID", description = "Retrieve single module configuration")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Module retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Module not found"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - SUPER_ADMIN only")
        })
        public ApiResponse<ModuleAccessDto> getModuleById(@PathVariable("id") Long id) {
                log.info("GET /api/admin/modules/{}", id);
                ModuleAccessDto module = moduleAccessService.getModuleById(id);
                return ApiResponse.success("Module retrieved successfully", module);
        }

        /**
         * GET /api/admin/modules/key/{key}
         * Get module by key
         */
        @GetMapping("/key/{key}")
        @Operation(summary = "Get module by key", description = "Retrieve module by unique key")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Module retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Module not found"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - SUPER_ADMIN only")
        })
        public ApiResponse<ModuleAccessDto> getModuleByKey(@PathVariable("key") String key) {
                log.info("GET /api/admin/modules/key/{}", key);
                ModuleAccessDto module = moduleAccessService.getModuleByKey(key);
                return ApiResponse.success("Module retrieved successfully", module);
        }

        /**
         * POST /api/admin/modules
         * Create new module
         */
        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        @Operation(summary = "Create module", description = "Create a new module access configuration")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Module created successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error or module key already exists"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - SUPER_ADMIN only")
        })
        public ApiResponse<ModuleAccessDto> createModule(@Valid @RequestBody ModuleAccessDto dto,
                        Authentication authentication) {
                log.info("POST /api/admin/modules - Creating module: {}", dto.getModuleKey());
                String createdBy = authentication.getName();
                ModuleAccessDto created = moduleAccessService.createModule(dto, createdBy);
                return ApiResponse.success("Module created successfully", created);
        }

        /**
         * PUT /api/admin/modules/{id}
         * Update module
         */
        @PutMapping("/{id:\\d+}")
        @Operation(summary = "Update module", description = "Update existing module configuration")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Module updated successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Module not found"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - SUPER_ADMIN only")
        })
        public ApiResponse<ModuleAccessDto> updateModule(@PathVariable("id") Long id,
                        @Valid @RequestBody ModuleAccessDto dto,
                        Authentication authentication) {
                log.info("PUT /api/admin/modules/{} - Updating module", id);
                String updatedBy = authentication.getName();
                ModuleAccessDto updated = moduleAccessService.updateModule(id, dto, updatedBy);
                return ApiResponse.success("Module updated successfully", updated);
        }

        /**
         * DELETE /api/admin/modules/{id}
         * Delete module
         */
        @DeleteMapping("/{id:\\d+}")
        @Operation(summary = "Delete module", description = "Permanently delete a module configuration")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Module deleted successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Module not found"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - SUPER_ADMIN only")
        })
        public ApiResponse<Void> deleteModule(@PathVariable("id") Long id, Authentication authentication) {
                log.info("DELETE /api/admin/modules/{}", id);
                String deletedBy = authentication.getName();
                moduleAccessService.deleteModule(id, deletedBy);
                return ApiResponse.success("Module deleted successfully", null);
        }

        /**
         * PUT /api/admin/modules/{id}/toggle
         * Toggle module active status
         */
        @PutMapping("/{id:\\d+}/toggle")
        @Operation(summary = "Toggle module status", description = "Activate or deactivate a module")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Module status toggled successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Module not found"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - SUPER_ADMIN only")
        })
        public ApiResponse<ModuleAccessDto> toggleModuleStatus(@PathVariable("id") Long id,
                        @RequestParam(name = "active") Boolean active,
                        Authentication authentication) {
                log.info("PUT /api/admin/modules/{}/toggle?active={}", id, active);
                String updatedBy = authentication.getName();
                ModuleAccessDto toggled = moduleAccessService.toggleModuleStatus(id, active, updatedBy);
                return ApiResponse.success("Module status toggled successfully", toggled);
        }

        /**
         * PUT /api/admin/modules/{id}/access
         * Update module access (roles & permissions)
         */
        @PutMapping("/{id:\\d+}/access")
        @Operation(summary = "Update module access", description = "Update allowed roles and required permissions")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Module access updated successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Module not found"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - SUPER_ADMIN only")
        })
        public ApiResponse<ModuleAccessDto> updateModuleAccess(@PathVariable("id") Long id,
                        @RequestBody Map<String, List<String>> payload,
                        Authentication authentication) {
                log.info("PUT /api/admin/modules/{}/access", id);
                List<String> allowedRoles = payload.get("allowedRoles");
                List<String> requiredPermissions = payload.get("requiredPermissions");

                if (allowedRoles == null)
                        allowedRoles = List.of();
                if (requiredPermissions == null)
                        requiredPermissions = List.of();

                String updatedBy = authentication.getName();
                ModuleAccessDto updated = moduleAccessService.updateModuleAccess(
                                id, allowedRoles, requiredPermissions, updatedBy);
                return ApiResponse.success("Module access updated successfully", updated);
        }

        /**
         * GET /api/admin/modules/role/{roleName}
         * Get modules accessible by role
         */
        @GetMapping("/role/{roleName}")
        @Operation(summary = "Get modules for role", description = "List modules accessible by specific role")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Modules retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - SUPER_ADMIN only")
        })
        public ApiResponse<List<ModuleAccessDto>> getModulesForRole(@PathVariable("roleName") String roleName) {
                log.info("GET /api/admin/modules/role/{}", roleName);
                List<ModuleAccessDto> modules = moduleAccessService.getModulesForRole(roleName);
                return ApiResponse.success("Modules retrieved successfully", modules);
        }

        /**
         * GET /api/admin/modules/feature/{flagKey}
         * Get modules by feature flag
         */
        @GetMapping("/feature/{flagKey}")
        @Operation(summary = "Get modules by feature flag", description = "List modules linked to specific feature flag")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Modules retrieved successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - SUPER_ADMIN only")
        })
        public ApiResponse<List<ModuleAccessDto>> getModulesByFeatureFlag(@PathVariable("flagKey") String flagKey) {
                log.info("GET /api/admin/modules/feature/{}", flagKey);
                List<ModuleAccessDto> modules = moduleAccessService.getModulesByFeatureFlag(flagKey);
                return ApiResponse.success("Modules retrieved successfully", modules);
        }
}

