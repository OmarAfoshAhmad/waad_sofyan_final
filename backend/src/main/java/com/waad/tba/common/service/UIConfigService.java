package com.waad.tba.common.service;

import com.waad.tba.common.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UIConfigService {

    private final SystemSettingRepository settingRepository;

    public static final String LOGO_URL_KEY = "LOGO_URL";
    public static final String FONT_FAMILY_KEY = "FONT_FAMILY";
    public static final String FONT_SIZE_BASE_KEY = "FONT_SIZE_BASE";
    public static final String SYSTEM_NAME_AR_KEY = "SYSTEM_NAME_AR";
    public static final String SYSTEM_NAME_EN_KEY = "SYSTEM_NAME_EN";

    public static final String BENEFICIARY_NUMBER_FORMAT_KEY = "BENEFICIARY_NUMBER_FORMAT";
    public static final String BENEFICIARY_NUMBER_PREFIX_KEY = "BENEFICIARY_NUMBER_PREFIX";
    public static final String BENEFICIARY_NUMBER_DIGITS_KEY = "BENEFICIARY_NUMBER_DIGITS";

    public static final String ELIGIBILITY_STRICT_MODE_KEY = "ELIGIBILITY_STRICT_MODE";
    public static final String WAITING_PERIOD_DAYS_DEFAULT_KEY = "WAITING_PERIOD_DAYS_DEFAULT";
    public static final String ELIGIBILITY_GRACE_PERIOD_DAYS_KEY = "ELIGIBILITY_GRACE_PERIOD_DAYS";

    public static final String BIOBERT_API_URL = "BIOBERT_API_URL";
    public static final String DEFAULT_BIOBERT_API_URL = "http://localhost:8000/predict";

    @Cacheable(value = "systemSettings", key = "#key")
    public String getSetting(String key, String defaultValue) {
        return settingRepository.findBySettingKey(key)
                .map(com.waad.tba.common.entity.SystemSetting::getSettingValue)
                .orElseGet(() -> {
                    log.warn("⚠️ Setting {} not found, using default: {}", key, defaultValue);
                    return defaultValue;
                });
    }

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

    // UI / Appearance
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

    // Member Numbering
    public String getBeneficiaryNumberFormat() {
        return getSetting(BENEFICIARY_NUMBER_FORMAT_KEY, "PREFIX_SEQUENCE");
    }

    public String getBeneficiaryNumberPrefix() {
        return getSetting(BENEFICIARY_NUMBER_PREFIX_KEY, "MEM");
    }

    public int getBeneficiaryNumberDigits() {
        return getSettingAsInt(BENEFICIARY_NUMBER_DIGITS_KEY, 6);
    }

    // Eligibility
    public boolean isEligibilityStrictMode() {
        return Boolean.parseBoolean(getSetting(ELIGIBILITY_STRICT_MODE_KEY, "false"));
    }

    public int getWaitingPeriodDaysDefault() {
        return getSettingAsInt(WAITING_PERIOD_DAYS_DEFAULT_KEY, 30);
    }

    public int getEligibilityGracePeriodDays() {
        return getSettingAsInt(ELIGIBILITY_GRACE_PERIOD_DAYS_KEY, 7);
    }

    // AI / BioBERT
    public String getBiobertApiUrl() {
        return getSetting(BIOBERT_API_URL, DEFAULT_BIOBERT_API_URL);
    }

    // Composite DTO
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
