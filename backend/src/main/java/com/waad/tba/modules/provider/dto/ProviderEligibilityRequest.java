package com.waad.tba.modules.provider.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Provider Eligibility Check Request.
 * 
 * الفحص يتم فقط بـ:
 * 1. الباركود (WAD-2026-00001234) - الطريقة الرئيسية
 * 2. رقم البطاقة (Card Number) - بديل
 * 
 * ملاحظة مهمة: الرقم الوطني لا يُستخدم للفحص
 * يظهر فقط كمعلومات أساسية للمؤمن عليه بعد الفحص
 * 
 * @since Phase 1 - Provider Portal
 */
@Schema(description = "Provider eligibility check request - barcode or card number required")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderEligibilityRequest {
    
    /**
     * Member barcode (WAD-2026-XXXXXXXX) or Card Number.
     * This is the ONLY identifier used for eligibility checks.
     * 
     * يقبل:
     * - الباركود: WAD-2026-00001234
     * - رقم البطاقة: أرقام فقط
     */
    @Schema(
        description = "Member barcode (WAD-2026-XXXXXXXX) or card number - REQUIRED for eligibility check", 
        example = "WAD-2026-00001234",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "الباركود أو رقم البطاقة مطلوب / Barcode or card number is required")
    @Size(max = 50, message = "الباركود يجب ألا يتجاوز 50 حرفاً / Barcode must not exceed 50 characters")
    private String barcode;
    
    @Schema(description = "Member ID for exact matching when selected from autocomplete list")
    private Long memberId;
    
    /**
     * Service Date - Date of planned service (optional).
     * Defaults to today if not provided.
     * Used for waiting period checks.
     */
    @Schema(
        description = "Service date (ISO format) - defaults to today", 
        example = "2026-01-13"
    )
    @Pattern(
        regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$",
        message = "تاريخ الخدمة يجب أن يكون بصيغة YYYY-MM-DD / Service date must be in YYYY-MM-DD format"
    )
    private String serviceDate;
    
    /**
     * Validation: barcode must be provided.
     * 
     * @return true if barcode is provided and not blank
     */
    public boolean isValid() {
        return hasBarcode();
    }
    
    /**
     * Check if barcode is provided (not null and not blank).
     */
    public boolean hasBarcode() {
        return barcode != null && !barcode.isBlank();
    }
    
    /**
     * Get the lookup key (barcode or card number).
     * 
     * @return barcode value
     */
    public String getLookupKey() {
        return barcode;
    }
}
