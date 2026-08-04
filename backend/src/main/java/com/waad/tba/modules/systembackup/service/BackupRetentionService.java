package com.waad.tba.modules.systembackup.service;

import com.waad.tba.modules.systembackup.dto.BackupDtos.PurgeCandidateDto;
import com.waad.tba.modules.systembackup.dto.BackupDtos.PurgeResultDto;
import com.waad.tba.modules.systembackup.entity.BackupStatus;
import com.waad.tba.modules.systembackup.entity.SystemBackupJob;
import com.waad.tba.modules.systembackup.entity.SystemBackupSettings;
import com.waad.tba.modules.systembackup.repository.SystemBackupJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deletes backups older than the configured retention window — safely:
 *  - only ever touches files inside the configured backup base directory (no traversal),
 *  - never deletes the most recent successful backup, even if it is old,
 *  - supports a dry-run that deletes nothing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupRetentionService {

    private final BackupSettingsService settingsService;
    private final SystemBackupJobRepository jobRepository;

    @Transactional
    public PurgeResultDto purge(boolean dryRun, String username) {
        SystemBackupSettings settings = settingsService.getOrCreate();
        int retentionDays = settings.getRetentionDays() == null || settings.getRetentionDays() < 1
                ? 30 : settings.getRetentionDays();
        Path backupRoot = settingsService.localBackupPath();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

        List<SystemBackupJob> jobs = jobRepository.findTop100ByOrderByStartedAtDesc();
        Long latestSuccessId = jobs.stream()
                .filter(j -> j.getStatus() == BackupStatus.SUCCESS)
                .max(Comparator.comparing(SystemBackupJob::getStartedAt))
                .map(SystemBackupJob::getId)
                .orElse(null);

        List<PurgeCandidateDto> items = new ArrayList<>();
        long deleted = 0;
        long bytesFreed = 0;

        for (SystemBackupJob job : jobs) {
            if (job.getStartedAt() == null || !job.getStartedAt().isBefore(cutoff)) {
                continue; // within retention window
            }
            if (latestSuccessId != null && latestSuccessId.equals(job.getId())) {
                continue; // never delete the latest successful backup
            }

            boolean insideRoot = isInsideRoot(job.getFilePath(), backupRoot);
            if (job.getFilePath() != null && !insideRoot) {
                items.add(new PurgeCandidateDto(job.getId(), job.getFileName(), job.getStartedAt(),
                        job.getFileSize(), false, "خارج مسار النسخ الاحتياطي — تم التخطي"));
                continue;
            }

            if (dryRun) {
                items.add(new PurgeCandidateDto(job.getId(), job.getFileName(), job.getStartedAt(),
                        job.getFileSize(), false, "سيتم الحذف (تجريبي)"));
                continue;
            }

            long freed = deleteFiles(job, backupRoot);
            jobRepository.delete(job);
            deleted++;
            bytesFreed += freed;
            items.add(new PurgeCandidateDto(job.getId(), job.getFileName(), job.getStartedAt(),
                    job.getFileSize(), true, "تم الحذف"));
        }

        String messageAr = dryRun
                ? "معاينة: " + items.size() + " نسخة مرشحة للحذف (أقدم من " + retentionDays + " يومًا)."
                : "تم حذف " + deleted + " نسخة قديمة وتحرير " + bytesFreed + " بايت.";
        if (!dryRun) {
            settingsService.recordPurge("SUCCESS", messageAr);
        }
        return new PurgeResultDto(dryRun, retentionDays, items.size(), deleted, bytesFreed,
                latestSuccessId, items, messageAr);
    }

    private long deleteFiles(SystemBackupJob job, Path backupRoot) {
        long freed = 0;
        freed += deleteIfInside(job.getFilePath(), backupRoot);
        deleteIfInside(job.getManifestPath(), backupRoot);
        return freed;
    }

    private long deleteIfInside(String rawPath, Path backupRoot) {
        if (rawPath == null || rawPath.isBlank()) {
            return 0;
        }
        try {
            Path path = Path.of(rawPath).toAbsolutePath().normalize();
            if (!path.startsWith(backupRoot)) {
                log.warn("[BKP-4] Refusing to delete path outside backup root: {}", path);
                return 0;
            }
            long size = Files.exists(path) ? Files.size(path) : 0;
            Files.deleteIfExists(path);
            return size;
        } catch (Exception e) {
            log.warn("[BKP-4] Failed to delete backup artifact {}: {}", rawPath, e.getMessage());
            return 0;
        }
    }

    private boolean isInsideRoot(String rawPath, Path backupRoot) {
        if (rawPath == null || rawPath.isBlank()) {
            return true; // nothing to delete on disk; row-only cleanup is safe
        }
        try {
            return Path.of(rawPath).toAbsolutePath().normalize().startsWith(backupRoot);
        } catch (Exception e) {
            return false;
        }
    }
}
