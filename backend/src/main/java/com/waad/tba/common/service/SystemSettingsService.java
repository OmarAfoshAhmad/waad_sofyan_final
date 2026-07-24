package com.waad.tba.common.service;

import com.waad.tba.common.dto.SystemSettingDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * System Settings Service - Facade
 *
 * Delegates to specialized settings services:
 * - SettingsInitializationService: initialize defaults
 * - SettingsManagementService: CRUD and validation
 * - SLASettingsService: SLA-related settings
 * - AuthenticationSettingsService: auth/password settings
 * - UIConfigService: UI and eligibility settings
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemSettingsService {

    private final SettingsInitializationService initializationService;
    private final SettingsManagementService managementService;
    private final SLASettingsService slaService;
    private final AuthenticationSettingsService authService;
    private final UIConfigService uiService;

    // ── SLA ────────────────────────────────────────────────────────────
    public int getClaimSlaDays() {
        return slaService.getClaimSlaDays();
    }

    public int getPreApprovalSlaDays() {
        return slaService.getPreApprovalSlaDays();
    }

    public int getClaimBackdatedMonths() {
        return slaService.getClaimBackdatedMonths();
    }

    // ── Authentication / Password Reset ────────────────────────────────
    public String getPasswordResetMethod() {
        return authService.getPasswordResetMethod();
    }

    public boolean isOtpPasswordResetEnabled() {
        return authService.isOtpPasswordResetEnabled();
    }

    public int getPasswordResetTokenExpiryMinutes() {
        return authService.getPasswordResetTokenExpiryMinutes();
    }

    public int getPasswordResetOtpExpiryMinutes() {
        return authService.getPasswordResetOtpExpiryMinutes();
    }

    public int getPasswordResetOtpLength() {
        return authService.getPasswordResetOtpLength();
    }

    // ── UI / Appearance ───────────────────────────────────────────────
    public String getLogoUrl() {
        return uiService.getLogoUrl();
    }

    public String getFontFamily() {
        return uiService.getFontFamily();
    }

    public int getFontSizeBase() {
        return uiService.getFontSizeBase();
    }

    public String getSystemNameAr() {
        return uiService.getSystemNameAr();
    }

    public String getSystemNameEn() {
        return uiService.getSystemNameEn();
    }

    // ── Member Numbering ──────────────────────────────────────────────
    public String getBeneficiaryNumberFormat() {
        return uiService.getBeneficiaryNumberFormat();
    }

    public String getBeneficiaryNumberPrefix() {
        return uiService.getBeneficiaryNumberPrefix();
    }

    public int getBeneficiaryNumberDigits() {
        return uiService.getBeneficiaryNumberDigits();
    }

    // ── Eligibility ───────────────────────────────────────────────────
    public boolean isEligibilityStrictMode() {
        return uiService.isEligibilityStrictMode();
    }

    public int getWaitingPeriodDaysDefault() {
        return uiService.getWaitingPeriodDaysDefault();
    }

    public int getEligibilityGracePeriodDays() {
        return uiService.getEligibilityGracePeriodDays();
    }

    // ── AI / BioBERT ──────────────────────────────────────────────────
    public String getBiobertApiUrl() {
        return uiService.getBiobertApiUrl();
    }

    // ── Management ────────────────────────────────────────────────────
    @Transactional
    public SystemSettingDto updateSetting(String key, String value, String updatedBy) {
        return managementService.updateSetting(key, value, updatedBy);
    }

    @Transactional
    public void updateClaimSlaDays(int slaDays, String updatedBy) {
        managementService.updateClaimSlaDays(slaDays, updatedBy);
    }

    public List<SystemSettingDto> getSettingsByCategory(String category) {
        return managementService.getSettingsByCategory(category);
    }

    public List<SystemSettingDto> getEditableSettings() {
        return managementService.getEditableSettings();
    }

    @Transactional
    public void resetToDefault(String key, String updatedBy) {
        managementService.resetToDefault(key, updatedBy);
    }

    // ── UI Config DTO ─────────────────────────────────────────────────
    public UIConfigService.UiConfigDto getUiConfig() {
        return uiService.getUiConfig();
    }
}
