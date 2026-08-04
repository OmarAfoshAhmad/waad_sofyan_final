package com.waad.tba.modules.systembackup.service;

import com.waad.tba.modules.systemadmin.service.AuditLogService;
import com.waad.tba.modules.systembackup.entity.BackupType;
import com.waad.tba.modules.systembackup.entity.SystemBackupSettings;
import com.waad.tba.modules.systembackup.repository.SystemBackupJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BackupServiceTest {

    @Mock
    private BackupSettingsService settingsService;
    @Mock
    private BackupStorageService storageService;
    @Mock
    private BackupChecksumService checksumService;
    @Mock
    private BackupManifestService manifestService;
    @Mock
    private BackupHistoryService historyService;
    @Mock
    private BackupJobWriter jobWriter;
    @Mock
    private SystemBackupJobRepository jobRepository;
    @Mock
    private AuditLogService auditLogService;

    private BackupService service;

    @BeforeEach
    void setUp() {
        service = new BackupService(settingsService, storageService, checksumService,
                manifestService, historyService, jobWriter, jobRepository, Optional.of(auditLogService));
    }

    @Test
    @DisplayName("A missing backup type is rejected before any work is done")
    void nullTypeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.create(null, "note", "admin"));
        verifyNoInteractions(jobRepository, jobWriter);
    }

    @Test
    @DisplayName("A disabled local destination is rejected before any filesystem work")
    void disabledDestinationIsRejected() {
        when(settingsService.getOrCreate()).thenReturn(SystemBackupSettings.builder()
                .id(1L)
                .localEnabled(false)
                .localPath("/app/backups/local1")
                .build());

        assertThrows(IllegalStateException.class, () -> service.create(BackupType.FILES_ONLY, "note", "admin"));
        verifyNoInteractions(jobRepository, jobWriter);
    }
}
