package com.waad.tba.modules.systembackup.repository;

import com.waad.tba.modules.systembackup.entity.BackupStatus;
import com.waad.tba.modules.systembackup.entity.SystemBackupJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SystemBackupJobRepository extends JpaRepository<SystemBackupJob, Long> {
    List<SystemBackupJob> findTop100ByOrderByStartedAtDesc();
    Optional<SystemBackupJob> findTopByOrderByStartedAtDesc();
    Optional<SystemBackupJob> findTopByStatusOrderByStartedAtDesc(BackupStatus status);
    long countByStatus(BackupStatus status);
}
