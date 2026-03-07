package com.waad.tba.common.service;

import com.waad.tba.common.entity.SystemSetting;
import com.waad.tba.common.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * System Settings Service.
 * 
 * Manages configurable system-wide settings.
 * Uses caching for performance (settings are read frequently).
 * 
 * @since Phase 1 - SLA Implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@SuppressWarnings("null")
public class SystemSettingsService {

    private final SystemSettingRepository settingRepository;

    // Default SLA values
    public static final String CLAIM_SLA_DAYS_KEY = "CLAIM_SLA_DAYS";
    public static final int DEFAULT_CLAIM_SLA_DAYS = 10;

    public static final String PRE_APPROVAL_SLA_DAYS_KEY = "PRE_APPROVAL_SLA_DAYS";
    public static final int DEFAULT_PRE_APPROVAL_SLA_DAYS = 3;

    // ── UI / Appearance ────────────────────────────────────────────────────────
    public static final String LOGO_URL_KEY = "LOGO_URL";
    public static final String FONT_FAMILY_KEY = "FONT_FAMILY";
    public static final String FONT_SIZE_BASE_KEY = "FONT_SIZE_BASE";
    public static final String SYSTEM_NAME_AR_KEY = "SYSTEM_NAME_AR";
    public static final String SYSTEM_NAME_EN_KEY = "SYSTEM_NAME_EN";

    // ── Member Numbering ───────────────────────────────────────────────────────
    public static final String BENEFICIARY_NUMBER_FORMAT_KEY = "BENEFICIARY_NUMBER_FORMAT";
    public static final String BENEFICIARY_NUMBER_PREFIX_KEY = "BENEFICIARY_NUMBER_PREFIX";
    public static final String BENEFICIARY_NUMBER_DIGITS_KEY = "BENEFICIARY_NUMBER_DIGITS";

    // ── Eligibility Rules ──────────────────────────────────────────────────────
    public static final String ELIGIBILITY_STRICT_MODE_KEY = "ELIGIBILITY_STRICT_MODE";
    public static final String WAITING_PERIOD_DAYS_DEFAULT_KEY = "WAITING_PERIOD_DAYS_DEFAULT";
    public static final String ELIGIBILITY_GRACE_PERIOD_DAYS_KEY = "ELIGIBILITY_GRACE_PERIOD_DAYS";

    // ── Auth / Password Reset ────────────────────────────────────────────────
    public static final String PASSWORD_RESET_METHOD_KEY = "PASSWORD_RESET_METHOD"; // TOKEN | OTP
    public static final String PASSWORD_RESET_TOKEN_EXPIRY_MINUTES_KEY = "PASSWORD_RESET_TOKEN_EXPIRY_MINUTES";
    public static final String PASSWORD_RESET_OTP_EXPIRY_MINUTES_KEY = "PASSWORD_RESET_OTP_EXPIRY_MINUTES";
    public static final String PASSWORD_RESET_OTP_LENGTH_KEY = "PASSWORD_RESET_OTP_LENGTH";

