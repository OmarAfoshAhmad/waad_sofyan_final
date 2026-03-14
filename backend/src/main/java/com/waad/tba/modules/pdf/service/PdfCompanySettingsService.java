package com.waad.tba.modules.pdf.service;

import com.waad.tba.modules.pdf.entity.PdfCompanySettings;
import com.waad.tba.modules.pdf.repository.PdfCompanySettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.util.List;

/**
 * PDF Company Settings Service
 * 
 * Business logic for managing PDF company settings.
 * 
 * @since 2026-01-11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfCompanySettingsService {
    
    private final PdfCompanySettingsRepository repository;
    private final JdbcTemplate jdbcTemplate;
    
    /**
     * AUTO-REPAIR: Ensure missing columns exist in the database.
     * This runs on startup to prevent 'column does not exist' errors even if 
     * migrations were skipped or had checksum issues on Windows.
     */
    @PostConstruct
    public void ensureSchemaIntegrity() {
        log.info("[PdfSettingsService] Verifying schema integrity for pdf_company_settings...");
        try {
            String[] columns = {
                "claim_report_title", "claim_report_primary_color", "claim_report_intro",
                "claim_report_footer_note", "claim_report_sig_right_top", "claim_report_sig_right_bottom",
                "claim_report_sig_left_top", "claim_report_sig_left_bottom"
            };
            
            for (String col : columns) {
                String type = "VARCHAR(255)";
                if (col.contains("_intro") || col.contains("_note")) {
                    type = "TEXT";
                } else if (col.contains("_color")) {
                    type = "VARCHAR(7)";
                }
                
                jdbcTemplate.execute("ALTER TABLE pdf_company_settings ADD COLUMN IF NOT EXISTS " + col + " " + type);
            }
            log.info("[PdfSettingsService] Schema integrity check completed successfully.");
        } catch (Exception e) {
            log.error("[PdfSettingsService] Failed to repair schema: {}", e.getMessage());
        }
    }
    
    private static final List<String> ALLOWED_IMAGE_TYPES = List.of(
        "image/png", "image/jpeg", "image/jpg", "image/svg+xml"
    );
    
    private static final long MAX_LOGO_SIZE = 5 * 1024 * 1024; // 5MB
    
    /**
     * Get active PDF settings.
     * If no settings exist, creates and saves default settings to ensure a valid ID exists.
     */
    @Transactional
    public PdfCompanySettings getActiveSettings() {
        return repository.findActiveSettings()
            .orElseGet(() -> {
                log.info("[PdfSettingsService] No active settings found. Creating default settings.");
                PdfCompanySettings defaultSettings = getDefaultSettings();
                defaultSettings.setCreatedBy("SYSTEM");
                defaultSettings.setUpdatedBy("SYSTEM");
                return repository.save(defaultSettings);
            });
    }
    
    /**
     * Get all settings
     */
    @Transactional(readOnly = true)
    public List<PdfCompanySettings> getAllSettings() {
        return repository.findAll();
    }
    
    /**
     * Get settings by ID
     */
    @Transactional(readOnly = true)
    public PdfCompanySettings getSettingsById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new RuntimeException("PDF settings not found: " + id));
    }
    
    /**
     * Create new settings
     */
    @Transactional
    public PdfCompanySettings createSettings(PdfCompanySettings settings, String username) {
        settings.setCreatedBy(username);
        settings.setUpdatedBy(username);
        
        // Deactivate other settings if this one is active
        if (Boolean.TRUE.equals(settings.getIsActive())) {
            deactivateAllSettings();
        }
        
        return repository.save(settings);
    }
    
    /**
     * Update existing settings
     */
    @Transactional
    public PdfCompanySettings updateSettings(Long id, PdfCompanySettings updates, String username) {
        PdfCompanySettings existing = getSettingsById(id);
        
        // Update fields
        if (updates.getCompanyName() != null) {
            existing.setCompanyName(updates.getCompanyName());
        }
        if (updates.getAddress() != null) {
            existing.setAddress(updates.getAddress());
        }
        if (updates.getPhone() != null) {
            existing.setPhone(updates.getPhone());
        }
        if (updates.getEmail() != null) {
            existing.setEmail(updates.getEmail());
        }
        if (updates.getWebsite() != null) {
            existing.setWebsite(updates.getWebsite());
        }
        if (updates.getFooterText() != null) {
            existing.setFooterText(updates.getFooterText());
        }
        if (updates.getFooterTextEn() != null) {
            existing.setFooterTextEn(updates.getFooterTextEn());
        }
        if (updates.getHeaderColor() != null) {
            existing.setHeaderColor(updates.getHeaderColor());
        }
        if (updates.getFooterColor() != null) {
            existing.setFooterColor(updates.getFooterColor());
        }
        if (updates.getPageSize() != null) {
            existing.setPageSize(updates.getPageSize());
        }
        if (updates.getMarginTop() != null) {
            existing.setMarginTop(updates.getMarginTop());
        }
        if (updates.getMarginBottom() != null) {
            existing.setMarginBottom(updates.getMarginBottom());
        }
        if (updates.getMarginLeft() != null) {
            existing.setMarginLeft(updates.getMarginLeft());
        }
        if (updates.getMarginRight() != null) {
            existing.setMarginRight(updates.getMarginRight());
        }

        // Claim Report specialized fields
        if (updates.getClaimReportTitle() != null) {
            existing.setClaimReportTitle(updates.getClaimReportTitle());
        }
        if (updates.getClaimReportPrimaryColor() != null) {
            existing.setClaimReportPrimaryColor(updates.getClaimReportPrimaryColor());
        }
        if (updates.getClaimReportIntro() != null) {
            existing.setClaimReportIntro(updates.getClaimReportIntro());
        }
        if (updates.getClaimReportFooterNote() != null) {
            existing.setClaimReportFooterNote(updates.getClaimReportFooterNote());
        }
        if (updates.getClaimReportSigRightTop() != null) {
            existing.setClaimReportSigRightTop(updates.getClaimReportSigRightTop());
        }
        if (updates.getClaimReportSigRightBottom() != null) {
            existing.setClaimReportSigRightBottom(updates.getClaimReportSigRightBottom());
        }
        if (updates.getClaimReportSigLeftTop() != null) {
            existing.setClaimReportSigLeftTop(updates.getClaimReportSigLeftTop());
        }
        if (updates.getClaimReportSigLeftBottom() != null) {
            existing.setClaimReportSigLeftBottom(updates.getClaimReportSigLeftBottom());
        }
        
        existing.setUpdatedBy(username);
        
        // Handle activation
        if (updates.getIsActive() != null && Boolean.TRUE.equals(updates.getIsActive())) {
            deactivateAllSettings();
            existing.setIsActive(true);
        }
        
        return repository.save(existing);
    }
    
    /**
     * Upload logo file
     */
    @Transactional
    public PdfCompanySettings uploadLogo(Long id, MultipartFile file, String username) throws IOException {
        // Validate file
        validateLogoFile(file);
        
        PdfCompanySettings settings = getSettingsById(id);
        
        // Store logo data
        settings.setLogoData(file.getBytes());
        settings.setUpdatedBy(username);
        
        log.info("[PdfSettingsService] Logo uploaded: {} bytes, type: {}", 
            file.getSize(), file.getContentType());
        
        return repository.save(settings);
    }
    
    /**
     * Delete settings
     */
    @Transactional
    public void deleteSettings(Long id) {
        repository.deleteById(id);
        log.info("[PdfSettingsService] Settings deleted: {}", id);
    }
    
    /**
     * Activate specific settings
     */
    @Transactional
    public PdfCompanySettings activateSettings(Long id, String username) {
        deactivateAllSettings();
        
        PdfCompanySettings settings = getSettingsById(id);
        settings.setIsActive(true);
        settings.setUpdatedBy(username);
        
        return repository.save(settings);
    }
    
    // ========== Private Helpers ==========
    
    private void deactivateAllSettings() {
        List<PdfCompanySettings> allSettings = repository.findAll();
        allSettings.forEach(s -> s.setIsActive(false));
        repository.saveAll(allSettings);
    }
    
    private void validateLogoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Logo file is empty");
        }
        
        if (file.getSize() > MAX_LOGO_SIZE) {
            throw new IllegalArgumentException(
                String.format("Logo file too large: %d bytes (max: %d bytes)", 
                    file.getSize(), MAX_LOGO_SIZE)
            );
        }
        
        String contentType = file.getContentType();
        if (!ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                "Invalid image type: " + contentType + ". Allowed: " + ALLOWED_IMAGE_TYPES
            );
        }
    }
    
    private PdfCompanySettings getDefaultSettings() {
        return PdfCompanySettings.builder()
            .companyName("نظام وعد الطبي")
            .address("الرياض، المملكة العربية السعودية")
            .phone("+966 XX XXX XXXX")
            .email("info@waad-system.com")
            .footerText("جميع الحقوق محفوظة © 2026 - نظام وعد الطبي")
            .footerTextEn("All Rights Reserved © 2026 - Waad Medical System")
            .headerColor("#1976d2")
            .footerColor("#757575")
            .pageSize("A4")
            .marginTop(20)
            .marginBottom(20)
            .marginLeft(20)
            .marginRight(20)
            .isActive(true)
            .claimReportTitle("نظام وعد الطبي")
            .claimReportPrimaryColor("#005f6b")
            .claimReportIntro("نحيطكم علماً بأننا قد انتهينا من مراجعة المطالبات المالية المقدمة من طرفكم والمشار إليها في الدفعة رقم ({batchCode})، وقد تمت المراجعة الفنية والمالية وفق المعايير المعتمدة، وكانت النتائج كالتالي:")
            .claimReportFooterNote("يرجى التكرم بمراجعة التفاصيل والملاحظات المرفقة، وفي حال وجود أي اعتراض يرجى مراسلتنا في غضون أسبوعين من تاريخه.")
            .claimReportSigRightTop("والسلام عليكم")
            .claimReportSigRightBottom("قسم المراجعة والتدقيق")
            .claimReportSigLeftTop("")
            .claimReportSigLeftBottom("إدارة الحسابات")
            .build();
    }
}
