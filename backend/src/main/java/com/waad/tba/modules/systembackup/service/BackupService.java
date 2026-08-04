package com.waad.tba.modules.systembackup.service;

import com.waad.tba.modules.systemadmin.service.AuditLogService;
import com.waad.tba.modules.systembackup.dto.BackupDtos.BackupJobDto;
import com.waad.tba.modules.systembackup.dto.BackupDtos.BackupManifest;
import com.waad.tba.modules.systembackup.dto.BackupDtos.BackupStatusDto;
import com.waad.tba.modules.systembackup.dto.BackupDtos.ValidationResultDto;
import com.waad.tba.modules.systembackup.entity.BackupStatus;
import com.waad.tba.modules.systembackup.entity.BackupType;
import com.waad.tba.modules.systembackup.entity.SystemBackupJob;
import com.waad.tba.modules.systembackup.entity.SystemBackupSettings;
import com.waad.tba.modules.systembackup.repository.SystemBackupJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;

/**
 * Creates, validates and serves system backups.
 *
 * The heavy work (pg_dump, zipping the uploads tree) runs OUTSIDE any transaction —
 * only the short job-row writes are transactional, via {@link BackupJobWriter}. This
 * keeps a pooled DB connection from being held for the whole backup and makes the
 * RUNNING row observable while the backup is still in progress.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    private final BackupSettingsService settingsService;
    private final BackupStorageService storageService;
    private final BackupChecksumService checksumService;
    private final BackupManifestService manifestService;
    private final BackupHistoryService historyService;
    private final BackupJobWriter jobWriter;
    private final SystemBackupJobRepository jobRepository;
    private final Optional<AuditLogService> auditLogService;

    /** Guarantees a single backup runs at a time (manual or scheduled). */
    private static final ReentrantLock BACKUP_LOCK = new ReentrantLock();

    public boolean isBackupRunning() {
        return BACKUP_LOCK.isLocked();
    }

    @Transactional(readOnly = true)
    public List<BackupJobDto> list() {
        return jobRepository.findTop100ByOrderByStartedAtDesc().stream().map(historyService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public BackupJobDto get(Long id) {
        return historyService.toDto(findJob(id));
    }

    @Transactional(readOnly = true)
    public BackupStatusDto status() {
        Path localPath = settingsService.localBackupPath();
        boolean writable = storageService.isWritableDirectory(localPath);
        Long usableSpace = storageService.usableSpace(localPath);
        Optional<SystemBackupJob> latest = jobRepository.findTopByOrderByStartedAtDesc();
        var settings = settingsService.getSettings();
        return new BackupStatusDto(
                latest.map(historyService::toDto).orElse(null),
                jobRepository.count(),
                jobRepository.countByStatus(BackupStatus.SUCCESS),
                jobRepository.countByStatus(BackupStatus.FAILED),
                localPath.toString(),
                settings.localHostPath(),
                settings.localContainerPath(),
                !localPath.toString().isBlank(),
                writable,
                writable
                        ? "المسار المحلي المعتمد على السيرفر قابل للكتابة"
                        : "المسار المحلي المعتمد على السيرفر غير قابل للكتابة",
                usableSpace,
                latest.map(SystemBackupJob::getFileSize).orElse(null)
        );
    }

    /**
     * Deliberately NOT @Transactional — see the class javadoc. Job rows are written
     * through {@link BackupJobWriter} in their own transactions.
     */
    public BackupJobDto create(BackupType type, String note, String username) {
        if (type == null) {
            throw new IllegalArgumentException("Backup type is required");
        }
        SystemBackupSettings settings = settingsService.getOrCreate();
        if (!Boolean.TRUE.equals(settings.getLocalEnabled())) {
            throw new IllegalStateException("وجهة النسخ الاحتياطي المحلية معطّلة في الإعدادات.");
        }
        // Prevent two backups running at the same time (manual + scheduled).
        if (!BACKUP_LOCK.tryLock()) {
            throw new IllegalStateException("نسخة احتياطية أخرى قيد التنفيذ حاليًا. حاول بعد اكتمالها.");
        }
        try {
            return runCreate(type, note, username, settings);
        } finally {
            BACKUP_LOCK.unlock();
        }
    }

    private BackupJobDto runCreate(BackupType type, String note, String username, SystemBackupSettings settings) {
        LocalDateTime startedAt = LocalDateTime.now();
        SystemBackupJob job = jobWriter.save(SystemBackupJob.builder()
                .type(type)
                .status(BackupStatus.RUNNING)
                .note(note)
                .createdBy(username)
                .startedAt(startedAt)
                .environment(storageService.environmentName())
                .destinationPath(settings.getLocalPath())
                .encrypted(false)
                .backupFormat("zip")
                .gitCommit(resolveGitCommit())
                .build());

        List<String> warnings = new ArrayList<>();
        Path workingDir = null;
        try {
            Path backupRoot = settingsService.localBackupPath();
            Files.createDirectories(backupRoot);
            if (!storageService.isWritableDirectory(backupRoot)) {
                throw new IllegalStateException("مسار النسخ الاحتياطي غير قابل للكتابة: " + backupRoot);
            }

            workingDir = storageService.createWorkingDirectory(backupRoot, job.getId());
            String baseName = "waad-backup-" + job.getId() + "-" + type.name().toLowerCase()
                    + "-" + System.currentTimeMillis();
            Path archive = backupRoot.resolve(baseName + ".zip").toAbsolutePath().normalize();

            boolean includeDb = type == BackupType.DATABASE_ONLY || type == BackupType.FULL_SYSTEM;
            boolean includeFiles = type == BackupType.FILES_ONLY || type == BackupType.FULL_SYSTEM;

            Path dbDump = includeDb ? storageService.dumpDatabase(workingDir, "database.dump", warnings) : null;
            Path finalDbDump = dbDump;

            BackupManifest preliminaryManifest = new BackupManifest(
                    job.getId(), type, startedAt, null,
                    job.getEnvironment(), job.getGitCommit(),
                    storageService.includedComponents(includeDb, includeFiles),
                    username, note, null, null, "zip", archive.toString(), warnings);

            storageService.writeZip(archive, zip -> {
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write(manifestService.toBytes(preliminaryManifest));
                zip.closeEntry();

                if (finalDbDump != null) {
                    zip.putNextEntry(new ZipEntry("database/database.dump"));
                    Files.copy(finalDbDump, zip);
                    zip.closeEntry();
                }
                if (includeFiles) {
                    storageService.addPathToZip(zip, storageService.uploadsPath(), "uploads", warnings);
                }
            });

            String checksum = checksumService.sha256(archive);
            long size = Files.size(archive);
            LocalDateTime completedAt = LocalDateTime.now();
            Path manifestPath = backupRoot.resolve(baseName + ".manifest.json").toAbsolutePath().normalize();
            manifestService.write(manifestPath, new BackupManifest(
                    job.getId(), type, startedAt, completedAt,
                    job.getEnvironment(), job.getGitCommit(),
                    storageService.includedComponents(includeDb, includeFiles),
                    username, note, checksum, size, "zip", archive.toString(), warnings));

            job.setStatus(BackupStatus.SUCCESS);
            job.setFileName(archive.getFileName().toString());
            job.setFilePath(archive.toString());
            job.setFileSize(size);
            job.setChecksum(checksum);
            job.setManifestPath(manifestPath.toString());
            job.setCompletedAt(completedAt);
            job.setDurationMs(Duration.between(startedAt, completedAt).toMillis());
            job.setWarnings(joinWarnings(warnings));
        } catch (Exception e) {
            LocalDateTime completedAt = LocalDateTime.now();
            log.error("[BKP] Backup job {} failed: {}", job.getId(), e.getMessage(), e);
            job.setStatus(BackupStatus.FAILED);
            job.setCompletedAt(completedAt);
            job.setDurationMs(Duration.between(startedAt, completedAt).toMillis());
            job.setErrorMessage(safeMessage(e));
            job.setWarnings(joinWarnings(warnings));
        } finally {
            cleanupWorkingDirectory(workingDir);
        }

        SystemBackupJob saved = jobWriter.save(job);
        audit(username, saved.getId(),
                "Backup executed (type=" + saved.getType() + ", status=" + saved.getStatus() + ")");
        return historyService.toDto(saved);
    }

    @Transactional(readOnly = true)
    public ValidationResultDto validate(Long id) {
        SystemBackupJob job = findJob(id);
        if (job.getFilePath() == null || job.getChecksum() == null) {
            return new ValidationResultDto(id, false, job.getChecksum(), null,
                    "Backup has no downloadable artifact",
                    "لا توجد حزمة نسخة احتياطية قابلة للتحقق");
        }
        Path path = resolveArtifactPath(job);
        if (path == null) {
            return new ValidationResultDto(id, false, job.getChecksum(), null,
                    "Backup path is outside the approved backup directory",
                    "مسار النسخة خارج مجلد النسخ الاحتياطي المعتمد");
        }
        if (!Files.exists(path)) {
            return new ValidationResultDto(id, false, job.getChecksum(), null,
                    "Backup file is missing",
                    "ملف النسخة الاحتياطية غير موجود");
        }
        String actual = checksumService.sha256(path);
        boolean valid = actual.equalsIgnoreCase(job.getChecksum());
        return new ValidationResultDto(id, valid, job.getChecksum(), actual,
                valid ? "Checksum is valid" : "Checksum mismatch",
                valid ? "التحقق ناجح: checksum مطابق" : "فشل التحقق: checksum غير مطابق");
    }

    @Transactional(readOnly = true)
    public Resource download(Long id) {
        SystemBackupJob job = findJob(id);
        if (job.getStatus() != BackupStatus.SUCCESS || job.getFilePath() == null) {
            throw new IllegalStateException("النسخة الاحتياطية غير متاحة للتنزيل.");
        }
        Path path = resolveArtifactPath(job);
        if (path == null) {
            throw new IllegalStateException("مسار النسخة خارج مجلد النسخ الاحتياطي المعتمد. مرفوض.");
        }
        if (!Files.exists(path)) {
            throw new IllegalStateException("ملف النسخة الاحتياطية غير موجود.");
        }
        return new FileSystemResource(path);
    }

    @Transactional(readOnly = true)
    public String downloadFileName(Long id) {
        return findJob(id).getFileName();
    }

    /**
     * Resolves a stored artifact path, refusing anything that escapes the configured
     * backup root. Guards against a stale row pointing outside the approved directory.
     */
    private Path resolveArtifactPath(SystemBackupJob job) {
        Path path = Path.of(job.getFilePath()).toAbsolutePath().normalize();
        return path.startsWith(settingsService.localBackupPath()) ? path : null;
    }

    private SystemBackupJob findJob(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("النسخة الاحتياطية المطلوبة غير موجودة."));
    }

    private void audit(String username, Long jobId, String details) {
        auditLogService.ifPresent(service -> {
            try {
                service.createAuditLog("CREATED", "SYSTEM_SETTING", jobId, details, null, username, null, null);
            } catch (Exception e) {
                log.warn("[BKP] Could not write backup audit entry: {}", e.getMessage());
            }
        });
    }

    private static String joinWarnings(List<String> warnings) {
        return warnings.isEmpty() ? null : String.join("\n", warnings);
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private void cleanupWorkingDirectory(Path workingDir) {
        if (workingDir == null) {
            return;
        }
        try (var stream = Files.walk(workingDir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // best-effort cleanup
                }
            });
        } catch (Exception e) {
            log.warn("[BKP] Could not clean backup working directory {}: {}", workingDir, e.getMessage());
        }
    }

    private String resolveGitCommit() {
        try {
            Path head = Path.of(".git", "HEAD");
            if (!Files.exists(head)) {
                return null;
            }
            String headValue = Files.readString(head, StandardCharsets.UTF_8).trim();
            if (headValue.startsWith("ref:")) {
                Path refPath = Path.of(".git", headValue.substring(4).trim());
                return Files.exists(refPath) ? Files.readString(refPath, StandardCharsets.UTF_8).trim() : null;
            }
            return headValue;
        } catch (Exception ignored) {
            return null;
        }
    }
}
