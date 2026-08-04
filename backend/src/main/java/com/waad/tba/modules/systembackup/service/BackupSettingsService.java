package com.waad.tba.modules.systembackup.service;

import com.waad.tba.modules.systemadmin.service.AuditLogService;
import com.waad.tba.modules.systembackup.dto.BackupDtos.BackupSettingsDto;
import com.waad.tba.modules.systembackup.entity.SystemBackupSettings;
import com.waad.tba.modules.systembackup.repository.SystemBackupSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Single-row settings for the backup module.
 *
 * The runtime backup path is intentionally server/Docker-configured only and is never
 * taken from the request body — a browser-submitted path would be an arbitrary-write
 * primitive against the server filesystem.
 */
@Service
@RequiredArgsConstructor
public class BackupSettingsService {

    private static final long SETTINGS_ID = 1L;
    private static final String DEFAULT_DISPLAY_NAME = "المسار المحلي الأساسي";
    private static final int DEFAULT_RETENTION_DAYS = 30;
    private static final String DEFAULT_BACKUP_TYPE = "FULL_SYSTEM";
    /** Used when neither Docker/env nor application properties configure a path. */
    private static final String DEFAULT_LOCAL_PATH = "./storage/backups";

    private final SystemBackupSettingsRepository repository;
    private final Environment environment;
    private final Optional<AuditLogService> auditLogService;

    /**
     * Transactional so the lazy first-time row creation cannot be half-applied, and so
     * callers outside a transaction do not each open their own connection per access.
     */
    @Transactional
    public SystemBackupSettings getOrCreate() {
        return repository.findById(SETTINGS_ID).orElseGet(() -> repository.save(SystemBackupSettings.builder()
                .id(SETTINGS_ID)
                .localPath(defaultBackupPath())
                .updatedBy("SYSTEM")
                .updatedAt(LocalDateTime.now())
                .build()));
    }

    @Transactional
    public BackupSettingsDto getSettings() {
        return toDto(getOrCreate());
    }

    @Transactional
    public BackupSettingsDto update(BackupSettingsDto dto, String username) {
        SystemBackupSettings settings = getOrCreate();
        settings.setLocalEnabled(Boolean.TRUE.equals(dto.localEnabled()));
        settings.setLocalDisplayName(blankToDefault(dto.localDisplayName(), DEFAULT_DISPLAY_NAME));
        // Server/Docker-configured only — never accept a browser-submitted filesystem path.
        settings.setLocalPath(defaultBackupPath());
        settings.setRetentionDays(dto.retentionDays() == null || dto.retentionDays() < 1
                ? DEFAULT_RETENTION_DAYS : dto.retentionDays());
        settings.setAutoBackupEnabled(Boolean.TRUE.equals(dto.autoBackupEnabled()));
        settings.setAutoBackupType(normalizeType(dto.autoBackupType()));
        settings.setAutoBackupHour(clamp(dto.autoBackupHour(), 0, 23, 2));
        settings.setAutoBackupMinute(clamp(dto.autoBackupMinute(), 0, 59, 0));
        settings.setUpdatedBy(username);
        settings.setUpdatedAt(LocalDateTime.now());

        BackupSettingsDto result = toDto(repository.save(settings));
        audit(username,
                "Backup settings updated (retentionDays=" + settings.getRetentionDays()
                        + ", localEnabled=" + settings.getLocalEnabled()
                        + ", autoBackupEnabled=" + settings.getAutoBackupEnabled()
                        + ", autoBackupType=" + settings.getAutoBackupType()
                        + ", autoBackupAt=" + settings.getAutoBackupHour() + ":" + settings.getAutoBackupMinute() + ")");
        return result;
    }

    public Path localBackupPath() {
        return Path.of(defaultBackupPath()).toAbsolutePath().normalize();
    }

    @Transactional
    public void recordAutoBackup(String status, String messageAr) {
        SystemBackupSettings settings = getOrCreate();
        settings.setLastAutoBackupAt(LocalDateTime.now());
        settings.setLastAutoBackupStatus(status);
        settings.setLastAutoBackupMessage(truncate(messageAr));
        repository.save(settings);
    }

    @Transactional
    public void recordPurge(String status, String messageAr) {
        SystemBackupSettings settings = getOrCreate();
        settings.setLastPurgeAt(LocalDateTime.now());
        settings.setLastPurgeStatus(status);
        settings.setLastPurgeMessage(truncate(messageAr));
        repository.save(settings);
    }

    /**
     * Uses the canonical audit verb so the entry maps to a real action rather than
     * falling through to MANUAL_OVERRIDE; the specific event stays in the details text.
     */
    private void audit(String username, String details) {
        auditLogService.ifPresent(service -> service.createAuditLog(
                "UPDATED", "SYSTEM_SETTING", SETTINGS_ID, details, null, username, null, null));
    }

    private BackupSettingsDto toDto(SystemBackupSettings settings) {
        String containerPath = defaultBackupPath();
        return new BackupSettingsDto(
                settings.getLocalEnabled(),
                settings.getLocalDisplayName(),
                containerPath,
                localHostPath(),
                containerPath,
                "مسار محلي على السيرفر",
                settings.getRetentionDays(),
                settings.getAutoBackupEnabled(),
                settings.getAutoBackupType(),
                settings.getAutoBackupHour(),
                settings.getAutoBackupMinute(),
                settings.getLastAutoBackupAt(),
                settings.getLastAutoBackupStatus(),
                settings.getLastAutoBackupMessage(),
                settings.getLastPurgeAt(),
                settings.getLastPurgeStatus(),
                settings.getLastPurgeMessage()
        );
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500);
    }

    private static String normalizeType(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_BACKUP_TYPE;
        }
        String upper = value.trim().toUpperCase();
        return switch (upper) {
            case "DATABASE_ONLY", "FILES_ONLY", "FULL_SYSTEM" -> upper;
            default -> DEFAULT_BACKUP_TYPE;
        };
    }

    private static int clamp(Integer value, int min, int max, int fallback) {
        int number = value == null ? fallback : value;
        if (number < min) {
            return min;
        }
        return Math.min(number, max);
    }

    private String defaultBackupPath() {
        String configured = environment.getProperty("BACKUP_LOCAL_1_CONTAINER");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        configured = environment.getProperty("BACKUP_DIR");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        configured = environment.getProperty("waad.backup.default-path");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return DEFAULT_LOCAL_PATH;
    }

    private String localHostPath() {
        String configured = environment.getProperty("BACKUP_LOCAL_1_HOST");
        return configured == null || configured.isBlank()
                ? "مسار سيرفر مضبوط في Docker/البيئة" : configured.trim();
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
