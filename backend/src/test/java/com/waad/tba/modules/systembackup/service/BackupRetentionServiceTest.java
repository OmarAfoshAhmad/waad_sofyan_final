package com.waad.tba.modules.systembackup.service;

import com.waad.tba.modules.systembackup.dto.BackupDtos.PurgeResultDto;
import com.waad.tba.modules.systembackup.entity.BackupStatus;
import com.waad.tba.modules.systembackup.entity.BackupType;
import com.waad.tba.modules.systembackup.entity.SystemBackupJob;
import com.waad.tba.modules.systembackup.entity.SystemBackupSettings;
import com.waad.tba.modules.systembackup.repository.SystemBackupJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackupRetentionServiceTest {

    @Mock private BackupSettingsService settingsService;
    @Mock private SystemBackupJobRepository jobRepository;

    private BackupRetentionService service;

    @TempDir Path backupRoot;
    @TempDir Path outsideDir;

    private SystemBackupJob latestSuccess;
    private SystemBackupJob olderSuccess;
    private SystemBackupJob oldFailed;
    private SystemBackupJob outside;

    @BeforeEach
    void setUp() throws Exception {
        service = new BackupRetentionService(settingsService, jobRepository);

        Path latestFile = Files.writeString(backupRoot.resolve("latest.zip"), "latest");
        Path olderFile = Files.writeString(backupRoot.resolve("older.zip"), "older");
        Path failedFile = Files.writeString(backupRoot.resolve("failed.zip"), "failed");
        Path outsideFile = Files.writeString(outsideDir.resolve("evil.zip"), "evil");

        LocalDateTime now = LocalDateTime.now();
        latestSuccess = job(10L, BackupStatus.SUCCESS, now.minusDays(35), latestFile);   // newest SUCCESS -> keep
        olderSuccess = job(11L, BackupStatus.SUCCESS, now.minusDays(40), olderFile);      // old -> delete
        oldFailed = job(12L, BackupStatus.FAILED, now.minusDays(50), failedFile);         // old -> delete
        outside = job(13L, BackupStatus.SUCCESS, now.minusDays(60), outsideFile);         // outside root -> skip

        SystemBackupSettings settings = SystemBackupSettings.builder().id(1L).retentionDays(30).build();
        lenient().when(settingsService.getOrCreate()).thenReturn(settings);
        lenient().when(settingsService.localBackupPath()).thenReturn(backupRoot.toAbsolutePath().normalize());
        lenient().when(jobRepository.findTop100ByOrderByStartedAtDesc())
                .thenReturn(List.of(latestSuccess, olderSuccess, oldFailed, outside));
    }

    private SystemBackupJob job(Long id, BackupStatus status, LocalDateTime startedAt, Path file) {
        return SystemBackupJob.builder()
                .id(id).type(BackupType.FULL_SYSTEM).status(status)
                .fileName(file.getFileName().toString()).filePath(file.toString())
                .fileSize(10L).startedAt(startedAt).build();
    }

    @Test
    @DisplayName("Dry-run deletes nothing but lists candidates")
    void dryRunDeletesNothing() {
        PurgeResultDto result = service.purge(true, "admin");

        assertTrue(result.dryRun());
        assertEquals(0, result.deletedCount());
        assertEquals(3, result.candidateCount()); // olderSuccess, oldFailed, outside (latestSuccess excluded)
        verify(jobRepository, never()).delete(any());
        assertTrue(Files.exists(backupRoot.resolve("older.zip")));
    }

    @Test
    @DisplayName("Real purge deletes old backups inside the root, keeps latest success, skips outside path")
    void realPurgeRespectsSafety() {
        PurgeResultDto result = service.purge(false, "admin");

        assertFalse(result.dryRun());
        assertEquals(2, result.deletedCount());
        assertEquals(Long.valueOf(10L), result.keptLatestSuccessId());

        // Deleted: olderSuccess + oldFailed (inside root, old, not latest success)
        verify(jobRepository).delete(olderSuccess);
        verify(jobRepository).delete(oldFailed);
        assertFalse(Files.exists(backupRoot.resolve("older.zip")));
        assertFalse(Files.exists(backupRoot.resolve("failed.zip")));

        // Kept: latest successful backup even though it is older than retention
        verify(jobRepository, never()).delete(latestSuccess);
        assertTrue(Files.exists(backupRoot.resolve("latest.zip")));

        // Skipped: file outside the backup root must never be deleted
        verify(jobRepository, never()).delete(outside);
        assertTrue(Files.exists(outsideDir.resolve("evil.zip")));
    }
}
