package com.waad.tba.modules.systembackup.service;

import com.waad.tba.modules.systembackup.entity.SystemBackupJob;
import com.waad.tba.modules.systembackup.repository.SystemBackupJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists backup job rows in their own short transactions.
 *
 * A backup can run for minutes (pg_dump plus zipping the whole uploads tree). Doing that
 * inside a single transaction would hold a pooled connection for the entire run and, worse,
 * would keep the initial RUNNING row invisible to every other session until the backup
 * finished — making "is a backup running?" unanswerable from outside.
 *
 * This lives in a separate bean because Spring's transaction proxy does not intercept
 * self-invocation, so REQUIRES_NEW would be silently ignored if these methods sat on
 * {@link BackupService} itself.
 */
@Service
@RequiredArgsConstructor
public class BackupJobWriter {

    private final SystemBackupJobRepository jobRepository;

    /** Commits the job row immediately so its RUNNING state is visible to other sessions. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SystemBackupJob save(SystemBackupJob job) {
        return jobRepository.save(job);
    }
}
