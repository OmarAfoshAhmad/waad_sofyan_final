package com.waad.tba.modules.systembackup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.waad.tba.TbaWaadApplication;
import com.waad.tba.modules.systembackup.dto.BackupDtos.BackupJobDto;
import com.waad.tba.modules.systembackup.dto.BackupDtos.BackupStatusDto;
import com.waad.tba.modules.systembackup.dto.BackupDtos.PurgeResultDto;
import com.waad.tba.modules.systembackup.dto.BackupDtos.ValidationResultDto;
import com.waad.tba.modules.systembackup.entity.BackupStatus;
import com.waad.tba.modules.systembackup.entity.BackupType;
import com.waad.tba.support.PostgresIntegrationTestBase;

/**
 * End-to-end proof that the backup module works against the real schema created by
 * V131 on a real PostgreSQL instance.
 *
 * Uses FILES_ONLY backups deliberately: they exercise the full pipeline (settings row,
 * job lifecycle, archive creation, manifest, checksum, validation, retention) without
 * requiring a pg_dump binary, which is not guaranteed to exist on a build agent.
 *
 * Not annotated @Transactional: backup job rows are written in their own committed
 * transactions by design, so a rollback-per-test wrapper would not reflect reality.
 */
@SpringBootTest(classes = TbaWaadApplication.class)
@ActiveProfiles("test")
class SystemBackupIntegrationTest extends PostgresIntegrationTestBase {

    private static final Path BACKUP_ROOT;

    static {
        try {
            BACKUP_ROOT = Files.createTempDirectory("waad-backup-it-");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void backupProperties(DynamicPropertyRegistry registry) {
        // Keep every artifact inside a disposable directory, never the repo tree.
        registry.add("waad.backup.default-path", BACKUP_ROOT::toString);
    }

    @Autowired
    private BackupService backupService;

    @Autowired
    private BackupSettingsService settingsService;

    @Autowired
    private BackupRetentionService retentionService;

    @Autowired
    private RestoreService restoreService;

    @Test
    void settingsRowIsCreatedOnDemandAndPathIsServerControlled() {
        var settings = settingsService.getSettings();

        assertThat(settings.localPath()).isEqualTo(BACKUP_ROOT.toString());
        assertThat(settings.retentionDays()).isPositive();
        // The scheduler must stay off until an administrator explicitly enables it.
        assertThat(settings.autoBackupEnabled()).isFalse();
    }

    @Test
    void filesOnlyBackupProducesAVerifiableArchive() {
        BackupJobDto job = backupService.create(BackupType.FILES_ONLY, "اختبار تكامل", "admin");

        assertThat(job.status()).isEqualTo(BackupStatus.SUCCESS);
        assertThat(job.fileName()).endsWith(".zip");
        assertThat(job.fileSize()).isPositive();
        assertThat(job.checksum()).isNotBlank();
        assertThat(job.durationMs()).isNotNull();

        Path archive = BACKUP_ROOT.resolve(job.fileName());
        assertThat(archive).exists();
        // The manifest is written next to the archive for out-of-band inspection.
        assertThat(BACKUP_ROOT.resolve(job.fileName().replace(".zip", ".manifest.json"))).exists();

        // Recomputing the checksum over the stored file must match what was recorded.
        ValidationResultDto validation = backupService.validate(job.id());
        assertThat(validation.valid()).isTrue();
        assertThat(validation.actualChecksum()).isEqualToIgnoringCase(job.checksum());

        // A files-only archive has no database component, which verification treats as N/A.
        var verification = restoreService.verify(job.id());
        assertThat(verification.fileExists()).isTrue();
        assertThat(verification.checksumOk()).isTrue();
        assertThat(verification.dumpRestorable()).isTrue();
        assertThat(verification.valid()).isTrue();
    }

    @Test
    void workingDirectoryIsCleanedUpAfterABackup() {
        BackupJobDto job = backupService.create(BackupType.FILES_ONLY, null, "admin");

        assertThat(job.status()).isEqualTo(BackupStatus.SUCCESS);
        // The per-job scratch directory must not survive the run.
        assertThat(BACKUP_ROOT.resolve("work").resolve("backup-" + job.id())).doesNotExist();
    }

    @Test
    void statusReportsWritablePathAndLatestBackup() {
        BackupJobDto job = backupService.create(BackupType.FILES_ONLY, null, "admin");

        BackupStatusDto status = backupService.status();

        assertThat(status.localPathWritable()).isTrue();
        assertThat(status.localPathConfigured()).isTrue();
        assertThat(status.backupCount()).isPositive();
        assertThat(status.successfulBackupCount()).isPositive();
        assertThat(status.lastBackup()).isNotNull();
        assertThat(status.lastBackup().id()).isEqualTo(job.id());
    }

    @Test
    void retentionDryRunNeverDeletesAnything() {
        BackupJobDto job = backupService.create(BackupType.FILES_ONLY, null, "admin");

        PurgeResultDto result = retentionService.purge(true, "admin");

        assertThat(result.dryRun()).isTrue();
        assertThat(result.deletedCount()).isZero();
        // A freshly created backup is inside the retention window and must survive.
        assertThat(BACKUP_ROOT.resolve(job.fileName())).exists();
        assertThat(backupService.get(job.id())).isNotNull();
    }

    @Test
    void unknownBackupIsRejectedRatherThanReturningNull() {
        assertThatThrownBy(() -> backupService.get(999_999L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
