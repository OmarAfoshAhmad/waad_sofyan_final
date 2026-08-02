package com.waad.tba.common.service;

import com.waad.tba.common.dto.SystemSettingDto;
import com.waad.tba.common.entity.SystemSetting;
import com.waad.tba.common.repository.SystemSettingRepository;
import com.waad.tba.security.audit.SecurityAuditEvent;
import com.waad.tba.security.audit.SecurityAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SettingsManagementService {

    private final SystemSettingRepository settingRepository;
    private final SecurityAuditService securityAuditService;

    public static final String CLAIM_SLA_DAYS_KEY = "CLAIM_SLA_DAYS";
    private static final java.util.Set<String> OPTIONAL_STRING_SETTINGS = java.util.Set.of(
            "LOGO_URL"
    );

    @Cacheable(value = "systemSettings", key = "#key")
    public String getSetting(String key, String defaultValue) {
        return settingRepository.findBySettingKey(key)
                .map(SystemSetting::getSettingValue)
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

    @Transactional
    @CacheEvict(value = "systemSettings", key = "#key")
    public SystemSettingDto updateSetting(String key, String value, String updatedBy) {
        SystemSetting setting = settingRepository.findBySettingKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Unknown system setting: " + key));

        if (!Boolean.TRUE.equals(setting.getActive())) {
            throw new IllegalStateException("System setting is inactive: " + key);
        }
        if (!Boolean.TRUE.equals(setting.getIsEditable())) {
            throw new IllegalStateException("System setting is not editable: " + key);
        }

        String oldValue = setting.getSettingValue();
        String normalizedValue = normalizeAndValidateValue(setting, value);
        setting.setSettingValue(normalizedValue);
        setting.setUpdatedBy(updatedBy);

        SystemSetting saved = settingRepository.save(setting);
        log.info("⚙️ Setting {} updated by {}", key, updatedBy);

        // Security-relevant setting changes (password-reset policy, SLA,
        // auth/feature-affecting keys) previously produced only this log
        // line — no queryable audit record, unlike login/password events.
        securityAuditService.logSecurityEvent(null, updatedBy,
                SecurityAuditEvent.AuditActionType.SETTING_CHANGED,
                "SYSTEM_SETTING", null, key, null, null,
                SecurityAuditEvent.AuditResult.SUCCESS,
                oldValue + " -> " + normalizedValue, null, null);

        return SystemSettingDto.from(saved);
    }

    @Transactional
    @CacheEvict(value = "systemSettings", key = "'" + CLAIM_SLA_DAYS_KEY + "'")
    public void updateClaimSlaDays(int slaDays, String updatedBy) {
        if (slaDays < 1 || slaDays > 30) {
            throw new IllegalArgumentException("SLA days must be between 1 and 30");
        }

        updateSetting(CLAIM_SLA_DAYS_KEY, String.valueOf(slaDays), updatedBy);
        log.info("📅 Claim SLA days updated to {} by {}", slaDays, updatedBy);
    }

    public List<SystemSettingDto> getSettingsByCategory(String category) {
        return settingRepository.findByCategory(category).stream()
                .filter(setting -> Boolean.TRUE.equals(setting.getActive()))
                .map(SystemSettingDto::from)
                .toList();
    }

    public List<SystemSettingDto> getEditableSettings() {
        return settingRepository.findEditableSettings().stream()
                .map(SystemSettingDto::from)
                .toList();
    }

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

        securityAuditService.logSecurityEvent(null, updatedBy,
                SecurityAuditEvent.AuditActionType.SETTING_CHANGED,
                "SYSTEM_SETTING", null, key, null, null,
                SecurityAuditEvent.AuditResult.SUCCESS,
                "reset to default: " + oldValue + " -> " + setting.getDefaultValue(), null, null);
    }

    private String normalizeAndValidateValue(SystemSetting setting, String value) {
        String normalized = value != null ? value.trim() : "";
        SystemSetting.SettingValueType valueType = setting.getValueType();

        // Baseline invariants that must hold regardless of whether this
        // setting's `validation_rules` column happens to be populated — a
        // blank/null validation_rules previously meant "accept anything",
        // so e.g. PASSWORD_RESET_TOKEN_EXPIRY_MINUTES = -1 or 0 was
        // persisted and served verbatim by AuthenticationSettingsService.
        if (valueType == SystemSetting.SettingValueType.STRING
                && normalized.isEmpty()
                && !OPTIONAL_STRING_SETTINGS.contains(setting.getSettingKey())) {
            throw new IllegalArgumentException("Setting " + setting.getSettingKey() + " cannot be empty");
        }
        if (valueType == SystemSetting.SettingValueType.INTEGER) {
            if (parseInteger(setting.getSettingKey(), normalized) < 0) {
                throw new IllegalArgumentException("Setting " + setting.getSettingKey() + " must not be negative");
            }
        } else if (valueType == SystemSetting.SettingValueType.DECIMAL) {
            parseDecimal(setting.getSettingKey(), normalized);
            if (new java.math.BigDecimal(normalized).signum() < 0) {
                throw new IllegalArgumentException("Setting " + setting.getSettingKey() + " must not be negative");
            }
        } else if (valueType == SystemSetting.SettingValueType.BOOLEAN) {
            normalized = normalized.toLowerCase(Locale.ROOT);
            if (!"true".equals(normalized) && !"false".equals(normalized)) {
                throw new IllegalArgumentException("Setting " + setting.getSettingKey() + " must be true or false");
            }
        }

        validateRules(setting, normalized);
        return normalized;
    }

    private int parseInteger(String key, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Setting " + key + " must be an integer", ex);
        }
    }

    private void parseDecimal(String key, String value) {
        try {
            new java.math.BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Setting " + key + " must be a decimal", ex);
        }
    }

    private void validateRules(SystemSetting setting, String value) {
        String rules = setting.getValidationRules();
        if (rules == null || rules.isBlank()) {
            return;
        }

        for (String rule : rules.split(",")) {
            String trimmed = rule.trim();
            if (trimmed.startsWith("min:")) {
                int min = Integer.parseInt(trimmed.substring(4));
                if (parseInteger(setting.getSettingKey(), value) < min) {
                    throw new IllegalArgumentException("Setting " + setting.getSettingKey() + " must be >= " + min);
                }
            } else if (trimmed.startsWith("max:")) {
                int max = Integer.parseInt(trimmed.substring(4));
                if (parseInteger(setting.getSettingKey(), value) > max) {
                    throw new IllegalArgumentException("Setting " + setting.getSettingKey() + " must be <= " + max);
                }
            } else if (trimmed.startsWith("enum:")) {
                String allowed = trimmed.substring(5);
                boolean matches = List.of(allowed.split("\\|")).stream()
                        .anyMatch(option -> option.equals(value));
                if (!matches) {
                    throw new IllegalArgumentException("Setting " + setting.getSettingKey() + " has unsupported value");
                }
            }
        }
    }
}
