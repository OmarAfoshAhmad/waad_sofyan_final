package com.waad.tba.common.service;

import com.waad.tba.common.entity.SystemSetting;
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
public class SLASettingsService {

    private final SystemSettingRepository settingRepository;

    public static final String CLAIM_SLA_DAYS_KEY = "CLAIM_SLA_DAYS";
    public static final int DEFAULT_CLAIM_SLA_DAYS = 10;

    public static final String PRE_APPROVAL_SLA_DAYS_KEY = "PRE_APPROVAL_SLA_DAYS";
    public static final int DEFAULT_PRE_APPROVAL_SLA_DAYS = 3;

    public static final String CLAIM_BACKDATED_MONTHS_KEY = "CLAIM_BACKDATED_MONTHS";
    public static final int DEFAULT_CLAIM_BACKDATED_MONTHS = 3;

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

    public int getClaimSlaDays() {
        return getSettingAsInt(CLAIM_SLA_DAYS_KEY, DEFAULT_CLAIM_SLA_DAYS);
    }

    public int getPreApprovalSlaDays() {
        return getSettingAsInt(PRE_APPROVAL_SLA_DAYS_KEY, DEFAULT_PRE_APPROVAL_SLA_DAYS);
    }

    public int getClaimBackdatedMonths() {
        return getSettingAsInt(CLAIM_BACKDATED_MONTHS_KEY, DEFAULT_CLAIM_BACKDATED_MONTHS);
    }
}