    /**
     * Initialize default settings on application startup.
     */
    @PostConstruct
    @Transactional
    public void initializeDefaultSettings() {
        log.info("🔧 Initializing system settings...");

        // Create CLAIM_SLA_DAYS if not exists
        if (settingRepository.findBySettingKey(CLAIM_SLA_DAYS_KEY).isEmpty()) {
            SystemSetting slaSetting = SystemSetting.builder()
                    .settingKey(CLAIM_SLA_DAYS_KEY)
                    .settingValue(String.valueOf(DEFAULT_CLAIM_SLA_DAYS))
                    .valueType(SystemSetting.SettingValueType.INTEGER)
                    .description("Number of business days allowed for claim processing (SLA)")
                    .category("CLAIMS")
                    .isEditable(true)
                    .defaultValue(String.valueOf(DEFAULT_CLAIM_SLA_DAYS))
                    .validationRules("min:1,max:30")
                    .active(true)
                    .build();

            settingRepository.save(slaSetting);
            log.info("✅ Created default setting: {} = {}", CLAIM_SLA_DAYS_KEY, DEFAULT_CLAIM_SLA_DAYS);
        }

        // ✅ PHASE 1: Create PRE_APPROVAL_SLA_DAYS if not exists
        if (settingRepository.findBySettingKey(PRE_APPROVAL_SLA_DAYS_KEY).isEmpty()) {
            SystemSetting preApprovalSlaSetting = SystemSetting.builder()
                    .settingKey(PRE_APPROVAL_SLA_DAYS_KEY)
                    .settingValue(String.valueOf(DEFAULT_PRE_APPROVAL_SLA_DAYS))
                    .valueType(SystemSetting.SettingValueType.INTEGER)
                    .description("Number of business days allowed for pre-approval processing (SLA)")
                    .category("PRE_APPROVALS")
                    .isEditable(true)
                    .defaultValue(String.valueOf(DEFAULT_PRE_APPROVAL_SLA_DAYS))
                    .validationRules("min:1,max:10")
                    .active(true)
                    .build();

            settingRepository.save(preApprovalSlaSetting);
            log.info("✅ Created default setting: {} = {}", PRE_APPROVAL_SLA_DAYS_KEY, DEFAULT_PRE_APPROVAL_SLA_DAYS);
        }

        // Auth: Password reset method (default TOKEN for better security)
        if (settingRepository.findBySettingKey(PASSWORD_RESET_METHOD_KEY).isEmpty()) {
            settingRepository.save(SystemSetting.builder()
                    .settingKey(PASSWORD_RESET_METHOD_KEY)
                    .settingValue("TOKEN")
                    .valueType(SystemSetting.SettingValueType.STRING)
                    .description("Password reset method. Allowed values: TOKEN or OTP")
                    .category("SECURITY")
                    .isEditable(true)
                    .defaultValue("TOKEN")
                    .validationRules("enum:TOKEN|OTP")
                    .active(true)
                    .build());
            log.info("✅ Created default setting: {} = TOKEN", PASSWORD_RESET_METHOD_KEY);
        }

        // Auth: Token expiry in minutes
        if (settingRepository.findBySettingKey(PASSWORD_RESET_TOKEN_EXPIRY_MINUTES_KEY).isEmpty()) {
            settingRepository.save(SystemSetting.builder()
                    .settingKey(PASSWORD_RESET_TOKEN_EXPIRY_MINUTES_KEY)
                    .settingValue("60")
                    .valueType(SystemSetting.SettingValueType.INTEGER)
                    .description("Password reset token validity in minutes")
                    .category("SECURITY")
                    .isEditable(true)
                    .defaultValue("60")
                    .validationRules("min:5,max:1440")
                    .active(true)
                    .build());
            log.info("✅ Created default setting: {} = 60", PASSWORD_RESET_TOKEN_EXPIRY_MINUTES_KEY);
        }

        // Auth: OTP expiry in minutes
        if (settingRepository.findBySettingKey(PASSWORD_RESET_OTP_EXPIRY_MINUTES_KEY).isEmpty()) {
            settingRepository.save(SystemSetting.builder()
                    .settingKey(PASSWORD_RESET_OTP_EXPIRY_MINUTES_KEY)
                    .settingValue("10")
                    .valueType(SystemSetting.SettingValueType.INTEGER)
                    .description("Password reset OTP validity in minutes")
                    .category("SECURITY")
                    .isEditable(true)
                    .defaultValue("10")
                    .validationRules("min:1,max:60")
                    .active(true)
                    .build());
            log.info("✅ Created default setting: {} = 10", PASSWORD_RESET_OTP_EXPIRY_MINUTES_KEY);
        }

        // Auth: OTP length
        if (settingRepository.findBySettingKey(PASSWORD_RESET_OTP_LENGTH_KEY).isEmpty()) {
            settingRepository.save(SystemSetting.builder()
                    .settingKey(PASSWORD_RESET_OTP_LENGTH_KEY)
                    .settingValue("6")
                    .valueType(SystemSetting.SettingValueType.INTEGER)
                    .description("Password reset OTP code length")
                    .category("SECURITY")
                    .isEditable(true)
                    .defaultValue("6")
                    .validationRules("min:4,max:10")
                    .active(true)
                    .build());
            log.info("✅ Created default setting: {} = 6", PASSWORD_RESET_OTP_LENGTH_KEY);
        }
    }

