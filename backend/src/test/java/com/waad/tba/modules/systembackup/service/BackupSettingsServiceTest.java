package com.waad.tba.modules.systembackup.service;

import com.waad.tba.modules.systemadmin.service.AuditLogService;
import com.waad.tba.modules.systembackup.dto.BackupDtos.BackupSettingsDto;
import com.waad.tba.modules.systembackup.entity.SystemBackupSettings;
import com.waad.tba.modules.systembackup.repository.SystemBackupSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackupSettingsServiceTest {

    @Mock
    private SystemBackupSettingsRepository repository;
    @Mock
    private Environment environment;
    @Mock
    private AuditLogService auditLogService;

    private BackupSettingsService service;

    @BeforeEach
    void setUp() {
        service = new BackupSettingsService(repository, environment, Optional.of(auditLogService));
        lenient().when(repository.findById(1L)).thenReturn(Optional.of(SystemBackupSettings.builder().id(1L).build()));
        lenient().when(repository.save(any(SystemBackupSettings.class))).thenAnswer(i -> i.getArgument(0));
        // No container/host path configured in the environment => the safe default is used.
        lenient().when(environment.getProperty("BACKUP_LOCAL_1_CONTAINER")).thenReturn(null);
        lenient().when(environment.getProperty("BACKUP_DIR")).thenReturn(null);
        lenient().when(environment.getProperty("BACKUP_LOCAL_1_HOST")).thenReturn(null);
    }

    private static BackupSettingsDto dto(String localPath, Integer retentionDays) {
        return new BackupSettingsDto(true, "الوجهة", localPath, "x", "y", "z", retentionDays,
                false, "FULL_SYSTEM", 2, 0, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("Browser-submitted paths are ignored; the server-controlled default path wins")
    void rejectsBrowserSuppliedPath() {
        BackupSettingsDto result = service.update(dto("C:\\Users\\evil\\Desktop", 30), "admin");

        assertEquals("./storage/backups", result.localPath());
        assertFalse(result.localPath().contains("evil"));
    }

    @Test
    @DisplayName("Invalid retention days fall back to a safe default of 30")
    void invalidRetentionFallsBack() {
        assertEquals(30, service.update(dto(null, 0), "admin").retentionDays());
        assertEquals(30, service.update(dto(null, -5), "admin").retentionDays());
        assertEquals(15, service.update(dto(null, 15), "admin").retentionDays());
    }

    @Test
    @DisplayName("Settings update is written to the audit log under a canonical action verb")
    void updateIsAudited() {
        service.update(dto(null, 30), "admin");
        // "UPDATED"/"SYSTEM_SETTING" map to real audit enum values; a bespoke verb would
        // fall through to MANUAL_OVERRIDE and lose meaning in the audit trail.
        verify(auditLogService).createAuditLog(
                eq("UPDATED"), eq("SYSTEM_SETTING"), eq(1L), any(), any(), eq("admin"), any(), any());
    }

    @Test
    @DisplayName("A configured environment path overrides the built-in default")
    void configuredPathWins() {
        when(environment.getProperty("BACKUP_LOCAL_1_CONTAINER")).thenReturn("/app/backups/local1");

        assertEquals("/app/backups/local1", service.update(dto(null, 30), "admin").localPath());
    }
}
