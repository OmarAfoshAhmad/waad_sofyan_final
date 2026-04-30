package com.waad.tba.common.controller;

import com.waad.tba.common.entity.SystemSetting;
import com.waad.tba.common.repository.SystemSettingRepository;
import com.waad.tba.common.service.SystemSettingsService;
import com.waad.tba.modules.claim.service.SlaMonitoringScheduler;
import com.waad.tba.security.AuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * System Settings Admin Controller.
 * 
 * Provides endpoints for managing system-wide configurable settings.
 * Restricted to SUPER_ADMIN and INSURANCE_ADMIN roles.
 * 
 * @since Phase 1 - SLA Implementation
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/system-settings")
@RequiredArgsConstructor
@Tag(name = "Admin - System Settings", description = "Manage system-wide settings (SLA, configurations)")
@PreAuthorize("isAuthenticated()")
public class SystemSettingsController {

    private final SystemSettingsService systemSettingsService;
    private final SystemSettingRepository settingRepository;
    private final SlaMonitoringScheduler slaMonitoringScheduler;
    private final AuthorizationService authorizationService;

    /**
     * Get UI configuration (public — no authentication required).
     * Returns only non-sensitive UI settings: logo, font, system name.
     * Called once on app load by the frontend.
     *
     * GET /api/v1/admin/system-settings/ui-config
     */
    @GetMapping("/ui-config")
    @Operation(summary = "Get UI configuration (public)")
    public ResponseEntity<SystemSettingsService.UiConfigDto> getUiConfig() {
        return ResponseEntity.ok(systemSettingsService.getUiConfig());
    }

    /**
     * Get all editable system settings.
     * 
     * GET /api/admin/system-settings
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get all editable system settings")
    public ResponseEntity<List<SystemSetting>> getAllSettings() {
        List<SystemSetting> settings = systemSettingsService.getEditableSettings();
        return ResponseEntity.ok(settings);
    }

    /**
     * Get settings by category.
     * 
     * GET /api/admin/system-settings/category/{category}
     */
    @GetMapping("/category/{category}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get settings by category")
    public ResponseEntity<List<SystemSetting>> getSettingsByCategory(@PathVariable("category") String category) {
        log.info("📋 Getting settings for category: {}", category);

        List<SystemSetting> settings = systemSettingsService.getSettingsByCategory(category);
        return ResponseEntity.ok(settings);
    }

    /**
     * Get current claim SLA days.
     * 
     * GET /api/admin/system-settings/claim-sla-days
     */
    @GetMapping("/claim-sla-days")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get current claim SLA days")
    public ResponseEntity<ClaimSlaDaysResponse> getClaimSlaDays() {
        int slaDays = systemSettingsService.getClaimSlaDays();

        return ResponseEntity.ok(new ClaimSlaDaysResponse(
                slaDays,
                "Claims must be processed within " + slaDays + " business days"));
    }

    /**
     * Update claim SLA days.
     * 
     * PUT /api/admin/system-settings/claim-sla-days
     * 
     * Body: { "slaDays": 7 }
     * 
     * Notes:
     * - Only affects NEW claims submitted after this change
     * - Existing claims retain their original SLA value
     * - Value must be between 1 and 30 days
     */
    @PutMapping("/claim-sla-days")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update claim SLA days (affects new claims only)")
    public ResponseEntity<UpdateSlaDaysResponse> updateClaimSlaDays(
            @RequestBody UpdateSlaDaysRequest request) {

        String username = authorizationService.getCurrentUser() != null
                ? authorizationService.getCurrentUser().getUsername()
                : "SYSTEM";

        int oldValue = systemSettingsService.getClaimSlaDays();

        systemSettingsService.updateClaimSlaDays(request.slaDays(), username);
        return ResponseEntity.ok(new UpdateSlaDaysResponse(
                oldValue,
                request.slaDays(),
                "SLA days updated successfully. New claims will use " + request.slaDays() + " business days.",
                username));
    }

    /**
     * Reset claim SLA days to default (10 days).
     * 
     * POST /api/admin/system-settings/claim-sla-days/reset
     */
    @PostMapping("/claim-sla-days/reset")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Reset claim SLA days to default (10 days)")
    public ResponseEntity<UpdateSlaDaysResponse> resetClaimSlaDays() {
        String username = authorizationService.getCurrentUser() != null
                ? authorizationService.getCurrentUser().getUsername()
                : "SYSTEM";

        int oldValue = systemSettingsService.getClaimSlaDays();

        systemSettingsService.resetToDefault(SystemSettingsService.CLAIM_SLA_DAYS_KEY, username);

        int newValue = systemSettingsService.getClaimSlaDays();

        log.info("🔄 Claim SLA days reset: {} → {} (default) by {}", oldValue, newValue, username);

        return ResponseEntity.ok(new UpdateSlaDaysResponse(
                oldValue,
                newValue,
                "SLA days reset to default value",
                username));
    }

    /**
     * Get SLA compliance report.
     * 
     * GET /api/admin/system-settings/sla-compliance-report
     */
    @GetMapping("/sla-compliance-report")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get SLA compliance report")
    public ResponseEntity<SlaMonitoringScheduler.SlaComplianceReport> getSlaComplianceReport() {
        log.info("📊 Generating SLA compliance report");

        SlaMonitoringScheduler.SlaComplianceReport report = slaMonitoringScheduler.generateComplianceReport();

        return ResponseEntity.ok(report);
    }

    /**
     * Update a specific setting by key.
     * 
     * PUT /api/admin/system-settings/{key}
     */
    @PutMapping("/{key}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update a specific setting by key")
    public ResponseEntity<SystemSetting> updateSettingByKey(
            @PathVariable("key") String key,
            @RequestBody UpdateSettingRequest request) {

        String username = authorizationService.getCurrentUser() != null
                ? authorizationService.getCurrentUser().getUsername()
                : "SYSTEM";

        log.info("⚙️ Updating setting {} by {}", key, username);

        systemSettingsService.updateSetting(key, request.value(), username);

        return settingRepository.findBySettingKey(key)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DTOs
    // ═══════════════════════════════════════════════════════════════════════════

    public record ClaimSlaDaysResponse(
            int slaDays,
            String description) {
    }

    public record UpdateSlaDaysRequest(
            int slaDays) {
        public UpdateSlaDaysRequest {
            if (slaDays < 1 || slaDays > 30) {
                throw new IllegalArgumentException("SLA days must be between 1 and 30");
            }
        }
    }

    public record UpdateSlaDaysResponse(
            int oldValue,
            int newValue,
            String message,
            String updatedBy) {
    }

    public record UpdateSettingRequest(
            String value) {
    }
}

