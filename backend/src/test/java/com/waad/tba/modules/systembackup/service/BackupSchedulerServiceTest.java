package com.waad.tba.modules.systembackup.service;

import com.waad.tba.modules.systemadmin.service.AuditLogService;
import com.waad.tba.modules.systembackup.dto.BackupDtos.BackupJobDto;
import com.waad.tba.modules.systembackup.entity.BackupStatus;
import com.waad.tba.modules.systembackup.entity.BackupType;
import com.waad.tba.modules.systembackup.entity.SystemBackupSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackupSchedulerServiceTest {

    @Mock private BackupSettingsService settingsService;
    @Mock private BackupService backupService;
    @Mock private BackupRetentionService retentionService;
    @Mock private BackupStorageService storageService;
    @Mock private BackupFailureNotifier failureNotifier;
    @Mock private AuditLogService auditLogService;

    private BackupSchedulerService scheduler;
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 14, 2, 0);

    @BeforeEach
    void setUp() {
        scheduler = new BackupSchedulerService(settingsService, backupService, retentionService,
                storageService, Optional.of(failureNotifier), Optional.of(auditLogService));
    }

    private SystemBackupSettings dueSettings() {
        return SystemBackupSettings.builder()
                .id(1L)
                .autoBackupEnabled(true)
                .autoBackupType("FULL_SYSTEM")
                .autoBackupHour(2)
                .autoBackupMinute(0)
                .retentionDays(30)
                .build();
    }

    private BackupJobDto successJob() {
        return new BackupJobDto(7L, BackupType.FULL_SYSTEM, BackupStatus.SUCCESS, "f.zip", 10L, "abc",
                null, "SCHEDULER", now, now, 100L, null, "test", null, false, "/app/backups/local1", "zip", null);
    }

    @Test
    @DisplayName("Disabled scheduler does not run a backup")
    void disabledDoesNothing() {
        SystemBackupSettings s = dueSettings();
        s.setAutoBackupEnabled(false);
        when(settingsService.getOrCreate()).thenReturn(s);

        scheduler.runIfDue(now);

        verifyNoInteractions(backupService);
    }

    @Test
    @DisplayName("Not due (different minute) does not run a backup")
    void notDueDoesNothing() {
        when(settingsService.getOrCreate()).thenReturn(dueSettings());

        scheduler.runIfDue(now.withMinute(31));

        verifyNoInteractions(backupService);
    }

    @Test
    @DisplayName("When due, runs backup then purges")
    void dueRunsBackupAndPurge() {
        when(settingsService.getOrCreate()).thenReturn(dueSettings());
        when(backupService.create(eq(BackupType.FULL_SYSTEM), anyString(), anyString())).thenReturn(successJob());

        scheduler.runIfDue(now);

        verify(backupService, times(1)).create(eq(BackupType.FULL_SYSTEM), anyString(), anyString());
        verify(retentionService, times(1)).purge(eq(false), anyString());
        verify(settingsService).recordAutoBackup(eq("SUCCESS"), anyString());
    }

    @Test
    @DisplayName("A re-entrant tick during a running cycle is skipped (no concurrent backup)")
    void concurrentCycleIsSkipped() {
        when(settingsService.getOrCreate()).thenReturn(dueSettings());
        when(backupService.create(any(), anyString(), anyString())).thenAnswer(inv -> {
            // Simulate a second scheduler tick firing while this cycle is in progress.
            scheduler.runIfDue(now);
            return successJob();
        });

        scheduler.runIfDue(now);

        // Despite the re-entrant call, create must run exactly once.
        verify(backupService, times(1)).create(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Backup failure never propagates and is recorded as FAILED")
    void failureDoesNotBreak() {
        when(settingsService.getOrCreate()).thenReturn(dueSettings());
        when(backupService.create(any(), anyString(), anyString())).thenThrow(new IllegalStateException("disk full"));

        assertDoesNotThrow(() -> scheduler.runIfDue(now));

        verify(settingsService).recordAutoBackup(eq("FAILED"), anyString());
    }

    @Test
    @DisplayName("Failure alerts report the runtime environment, not the settings updatedBy field")
    void failureAlertReportsEnvironment() {
        when(settingsService.getOrCreate()).thenReturn(dueSettings());
        when(storageService.environmentName()).thenReturn("production");
        when(backupService.create(any(), anyString(), anyString())).thenThrow(new IllegalStateException("disk full"));

        scheduler.runIfDue(now);

        verify(failureNotifier).notifyScheduledBackupFailed(eq("production"), contains("disk full"));
    }

    @Test
    @DisplayName("A failing alert channel never breaks the scheduler")
    void brokenNotifierIsContained() {
        when(settingsService.getOrCreate()).thenReturn(dueSettings());
        when(backupService.create(any(), anyString(), anyString())).thenThrow(new IllegalStateException("disk full"));
        doThrow(new RuntimeException("telegram unreachable"))
                .when(failureNotifier).notifyScheduledBackupFailed(any(), any());

        assertDoesNotThrow(() -> scheduler.runIfDue(now));

        verify(settingsService).recordAutoBackup(eq("FAILED"), anyString());
    }
}
