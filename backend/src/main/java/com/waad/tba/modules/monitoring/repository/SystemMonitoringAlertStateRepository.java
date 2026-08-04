package com.waad.tba.modules.monitoring.repository;

import com.waad.tba.modules.monitoring.entity.SystemMonitoringAlertState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemMonitoringAlertStateRepository extends JpaRepository<SystemMonitoringAlertState, String> {
    List<SystemMonitoringAlertState> findTop20ByOrderByUpdatedAtDesc();
}
