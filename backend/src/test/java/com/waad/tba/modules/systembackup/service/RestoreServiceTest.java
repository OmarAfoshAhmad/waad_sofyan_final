package com.waad.tba.modules.systembackup.service;

import com.waad.tba.modules.systembackup.dto.BackupDtos.RestoreVerificationDto;
import com.waad.tba.modules.systembackup.entity.BackupStatus;
import com.waad.tba.modules.systembackup.entity.BackupType;
import com.waad.tba.modules.systembackup.entity.SystemBackupJob;
import com.waad.tba.modules.systembackup.repository.SystemBackupJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestoreServiceTest {

    @Mock private SystemBackupJobRepository jobRepository;
    @Mock private BackupSettingsService settingsService;
    @Mock private BackupStorageService storageService;
    @Mock private BackupChecksumService checksumService;

    private RestoreService service;

    @TempDir Path backupRoot;
    @TempDir Path outsideDir;

    @BeforeEach
    void setUp() {
        service = new RestoreService(jobRepository, settingsService, storageService, checksumService);
        lenient().when(settingsService.localBackupPath()).thenReturn(backupRoot.toAbsolutePath().normalize());
    }

    private SystemBackupJob job(String filePath) {
        return SystemBackupJob.builder()
                .id(1L).type(BackupType.FULL_SYSTEM).status(BackupStatus.SUCCESS)
                .filePath(filePath).checksum("abc").startedAt(LocalDateTime.now()).build();
    }

    @Test
    @DisplayName("Missing job throws")
    void missingJobThrows() {
        when(jobRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.verify(99L));
    }

    @Test
    @DisplayName("File outside backup root is rejected with an Arabic message")
    void outsidePathRejected() {
        Path evil = outsideDir.resolve("evil.zip");
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job(evil.toString())));

        RestoreVerificationDto result = service.verify(1L);

        assertFalse(result.valid());
        assertTrue(result.messageAr().contains("خارج"));
    }

    @Test
    @DisplayName("Missing file fails verification with an Arabic message")
    void missingFileFails() {
        Path missing = backupRoot.resolve("missing.zip");
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job(missing.toString())));

        RestoreVerificationDto result = service.verify(1L);

        assertFalse(result.valid());
        assertFalse(result.fileExists());
        assertTrue(result.messageAr().contains("غير موجود"));
    }
}
