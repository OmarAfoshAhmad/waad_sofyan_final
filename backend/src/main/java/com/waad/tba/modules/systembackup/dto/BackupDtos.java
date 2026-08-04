package com.waad.tba.modules.systembackup.dto;

import com.waad.tba.modules.systembackup.entity.BackupStatus;
import com.waad.tba.modules.systembackup.entity.BackupType;

import java.time.LocalDateTime;
import java.util.List;

public final class BackupDtos {
    private BackupDtos() {
    }

    public record BackupSettingsDto(
            Boolean localEnabled,
            String localDisplayName,
            String localPath,
            String localHostPath,
            String localContainerPath,
            String localDestinationType,
            Integer retentionDays,
            Boolean autoBackupEnabled,
            String autoBackupType,
            Integer autoBackupHour,
            Integer autoBackupMinute,
            LocalDateTime lastAutoBackupAt,
            String lastAutoBackupStatus,
            String lastAutoBackupMessage,
            LocalDateTime lastPurgeAt,
            String lastPurgeStatus,
            String lastPurgeMessage
    ) {
    }

    /** One backup considered for retention purge. */
    public record PurgeCandidateDto(
            Long backupId,
            String fileName,
            LocalDateTime startedAt,
            Long fileSize,
            boolean deleted,
            String reason
    ) {
    }

    public record PurgeResultDto(
            boolean dryRun,
            int retentionDays,
            long candidateCount,
            long deletedCount,
            long bytesFreed,
            Long keptLatestSuccessId,
            List<PurgeCandidateDto> items,
            String messageAr
    ) {
    }

    /** Rich pre-restore verification result. */
    public record RestoreVerificationDto(
            Long backupId,
            boolean valid,
            boolean fileExists,
            boolean readable,
            boolean sizePositive,
            boolean checksumOk,
            boolean metadataReadable,
            boolean dumpRestorable,
            String message,
            String messageAr
    ) {
    }

    /** Result of a safe restore rehearsal (no production DB is touched). */
    public record RestoreRehearsalDto(
            Long backupId,
            boolean success,
            long durationMs,
            Long backupSize,
            String method,
            String message,
            String messageAr
    ) {
    }

    public record CreateBackupRequest(
            BackupType type,
            String note
    ) {
    }

    public record BackupJobDto(
            Long id,
            BackupType type,
            BackupStatus status,
            String fileName,
            Long fileSize,
            String checksum,
            String note,
            String createdBy,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            Long durationMs,
            String errorMessage,
            String environment,
            String gitCommit,
            Boolean encrypted,
            String destinationPath,
            String backupFormat,
            String warnings
    ) {
    }

    public record BackupStatusDto(
            BackupJobDto lastBackup,
            long backupCount,
            long successfulBackupCount,
            long failedBackupCount,
            String localPath,
            String localHostPath,
            String localContainerPath,
            boolean localPathConfigured,
            boolean localPathWritable,
            String localPathStatus,
            Long localUsableSpace,
            Long lastBackupSize
    ) {
    }

    public record ValidationResultDto(
            Long backupId,
            boolean valid,
            String expectedChecksum,
            String actualChecksum,
            String message,
            String messageAr
    ) {
    }

    public record BackupManifest(
            Long backupId,
            BackupType backupType,
            LocalDateTime createdAt,
            LocalDateTime completedAt,
            String environment,
            String gitCommit,
            List<String> includedComponents,
            String createdBy,
            String note,
            String checksum,
            Long fileSize,
            String backupFormat,
            String backupPath,
            List<String> warnings
    ) {
    }
}
