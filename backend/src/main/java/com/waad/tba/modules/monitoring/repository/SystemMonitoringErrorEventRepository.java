package com.waad.tba.modules.monitoring.repository;

import com.waad.tba.modules.monitoring.entity.SystemMonitoringErrorEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface SystemMonitoringErrorEventRepository extends JpaRepository<SystemMonitoringErrorEvent, Long> {
    long countByOccurredAtAfter(LocalDateTime occurredAt);
    void deleteByOccurredAtBefore(LocalDateTime occurredAt);
}
