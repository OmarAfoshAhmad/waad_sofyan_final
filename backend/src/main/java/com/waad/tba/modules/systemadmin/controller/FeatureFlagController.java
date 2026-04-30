package com.waad.tba.modules.systemadmin.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.systemadmin.dto.FeatureFlagDto;
import com.waad.tba.modules.systemadmin.service.FeatureFlagService;
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

@RestController
@RequestMapping({"/api/v1/admin/features", "/api/admin/features"})
@Tag(name = "Feature Flags", description = "Feature flag management (SUPER_ADMIN only)")
@Slf4j
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;

    /**
     * Public endpoint — returns only UI-relevant feature flags (no SUPER_ADMIN
     * required).
     * Used by the frontend on app load to determine visible features.
     * Internal-only flags are excluded.
     */
    @GetMapping("/public")
    @Operation(summary = "Get public feature flags (no auth required)")
    @PreAuthorize("permitAll()")
    public ApiResponse<List<FeatureFlagDto>> getPublicFeatureFlags() {
        List<FeatureFlagDto> publicFlags = featureFlagService.getAllFeatureFlags().stream()
                .filter(f -> !f.getFlagKey().startsWith("INTERNAL_"))
                .toList();
        return ApiResponse.success("Public feature flags retrieved", publicFlags);
    }

    @GetMapping
    @Operation(summary = "Get all feature flags")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ApiResponse<List<FeatureFlagDto>> getAllFeatureFlags() {
        List<FeatureFlagDto> flags = featureFlagService.getAllFeatureFlags();
        return ApiResponse.success("Feature flags retrieved", flags);
    }

    @GetMapping("/{key}")
    @Operation(summary = "Get feature flag by key")
    public ApiResponse<FeatureFlagDto> getFeatureFlagByKey(@PathVariable("key") String key) {
        FeatureFlagDto flag = featureFlagService.getFeatureFlagByKey(key);
        return ApiResponse.success("Feature flag retrieved", flag);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create feature flag")
    public ApiResponse<FeatureFlagDto> createFeatureFlag(
            @Valid @RequestBody FeatureFlagDto dto,
            Authentication authentication) {
        String createdBy = authentication.getName();
        FeatureFlagDto created = featureFlagService.createFeatureFlag(dto, createdBy);
        return ApiResponse.success("Feature flag created", created);
    }

    @PutMapping("/{key}/toggle")
    @Operation(summary = "Toggle feature flag")
    public ApiResponse<FeatureFlagDto> toggleFeatureFlag(
            @PathVariable("key") String key,
            @RequestParam(name = "enabled") Boolean enabled,
            Authentication authentication) {
        String updatedBy = authentication.getName();
        FeatureFlagDto toggled = featureFlagService.toggleFeatureFlag(key, enabled, updatedBy);
        return ApiResponse.success("Feature flag toggled", toggled);
    }

    @PutMapping("/{key}")
    @Operation(summary = "Update feature flag")
    public ApiResponse<FeatureFlagDto> updateFeatureFlag(
            @PathVariable("key") String key,
            @Valid @RequestBody FeatureFlagDto dto,
            Authentication authentication) {
        String updatedBy = authentication.getName();
        FeatureFlagDto updated = featureFlagService.updateFeatureFlag(key, dto, updatedBy);
        return ApiResponse.success("Feature flag updated", updated);
    }

    @DeleteMapping("/{key}")
    @Operation(summary = "Delete feature flag")
    public ApiResponse<Void> deleteFeatureFlag(@PathVariable("key") String key, Authentication authentication) {
        String deletedBy = authentication.getName();
        featureFlagService.deleteFeatureFlag(key, deletedBy);
        return ApiResponse.success("Feature flag deleted", null);
    }
}

