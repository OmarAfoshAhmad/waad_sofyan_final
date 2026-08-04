package com.waad.tba.modules.systembackup.repository;

import com.waad.tba.modules.systembackup.entity.SystemBackupSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemBackupSettingsRepository extends JpaRepository<SystemBackupSettings, Long> {
}
