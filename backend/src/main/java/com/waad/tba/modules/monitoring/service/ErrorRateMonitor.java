package com.waad.tba.modules.monitoring.service;

import com.waad.tba.modules.monitoring.entity.SystemMonitoringErrorEvent;
import com.waad.tba.modules.monitoring.repository.SystemMonitoringErrorEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ErrorRateMonitor {

    private final SystemMonitoringErrorEventRepository repository;

    @Transactional
    public void recordBackendError(int statusCode, String method, String path) {
        repository.save(SystemMonitoringErrorEvent.builder()
                .occurredAt(LocalDateTime.now())
                .statusCode(statusCode)
                .method(safe(method, 20))
                .path(safe(path, 500))
                .build());
    }

    @Transactional(readOnly = true)
    public long countRecentErrors(int windowMinutes) {
        int safeWindow = windowMinutes < 1 ? 15 : windowMinutes;
        return repository.countByOccurredAtAfter(LocalDateTime.now().minusMinutes(safeWindow));
    }

    @Transactional
    public void purgeOldEvents() {
        repository.deleteByOccurredAtBefore(LocalDateTime.now().minusDays(7));
    }

    private static String safe(String value, int max) {
        if (value == null) {
            return null;
        }
        String cleaned = value.replaceAll("(?i)(token|password|secret)=([^&\\s]+)", "$1=***");
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }
}
