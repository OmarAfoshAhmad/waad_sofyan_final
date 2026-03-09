package com.waad.tba.modules.medicaltaxonomy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MedicalServiceLookupDto - Canonical Lookup DTO for Medical Service Selection
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * ARCHITECTURAL LAW (NON-NEGOTIABLE)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * MedicalService MUST always be represented as:
 *   CODE + NAME + CATEGORY
 * 
 * Anywhere a service is selectable or displayed.
 * 
 * NO EXCEPTIONS.
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Used in:
 * - Provider Contract form (Pricing Item selector)
 * - Benefit Policy Rule form (Service selector)
 * - Provider Portal (Claim / PreAuth service lines)
 * 
 * Display format:
 *   [SVC-001] أشعة مقطعية CT Scan
 *   🗂 التصنيف: الأشعة التشخيصية
 * 
 * Searchable by:
 * - code
 * - name
 * - categoryName
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalServiceLookupDto {

    /**
     * Service ID - the ONLY value to be persisted
     */
    private Long id;

    /**
     * Service code (business identifier)
     * Format: "SRV-CARDIO-001", "SRV-LAB-CBC"
     */
    private String code;

    /**
     * Service name (unified - Arabic-only system)
     */
    private String name;

    /**
     * Arabic display name
     */
    private String nameAr;

    /**
     * English display name
     */
    private String nameEn;

    /**
     * Category ID (for filtering)
     */
    private Long categoryId;

    /**
     * Category name (unified field)
     */
    private String categoryName;

    /**
     * Category Arabic name
     */
    private String categoryNameAr;

    /**
     * Category English name
     */
    private String categoryNameEn;

    /**
     * Display label combining code and name
     * Format: "[SVC-001] أشعة مقطعية CT Scan"
     */
    public String getDisplayLabel() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(code).append("] ");
        if (nameAr != null && !nameAr.isBlank()) {
            sb.append(nameAr);
        } else if (name != null) {
            sb.append(name);
        }
        
        if (nameEn != null && !nameEn.isBlank()) {
            sb.append(" ").append(nameEn);
        }
        return sb.toString();
    }

    /**
     * Full display with category
     */
    public String getFullDisplayLabel() {
        String cat = categoryNameAr != null ? categoryNameAr : (categoryName != null ? categoryName : "غير مصنف");
        return String.format("%s - %s", getDisplayLabel(), cat);
    }
}
