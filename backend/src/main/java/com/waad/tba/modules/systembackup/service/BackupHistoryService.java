package com.waad.tba.modules.systembackup.service;

import com.waad.tba.modules.systembackup.dto.BackupDtos.BackupJobDto;
import com.waad.tba.modules.systembackup.entity.SystemBackupJob;
import org.springframework.stereotype.Service;

@Service
public class BackupHistoryService {
    public BackupJobDto toDto(SystemBackupJob job) {
        if (job == null) return null;
        return new BackupJobDto(
                job.getId(),
                job.getType(),
                job.getStatus(),
                job.getFileName(),
                job.getFileSize(),
                job.getChecksum(),
                job.getNote(),
                job.getCreatedBy(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getDurationMs(),
                job.getErrorMessage(),
                job.getEnvironment(),
                job.getGitCommit(),
                job.getEncrypted(),
                job.getDestinationPath(),
                job.getBackupFormat(),
                job.getWarnings()
        );
    }
}
