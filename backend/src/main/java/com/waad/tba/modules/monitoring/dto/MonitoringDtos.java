package com.waad.tba.modules.monitoring.dto;

import java.time.LocalDateTime;
import java.util.List;

public final class MonitoringDtos {
    private MonitoringDtos() {
    }

    public record MonitoringSettingsDto(
            Boolean telegramEnabled,
            Boolean tokenConfigured,
            String maskedBotToken,
            String botToken,
            String chatId,
            String threadId,
            String alertEnvironment,
            Integer minIntervalSeconds,
            Boolean recoveryEnabled,
            LocalDateTime updatedAt,
            String updatedBy,
            LocalDateTime lastTestAt,
            String lastTestStatus,
            String lastTestMessage,
            Boolean automaticMonitoringEnabled,
            Integer checkIntervalSeconds,
            Integer diskWarningPercent,
            Integer diskCriticalPercent,
            Integer maxBackupAgeHours,
            Integer repeatedErrorThreshold,
            Integer repeatedErrorWindowMinutes,
            Integer alertCooldownSeconds,
            LocalDateTime lastAutoCheckAt,
            String lastAutoCheckStatus,
            String lastAutoCheckMessage,
            LocalDateTime lastExternalHeartbeatAt,
            String lastExternalHeartbeatSource,
            String lastExternalHeartbeatStatus,
            RecentAlertStateDto lastAlertState
    ) {
    }

    public record TelegramTestResultDto(
            boolean success,
            String messageAr,
            LocalDateTime testedAt
    ) {
    }

    public record HealthCardDto(
            String key,
            String titleAr,
            String status,
            String descriptionAr,
            String details,
            LocalDateTime checkedAt
    ) {
    }

    public record SystemHealthDto(
            String overallStatus,
            String environment,
            String gitCommit,
            LocalDateTime serverTime,
            List<HealthCardDto> cards
    ) {
    }

    public record RecentAlertStateDto(
            String alertKey,
            String status,
            Integer severity,
            LocalDateTime firstDetectedAt,
            LocalDateTime lastDetectedAt,
            LocalDateTime lastSentAt,
            LocalDateTime recoveredAt,
            String lastSummary,
            Integer alertCount,
            LocalDateTime updatedAt
    ) {
    }
}
