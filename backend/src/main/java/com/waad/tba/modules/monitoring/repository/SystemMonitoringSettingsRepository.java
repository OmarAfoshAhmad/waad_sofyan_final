package com.waad.tba.modules.monitoring.repository;

import com.waad.tba.modules.monitoring.entity.SystemMonitoringSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemMonitoringSettingsRepository extends JpaRepository<SystemMonitoringSettings, Long> {
}