    /**
     * Get setting value as string.
     * 
     * @param key          Setting key
     * @param defaultValue Default value if setting not found
     * @return Setting value or default
     */
    @Cacheable(value = "systemSettings", key = "#key")
    public String getSetting(String key, String defaultValue) {
        return settingRepository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
                .orElseGet(() -> {
                    log.warn("⚠️ Setting {} not found, using default: {}", key, defaultValue);
                    return defaultValue;
                });
    }

    /**
     * Get setting value as integer.
     */
    @Cacheable(value = "systemSettings", key = "#key")
    public Integer getSettingAsInt(String key, Integer defaultValue) {
        return settingRepository.findBySettingKey(key)
                .map(setting -> {
                    try {
                        return Integer.parseInt(setting.getSettingValue());
                    } catch (NumberFormatException e) {
                        log.error("❌ Invalid integer value for setting {}: {}", key, setting.getSettingValue());
                        return defaultValue;
                    }
                })
                .orElseGet(() -> {
                    log.warn("⚠️ Setting {} not found, using default: {}", key, defaultValue);
                    return defaultValue;
                });
    }

    /**
     * Get claim SLA days (cached for performance).
     */
    public int getClaimSlaDays() {
        return getSettingAsInt(CLAIM_SLA_DAYS_KEY, DEFAULT_CLAIM_SLA_DAYS);
    }

    /**
     * ✅ PHASE 1: Get pre-approval SLA days (cached for performance).
     */
    public int getPreApprovalSlaDays() {
        return getSettingAsInt(PRE_APPROVAL_SLA_DAYS_KEY, DEFAULT_PRE_APPROVAL_SLA_DAYS);
    }

    /**
     * Update a setting value.
     * Evicts cache to force reload.
     * 
     * @param key       Setting key
     * @param value     New value
     * @param updatedBy Username of who made the change
     */
    @Transactional
    @CacheEvict(value = "systemSettings", key = "#key")
    public void updateSetting(String key, String value, String updatedBy) {
        SystemSetting setting = settingRepository.findBySettingKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Setting not found: " + key));

        if (!setting.getIsEditable()) {
            throw new IllegalStateException("Setting " + key + " is not editable");
        }

        String oldValue = setting.getSettingValue();
        setting.setSettingValue(value);
        setting.setUpdatedBy(updatedBy);

        settingRepository.save(setting);

        log.info("⚙️ Setting {} updated by {}: {} → {}", key, updatedBy, oldValue, value);
    }

    /**
     * Update claim SLA days.
     * 
     * @param slaDays   New SLA days value
     * @param updatedBy Username
     */
    @Transactional
    @CacheEvict(value = "systemSettings", key = "'" + CLAIM_SLA_DAYS_KEY + "'")
    public void updateClaimSlaDays(int slaDays, String updatedBy) {
        if (slaDays < 1 || slaDays > 30) {
            throw new IllegalArgumentException("SLA days must be between 1 and 30");
        }

        updateSetting(CLAIM_SLA_DAYS_KEY, String.valueOf(slaDays), updatedBy);
        log.info("📅 Claim SLA days updated to {} by {}", slaDays, updatedBy);
    }

    /**
     * Get all settings in a category.
     */
    public List<SystemSetting> getSettingsByCategory(String category) {
        return settingRepository.findByCategory(category);
    }

    /**
     * Get all editable settings (for admin panel).
     */
    public List<SystemSetting> getEditableSettings() {
        return settingRepository.findEditableSettings();
    }

