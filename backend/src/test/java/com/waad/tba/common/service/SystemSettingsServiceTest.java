package com.waad.tba.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.waad.tba.common.dto.SystemSettingDto;
import com.waad.tba.common.entity.SystemSetting;
import com.waad.tba.common.repository.SystemSettingRepository;
import com.waad.tba.common.service.SettingsManagementService;

@ExtendWith(MockitoExtension.class)
class SystemSettingsServiceTest {

    @Mock
    private SettingsInitializationService initializationService;

    @Mock
    private SettingsManagementService managementService;

    @Mock
    private SLASettingsService slaService;

    @Mock
    private AuthenticationSettingsService authService;

    @Mock
    private UIConfigService uiConfigService;

    @InjectMocks
    private SystemSettingsService service;

    @Test
    void updateSettingRejectsUnknownKeysInsteadOfCreatingAdHocSettings() {
        when(managementService.updateSetting("UNKNOWN_SETTING", "value", "admin"))
                .thenThrow(new IllegalArgumentException("Unknown system setting: UNKNOWN_SETTING"));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateSetting("UNKNOWN_SETTING", "value", "admin"));
    }

    @Test
    void updateSettingValidatesIntegerRules() {
        when(managementService.updateSetting(SettingsManagementService.CLAIM_SLA_DAYS_KEY, "31", "admin"))
                .thenThrow(new IllegalArgumentException("Setting must be <= 30"));

        assertThrows(IllegalArgumentException.class,
                () -> service.updateSetting(SettingsManagementService.CLAIM_SLA_DAYS_KEY, "31", "admin"));
    }

    @Test
    void updateSettingUpdatesKnownEditableSettingOnly() {
        SystemSettingDto dto = SystemSettingDto.builder()
                .id(1L)
                .settingKey(SettingsManagementService.CLAIM_SLA_DAYS_KEY)
                .settingValue("12")
                .category("CLAIMS")
                .build();

        when(managementService.updateSetting(SettingsManagementService.CLAIM_SLA_DAYS_KEY, "12", "admin"))
                .thenReturn(dto);

        SystemSettingDto result = service.updateSetting(SettingsManagementService.CLAIM_SLA_DAYS_KEY, "12", "admin");

        assertEquals("12", result.settingValue());
        verify(managementService).updateSetting(SettingsManagementService.CLAIM_SLA_DAYS_KEY, "12", "admin");
    }

    private SystemSetting editableIntegerSetting(String key, String validationRules) {
        return SystemSetting.builder()
                .id(1L)
                .settingKey(key)
                .settingValue("10")
                .valueType(SystemSetting.SettingValueType.INTEGER)
                .category("CLAIMS")
                .isEditable(true)
                .active(true)
                .validationRules(validationRules)
                .build();
    }
}
