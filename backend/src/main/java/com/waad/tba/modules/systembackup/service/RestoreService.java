package com.waad.tba.modules.systembackup.service;

import com.waad.tba.modules.systembackup.dto.BackupDtos.RestoreRehearsalDto;
import com.waad.tba.modules.systembackup.dto.BackupDtos.RestoreVerificationDto;
import com.waad.tba.modules.systembackup.entity.BackupStatus;
import com.waad.tba.modules.systembackup.entity.SystemBackupJob;
import com.waad.tba.modules.systembackup.repository.SystemBackupJobRepository;
import com.waad.tba.modules.systembackup.service.BackupStorageService.ProcessResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pre-restore verification and a SAFE restore rehearsal.
 * The rehearsal uses `pg_restore --list`, which parses the dump's table of contents
 * without touching any database — so it is safe to run even in production.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RestoreService {

    private final SystemBackupJobRepository jobRepository;
    private final BackupSettingsService settingsService;
    private final BackupStorageService storageService;
    private final BackupChecksumService checksumService;

    @Transactional(readOnly = true)
    public RestoreVerificationDto verify(Long id) {
        SystemBackupJob job = jobRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Backup job not found"));

        if (job.getStatus() != BackupStatus.SUCCESS || job.getFilePath() == null) {
            return invalid(id, "النسخة غير مكتملة أو لا تحتوي على ملف قابل للاستعادة.");
        }

        Path backupRoot = settingsService.localBackupPath();
        Path path = Path.of(job.getFilePath()).toAbsolutePath().normalize();
        if (!path.startsWith(backupRoot)) {
            return invalid(id, "مسار النسخة خارج مجلد النسخ الاحتياطي المعتمد. مرفوض.");
        }

        boolean fileExists = Files.exists(path);
        if (!fileExists) {
            return invalid(id, "ملف النسخة الاحتياطية غير موجود.");
        }
        boolean readable = Files.isReadable(path);
        long size;
        try {
            size = Files.size(path);
        } catch (Exception e) {
            size = 0;
        }
        boolean sizePositive = size > 0;

        boolean checksumOk = false;
        if (job.getChecksum() != null && readable) {
            try {
                checksumOk = checksumService.sha256(path).equalsIgnoreCase(job.getChecksum());
            } catch (Exception e) {
                checksumOk = false;
            }
        }

        boolean metadataReadable = storageService.readManifestFromArchive(path) != null;

        boolean dumpRestorable;
        if (storageService.archiveContainsDatabaseDump(path)) {
            dumpRestorable = rehearseDump(path).success();
        } else {
            // Files-only backup: no DB dump to restore — treated as N/A (restorable).
            dumpRestorable = true;
        }

        boolean valid = fileExists && readable && sizePositive && checksumOk && metadataReadable && dumpRestorable;
        String messageAr = valid
                ? "التحقق ناجح: الملف موجود وقابل للقراءة، checksum مطابق، البيانات الوصفية سليمة، والنسخة قابلة للاستعادة."
                : "فشل التحقق. راجع التفاصيل: " + summarize(fileExists, readable, sizePositive, checksumOk, metadataReadable, dumpRestorable);

        return new RestoreVerificationDto(id, valid, fileExists, readable, sizePositive, checksumOk,
                metadataReadable, dumpRestorable, valid ? "verified" : "verification failed", messageAr);
    }

    @Transactional(readOnly = true)
    public RestoreRehearsalDto rehearse(Long id) {
        long start = System.currentTimeMillis();
        SystemBackupJob job = jobRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Backup job not found"));

        if (job.getStatus() != BackupStatus.SUCCESS || job.getFilePath() == null) {
            return rehearsalFail(id, start, null, "النسخة غير مكتملة أو لا تحتوي على ملف قابل للاستعادة.");
        }
        Path backupRoot = settingsService.localBackupPath();
        Path path = Path.of(job.getFilePath()).toAbsolutePath().normalize();
        if (!path.startsWith(backupRoot)) {
            return rehearsalFail(id, start, job.getFileSize(), "مسار النسخة خارج مجلد النسخ الاحتياطي المعتمد. مرفوض.");
        }
        if (!Files.exists(path)) {
            return rehearsalFail(id, start, job.getFileSize(), "ملف النسخة الاحتياطية غير موجود.");
        }
        if (!storageService.archiveContainsDatabaseDump(path)) {
            long dur = System.currentTimeMillis() - start;
            return new RestoreRehearsalDto(id, true, dur, job.getFileSize(), "pg_restore --list",
                    "no database component", "هذه النسخة لا تحتوي قاعدة بيانات (ملفات فقط)؛ لا حاجة لاختبار استعادة القاعدة.");
        }

        RehearsalOutcome outcome = rehearseDump(path);
        long dur = System.currentTimeMillis() - start;
        if (outcome.success()) {
            return new RestoreRehearsalDto(id, true, dur, job.getFileSize(), "pg_restore --list",
                    "restorable", "اختبار الاستعادة ناجح: ملف قاعدة البيانات قابل للقراءة والاستعادة (بدون المساس بقاعدة التشغيل).");
        }
        return new RestoreRehearsalDto(id, false, dur, job.getFileSize(), "pg_restore --list",
                "not restorable", "فشل اختبار الاستعادة: تعذّرت قراءة أرشيف قاعدة البيانات. " + outcome.safeError());
    }

    private RehearsalOutcome rehearseDump(Path archive) {
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("waad-restore-rehearsal-");
            Path dump = storageService.extractDatabaseDump(archive, workDir);
            ProcessResult result = storageService.pgRestoreList(dump);
            if (result.ok()) {
                return new RehearsalOutcome(true, null);
            }
            return new RehearsalOutcome(false, safeTail(result.output()));
        } catch (Exception e) {
            return new RehearsalOutcome(false, e.getMessage());
        } finally {
            cleanup(workDir);
        }
    }

    private static void cleanup(Path dir) {
        if (dir == null) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // best-effort
                }
            });
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private RestoreVerificationDto invalid(Long id, String messageAr) {
        return new RestoreVerificationDto(id, false, false, false, false, false, false, false,
                "verification failed", messageAr);
    }

    private RestoreRehearsalDto rehearsalFail(Long id, long start, Long size, String messageAr) {
        return new RestoreRehearsalDto(id, false, System.currentTimeMillis() - start, size,
                "pg_restore --list", "failed", messageAr);
    }

    private static String summarize(boolean file, boolean readable, boolean size, boolean checksum,
                                    boolean metadata, boolean dump) {
        StringBuilder sb = new StringBuilder();
        if (!file) sb.append("الملف مفقود؛ ");
        if (!readable) sb.append("غير قابل للقراءة؛ ");
        if (!size) sb.append("الحجم صفر؛ ");
        if (!checksum) sb.append("checksum غير مطابق؛ ");
        if (!metadata) sb.append("البيانات الوصفية غير قابلة للقراءة؛ ");
        if (!dump) sb.append("أرشيف قاعدة البيانات غير قابل للاستعادة؛ ");
        return sb.toString();
    }

    private static String safeTail(String output) {
        if (output == null) {
            return "";
        }
        String trimmed = output.trim();
        return trimmed.length() > 300 ? trimmed.substring(trimmed.length() - 300) : trimmed;
    }

    private record RehearsalOutcome(boolean success, String safeError) {
    }
}
