package com.waad.tba.modules.pdf.controller;

import com.waad.tba.modules.pdf.entity.PdfCompanySettings;
import com.waad.tba.modules.pdf.service.PdfCompanySettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * PDF Company Settings Controller
 * 
 * REST API for managing PDF company settings.
 * 
 * @since 2026-01-11
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/pdf/settings")
@RequiredArgsConstructor
public class PdfCompanySettingsController {
    
    private final PdfCompanySettingsService service;
    
    /**
     * Get active PDF settings
     */
    @GetMapping("/active")
    public ResponseEntity<PdfCompanySettings> getActiveSettings() {
        log.info("[PdfSettingsController] Getting active PDF settings");
        PdfCompanySettings settings = service.getActiveSettings();
        return ResponseEntity.ok(settings);
    }
    
    /**
     * Get all PDF settings
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<PdfCompanySettings>> getAllSettings() {
        log.info("[PdfSettingsController] Getting all PDF settings");
        List<PdfCompanySettings> settings = service.getAllSettings();
        return ResponseEntity.ok(settings);
    }
    
    /**
     * Get specific PDF settings by ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PdfCompanySettings> getSettingsById(@PathVariable("id") Long id) {
        log.info("[PdfSettingsController] Getting PDF settings: {}", id);
        PdfCompanySettings settings = service.getSettingsById(id);
        return ResponseEntity.ok(settings);
    }
    
    /**
     * Create new PDF settings
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PdfCompanySettings> createSettings(
            @RequestBody PdfCompanySettings settings,
            Principal principal
    ) {
        log.info("[PdfSettingsController] Creating PDF settings");
        PdfCompanySettings created = service.createSettings(settings, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
    
    /**
     * Update existing PDF settings
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PdfCompanySettings> updateSettings(
            @PathVariable("id") Long id,
            @RequestBody PdfCompanySettings updates,
            Principal principal
    ) {
        log.info("[PdfSettingsController] Updating PDF settings: {}", id);
        PdfCompanySettings updated = service.updateSettings(id, updates, principal.getName());
        return ResponseEntity.ok(updated);
    }
    
    /**
     * Upload company logo
     */
    @PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> uploadLogo(
            @PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file,
            Principal principal
    ) {
        log.info("[PdfSettingsController] Uploading logo for settings: {}", id);
        
        try {
            PdfCompanySettings updated = service.uploadLogo(id, file, principal.getName());
            return ResponseEntity.ok(Map.of(
                "message", "Logo uploaded successfully",
                "size", file.getSize(),
                "contentType", file.getContentType(),
                "settings", updated
            ));
        } catch (IllegalArgumentException e) {
            log.warn("[PdfSettingsController] Invalid logo file: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid file",
                "message", e.getMessage()
            ));
        } catch (IOException e) {
            log.error("[PdfSettingsController] Failed to upload logo", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Upload failed",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * Activate specific settings
     */
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PdfCompanySettings> activateSettings(
            @PathVariable("id") Long id,
            Principal principal
    ) {
        log.info("[PdfSettingsController] Activating PDF settings: {}", id);
        PdfCompanySettings activated = service.activateSettings(id, principal.getName());
        return ResponseEntity.ok(activated);
    }
    
    /**
     * Delete PDF settings
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteSettings(@PathVariable("id") Long id) {
        log.info("[PdfSettingsController] Deleting PDF settings: {}", id);
        service.deleteSettings(id);
        return ResponseEntity.noContent().build();
    }
}

