package com.waad.tba.modules.monitoring.service;

import com.waad.tba.modules.monitoring.dto.MonitoringDtos.HealthCardDto;
import com.waad.tba.modules.monitoring.dto.MonitoringDtos.SystemHealthDto;
import com.waad.tba.modules.monitoring.entity.MonitoringAlertStatus;
import com.waad.tba.modules.monitoring.entity.SystemMonitoringAlertState;
import com.waad.tba.modules.monitoring.entity.SystemMonitoringSettings;
import com.waad.tba.modules.monitoring.repository.SystemMonitoringAlertStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitoringAlertSchedulerTest {

    @Mock
    private MonitoringSettingsService settingsService;
    @Mock
    private SystemHealthService systemHealthService;
    @Mock
    private TelegramAlertService telegramAlertService;
    @Mock
    private SystemMonitoringAlertStateRepository stateRepository;
    @Mock
    private ErrorRateMonitor errorRateMonitor;

    private final Map<String, SystemMonitoringAlertState> stateStore = new HashMap<>();
    private MonitoringAlertScheduler scheduler;
    private SystemMonitoringSettings settings;

    @BeforeEach
    void setUp() {
        scheduler = new MonitoringAlertScheduler(settingsService, systemHealthService, telegramAlertService, stateRepository, errorRateMonitor);
        settings = SystemMonitoringSettings.builder()
                .id(1L)
                .telegramEnabled(true)
                .telegramBotToken("123456:TEST_TOKEN")
                .telegramChatId("123")
                .alertEnvironment("test")
                .recoveryEnabled(true)
                .automaticMonitoringEnabled(true)
                .checkIntervalSeconds(60)
                .diskWarningPercent(80)
                .diskCriticalPercent(90)
                .maxBackupAgeHours(72)
                .repeatedErrorThreshold(10)
                .repeatedErrorWindowMinutes(15)
                .alertCooldownSeconds(1800)
                .build();

        lenient().when(settingsService.getSchedulerSettings()).thenReturn(settings);
        lenient().when(settingsService.safeCheckIntervalSeconds(settings)).thenReturn(60);
        lenient().when(settingsService.safeAlertCooldownSeconds(settings)).thenReturn(1800);
        lenient().when(settingsService.safeRepeatedErrorThreshold(settings)).thenReturn(10);
        lenient().when(settingsService.safeRepeatedErrorWindowMinutes(settings)).thenReturn(15);
        lenient().doNothing().when(settingsService).recordAutoCheck(anyString(), anyString(), any(LocalDateTime.class));
        lenient().doNothing().when(errorRateMonitor).purgeOldEvents();
        lenient().when(errorRateMonitor.countRecentErrors(anyInt())).thenReturn(0L);
        lenient().when(stateRepository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(stateStore.get(invocation.getArgument(0))));
        lenient().when(stateRepository.save(any(SystemMonitoringAlertState.class))).thenAnswer(invocation -> {
            SystemMonitoringAlertState state = invocation.getArgument(0);
            stateStore.put(state.getAlertKey(), state);
            return state;
        });
    }

    @Test
    @DisplayName("critical condition sends one alert and repeated checks do not flood before cooldown")
    void criticalConditionShouldNotFlood() {
        when(systemHealthService.fullHealth(settings)).thenReturn(healthWithDatabase("CRITICAL"));

        scheduler.runAutomaticCheck(true);
        scheduler.runAutomaticCheck(true);

        verify(telegramAlertService, times(1)).sendMonitoringMessage(eq(settings), contains("قاعدة البيانات"));
    }

    @Test
    @DisplayName("critical to healthy sends one recovery message")
    void recoveryShouldSendOnce() {
        when(systemHealthService.fullHealth(settings))
                .thenReturn(healthWithDatabase("CRITICAL"))
                .thenReturn(healthWithDatabase("OK"))
                .thenReturn(healthWithDatabase("OK"));

        scheduler.runAutomaticCheck(true);
        scheduler.runAutomaticCheck(true);
        scheduler.runAutomaticCheck(true);

        verify(telegramAlertService, times(2)).sendMonitoringMessage(eq(settings), contains("WAAD"));
        assertNotNull(stateStore.get("DATABASE").getRecoveredAt());
    }

    @Test
    @DisplayName("disabled automatic monitoring sends nothing")
    void disabledMonitoringShouldSendNothing() {
        settings.setAutomaticMonitoringEnabled(false);

        scheduler.runAutomaticCheck(true);

        verifyNoInteractions(telegramAlertService);
        verifyNoInteractions(systemHealthService);
    }

    @Test
    @DisplayName("telegram failure must not fail scheduler and must not mark alert as delivered")
    void telegramFailureMustNotMarkAlertDelivered() {
        when(systemHealthService.fullHealth(settings)).thenReturn(healthWithDatabase("CRITICAL"));
        doThrow(new IllegalStateException("telegram down")).when(telegramAlertService).sendMonitoringMessage(any(SystemMonitoringSettings.class), anyString());

        scheduler.runAutomaticCheck(true);

        verify(stateRepository, atLeastOnce()).save(any(SystemMonitoringAlertState.class));
        SystemMonitoringAlertState state = stateStore.get("DATABASE");
        assertNotNull(state);
        assertNull(state.getLastSentAt());
    }

    @Test
    @DisplayName("severity increase must send a new alert even before cooldown")
    void severityIncreaseShouldSendNewAlert() {
        when(systemHealthService.fullHealth(settings))
                .thenReturn(healthWithDisk("WARNING"))
                .thenReturn(healthWithDisk("CRITICAL"));

        scheduler.runAutomaticCheck(true);
        scheduler.runAutomaticCheck(true);

        verify(telegramAlertService, times(2)).sendMonitoringMessage(eq(settings), contains("مساحة القرص"));
    }

    @Test
    @DisplayName("persisted state with active cooldown prevents duplicate alert after restart")
    void persistedStateShouldPreventDuplicateAfterRestart() {
        stateStore.put("DATABASE", SystemMonitoringAlertState.builder()
                .alertKey("DATABASE")
                .status(MonitoringAlertStatus.CRITICAL)
                .severity(3)
                .firstDetectedAt(LocalDateTime.now().minusMinutes(5))
                .lastDetectedAt(LocalDateTime.now().minusMinutes(5))
                .lastSentAt(LocalDateTime.now())
                .alertCount(1)
                .updatedAt(LocalDateTime.now())
                .build());
        when(systemHealthService.fullHealth(settings)).thenReturn(healthWithDatabase("CRITICAL"));

        scheduler.runAutomaticCheck(true);

        verifyNoInteractions(telegramAlertService);
    }

    @Test
    @DisplayName("database alert still attempts telegram when alert-state repository is unavailable")
    void databaseAlertShouldSendWhenStateRepositoryUnavailable() {
        when(systemHealthService.fullHealth(settings)).thenReturn(healthWithDatabase("CRITICAL"));
        when(stateRepository.findById(anyString())).thenThrow(new RuntimeException("database unavailable"));
        when(stateRepository.save(any(SystemMonitoringAlertState.class))).thenThrow(new RuntimeException("database unavailable"));

        scheduler.runAutomaticCheck(true);

        verify(telegramAlertService, times(1)).sendMonitoringMessage(eq(settings), contains("قاعدة البيانات"));
    }

    private SystemHealthDto healthWithDatabase(String databaseStatus) {
        LocalDateTime now = LocalDateTime.now();
        return new SystemHealthDto(
                "CRITICAL".equals(databaseStatus) ? "CRITICAL" : "OK",
                "test",
                "test-commit",
                now,
                List.of(
                        card("backend", "Backend", "OK", now),
                        card("database", "Database", databaseStatus, now),
                        card("disk", "Disk", "OK", now),
                        card("backupPath", "Backup path", "OK", now),
                        card("uploadPath", "Upload path", "OK", now),
                        card("lastBackup", "Latest backup", "OK", now)
                )
        );
    }

    private SystemHealthDto healthWithDisk(String diskStatus) {
        LocalDateTime now = LocalDateTime.now();
        return new SystemHealthDto(
                "CRITICAL".equals(diskStatus) ? "CRITICAL" : "WARNING",
                "test",
                "test-commit",
                now,
                List.of(
                        card("backend", "Backend", "OK", now),
                        card("database", "Database", "OK", now),
                        card("disk", "Disk", diskStatus, now),
                        card("backupPath", "Backup path", "OK", now),
                        card("uploadPath", "Upload path", "OK", now),
                        card("lastBackup", "Latest backup", "OK", now)
                )
        );
    }

    private HealthCardDto card(String key, String title, String status, LocalDateTime now) {
        return new HealthCardDto(key, title, status, status.equals("OK") ? "healthy" : "problem", null, now);
    }
}
