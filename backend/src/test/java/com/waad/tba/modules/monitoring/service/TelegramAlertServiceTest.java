package com.waad.tba.modules.monitoring.service;

import com.waad.tba.modules.monitoring.entity.SystemMonitoringSettings;
import com.waad.tba.security.SecretEncryptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelegramAlertServiceTest {

    @Mock
    private MonitoringSettingsService settingsService;
    @Mock
    private SecretEncryptionService secretEncryptionService;

    private TelegramAlertService service;

    @BeforeEach
    void setUp() {
        service = new TelegramAlertService(settingsService, secretEncryptionService);
    }

    @Test
    @DisplayName("Test message fails cleanly (no network call, no decryption) when Telegram is disabled")
    void disabledTelegramThrowsClearError() {
        when(settingsService.getOrCreate()).thenReturn(SystemMonitoringSettings.builder()
                .telegramEnabled(false)
                .build());

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::sendTestMessage);
        assertTrue(ex.getMessage().contains("غير مفعلة"));
    }

    @Test
    @DisplayName("Test message fails cleanly when token or chat id is missing")
    void missingCredentialsThrowsClearError() {
        when(settingsService.getOrCreate()).thenReturn(SystemMonitoringSettings.builder()
                .telegramEnabled(true)
                .telegramBotToken(" ")
                .telegramChatId(null)
                .alertEnvironment("test")
                .build());

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::sendTestMessage);
        assertTrue(ex.getMessage().contains("غير مكتملة"));
    }
}