    /**
     * Reset a setting to its default value.
     */
    @Transactional
    @CacheEvict(value = "systemSettings", key = "#key")
    public void resetToDefault(String key, String updatedBy) {
        SystemSetting setting = settingRepository.findBySettingKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Setting not found: " + key));

        if (setting.getDefaultValue() == null) {
            throw new IllegalStateException("Setting " + key + " has no default value");
        }

        String oldValue = setting.getSettingValue();
        setting.setSettingValue(setting.getDefaultValue());
        setting.setUpdatedBy(updatedBy);

        settingRepository.save(setting);

        log.info("🔄 Setting {} reset to default by {}: {} → {}",
                key, updatedBy, oldValue, setting.getDefaultValue());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UI / Appearance getters
    // ═══════════════════════════════════════════════════════════════════════════

    public String getLogoUrl() {
        return getSetting(LOGO_URL_KEY, "");
    }

    public String getFontFamily() {
        return getSetting(FONT_FAMILY_KEY, "Tajawal");
    }

    public int getFontSizeBase() {
        return getSettingAsInt(FONT_SIZE_BASE_KEY, 14);
    }

    public String getSystemNameAr() {
        return getSetting(SYSTEM_NAME_AR_KEY, "نظام واعد الطبي");
    }

    public String getSystemNameEn() {
        return getSetting(SYSTEM_NAME_EN_KEY, "TBA WAAD System");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Member Numbering getters
    // ═══════════════════════════════════════════════════════════════════════════

    public String getBeneficiaryNumberFormat() {
        return getSetting(BENEFICIARY_NUMBER_FORMAT_KEY, "PREFIX_SEQUENCE");
    }

    public String getBeneficiaryNumberPrefix() {
        return getSetting(BENEFICIARY_NUMBER_PREFIX_KEY, "MEM");
    }

    public int getBeneficiaryNumberDigits() {
        return getSettingAsInt(BENEFICIARY_NUMBER_DIGITS_KEY, 6);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Eligibility getters
    // ═══════════════════════════════════════════════════════════════════════════

    public boolean isEligibilityStrictMode() {
        return Boolean.parseBoolean(getSetting(ELIGIBILITY_STRICT_MODE_KEY, "false"));
    }

    public int getWaitingPeriodDaysDefault() {
        return getSettingAsInt(WAITING_PERIOD_DAYS_DEFAULT_KEY, 30);
    }

    public int getEligibilityGracePeriodDays() {
        return getSettingAsInt(ELIGIBILITY_GRACE_PERIOD_DAYS_KEY, 7);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Auth / Password Reset getters
    // ═══════════════════════════════════════════════════════════════════════════

    public String getPasswordResetMethod() {
        String method = getSetting(PASSWORD_RESET_METHOD_KEY, "TOKEN");
        if (method == null) {
            return "TOKEN";
        }

        String normalized = method.trim().toUpperCase();
        return ("OTP".equals(normalized) || "TOKEN".equals(normalized)) ? normalized : "TOKEN";
    }

    public boolean isOtpPasswordResetEnabled() {
        return "OTP".equals(getPasswordResetMethod());
    }

    public int getPasswordResetTokenExpiryMinutes() {
        return getSettingAsInt(PASSWORD_RESET_TOKEN_EXPIRY_MINUTES_KEY, 60);
    }

    public int getPasswordResetOtpExpiryMinutes() {
        return getSettingAsInt(PASSWORD_RESET_OTP_EXPIRY_MINUTES_KEY, 10);
    }

    public int getPasswordResetOtpLength() {
        return getSettingAsInt(PASSWORD_RESET_OTP_LENGTH_KEY, 6);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Composite DTO — served to frontend on app load (no auth required)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns all UI-relevant settings in one call.
     * Cached individually; this method assembles from cached values.
     */
    public UiConfigDto getUiConfig() {
        return new UiConfigDto(
                getLogoUrl(),
                getFontFamily(),
                getFontSizeBase(),
                getSystemNameAr(),
                getSystemNameEn());
    }

    public record UiConfigDto(
            String logoUrl,
            String fontFamily,
            int fontSizeBase,
            String systemNameAr,
            String systemNameEn) {
    }
}
