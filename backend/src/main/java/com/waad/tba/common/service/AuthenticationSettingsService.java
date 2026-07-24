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
public class AuthenticationSettingsService {

    private final SystemSettingRepository settingRepository;

    public static final String PASSWORD_RESET_METHOD_KEY = "PASSWORD_RESET_METHOD";
    public static final String PASSWORD_RESET_TOKEN_EXPIRY_MINUTES_KEY = "PASSWORD_RESET_TOKEN_EXPIRY_MINUTES";
    public static final String PASSWORD_RESET_OTP_EXPIRY_MINUTES_KEY = "PASSWORD_RESET_OTP_EXPIRY_MINUTES";
    public static final String PASSWORD_RESET_OTP_LENGTH_KEY = "PASSWORD_RESET_OTP_LENGTH";

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
}
