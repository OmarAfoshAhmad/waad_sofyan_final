package com.waad.tba.modules.monitoring.service;

import com.waad.tba.modules.monitoring.dto.MonitoringDtos.MonitoringSettingsDto;
import com.waad.tba.modules.monitoring.dto.MonitoringDtos.RecentAlertStateDto;
import com.waad.tba.modules.monitoring.entity.SystemMonitoringSettings;
import com.waad.tba.modules.monitoring.repository.SystemMonitoringAlertStateRepository;
import com.waad.tba.modules.monitoring.repository.SystemMonitoringSettingsRepository;
import com.waad.tba.modules.systemadmin.service.AuditLogService;
import com.waad.tba.security.SecretEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Single-row settings for monitoring, alerting thresholds and Telegram delivery.
 *
 * The Telegram bot token is encrypted at rest via {@link SecretEncryptionService} — the
 * reference implementation stored it as plain text in a VARCHAR(500) column. Every read
 * path here works with the ciphertext; {@link TelegramAlertService} is the only place
 * that decrypts it, immediately before use.
 */
@Service
@RequiredArgsConstructor
public class MonitoringSettingsService {

    private static final long SETTINGS_ID = 1L;

    private final SystemMonitoringSettingsRepository repository;
    private final SystemMonitoringAlertStateRepository alertStateRepository;
    private final Environment environment;
    private final SecretEncryptionService secretEncryptionService;
    private final Optional<AuditLogService> auditLogService;
    private volatile SystemMonitoringSettings cachedSettings;

    @Transactional
    public SystemMonitoringSettings getOrCreate() {
        SystemMonitoringSettings settings = repository.findById(SETTINGS_ID)
                .orElseGet(() -> repository.save(defaultSettings()));
        cachedSettings = copy(settings);
        return settings;
    }

    /**
     * Used by the scheduler, which must never let a transient DB error stop the alert
     * loop: falls back to the last successfully loaded settings, then to safe defaults.
     */
    public SystemMonitoringSettings getSchedulerSettings() {
        try {
            return getOrCreate();
        } catch (Exception ignored) {
            if (cachedSettings != null) {
                return copy(cachedSettings);
            }
            return defaultSettings();
        }
    }

    private SystemMonitoringSettings defaultSettings() {
        String plainToken = blankToNull(env("TELEGRAM_BOT_TOKEN", null));
        return SystemMonitoringSettings.builder()
                .id(SETTINGS_ID)
                .telegramEnabled(Boolean.parseBoolean(env("TELEGRAM_ALERTS_ENABLED", "false")))
                .telegramBotToken(plainToken == null ? null : secretEncryptionService.encrypt(plainToken))
                .telegramChatId(blankToNull(env("TELEGRAM_CHAT_ID", null)))
                .telegramThreadId(blankToNull(env("TELEGRAM_ALERT_THREAD_ID", null)))
                .alertEnvironment(env("TELEGRAM_ALERT_ENVIRONMENT", activeProfile()))
                .minIntervalSeconds(parseInt(env("TELEGRAM_ALERT_MIN_INTERVAL_SECONDS", "300"), 300))
                .recoveryEnabled(Boolean.parseBoolean(env("TELEGRAM_ALERT_RECOVERY_ENABLED", "true")))
                .automaticMonitoringEnabled(Boolean.parseBoolean(env("MONITORING_AUTOMATIC_ENABLED", "false")))
                .checkIntervalSeconds(parseInt(env("MONITORING_CHECK_INTERVAL_SECONDS", "300"), 300))
                .diskWarningPercent(parseInt(env("MONITORING_DISK_WARNING_PERCENT", "80"), 80))
                .diskCriticalPercent(parseInt(env("MONITORING_DISK_CRITICAL_PERCENT", "90"), 90))
                .maxBackupAgeHours(parseInt(env("MONITORING_MAX_BACKUP_AGE_HOURS", "72"), 72))
                .repeatedErrorThreshold(parseInt(env("MONITORING_REPEATED_ERROR_THRESHOLD", "10"), 10))
                .repeatedErrorWindowMinutes(parseInt(env("MONITORING_REPEATED_ERROR_WINDOW_MINUTES", "15"), 15))
                .alertCooldownSeconds(parseInt(env("MONITORING_ALERT_COOLDOWN_SECONDS", "1800"), 1800))
                .updatedBy("SYSTEM")
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public MonitoringSettingsDto getSettings() {
        return toDto(getOrCreate());
    }

    /** True when Telegram is enabled and both bot token and chat id are configured. */
    public boolean isTelegramConfigured() {
        try {
            SystemMonitoringSettings s = getOrCreate();
            return Boolean.TRUE.equals(s.getTelegramEnabled())
                    && s.getTelegramBotToken() != null && !s.getTelegramBotToken().isBlank()
                    && s.getTelegramChatId() != null && !s.getTelegramChatId().isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public MonitoringSettingsDto update(MonitoringSettingsDto dto, String username) {
        SystemMonitoringSettings settings = getOrCreate();
        settings.setTelegramEnabled(Boolean.TRUE.equals(dto.telegramEnabled()));
        // A blank incoming token means "keep the existing one" — the DTO never echoes
        // back the real token (see toDto), so an empty field must not erase it.
        if (dto.botToken() != null && !dto.botToken().isBlank()) {
            settings.setTelegramBotToken(secretEncryptionService.encrypt(dto.botToken().trim()));
        }
        settings.setTelegramChatId(blankToNull(dto.chatId()));
        settings.setTelegramThreadId(blankToNull(dto.threadId()));
        settings.setAlertEnvironment(defaultIfBlank(dto.alertEnvironment(), activeProfile()));
        settings.setMinIntervalSeconds(
                dto.minIntervalSeconds() == null || dto.minIntervalSeconds() < 30 ? 300 : dto.minIntervalSeconds());
        settings.setRecoveryEnabled(!Boolean.FALSE.equals(dto.recoveryEnabled()));
        settings.setAutomaticMonitoringEnabled(Boolean.TRUE.equals(dto.automaticMonitoringEnabled()));
        settings.setCheckIntervalSeconds(clamp(dto.checkIntervalSeconds(), 60, 86400, 300));
        int warning = clamp(dto.diskWarningPercent(), 1, 99, 80);
        int critical = clamp(dto.diskCriticalPercent(), 2, 100, 90);
        if (critical <= warning) {
            critical = Math.min(100, warning + 1);
        }
        settings.setDiskWarningPercent(warning);
        settings.setDiskCriticalPercent(critical);
        settings.setMaxBackupAgeHours(clamp(dto.maxBackupAgeHours(), 1, 8760, 72));
        settings.setRepeatedErrorThreshold(clamp(dto.repeatedErrorThreshold(), 1, 10000, 10));
        settings.setRepeatedErrorWindowMinutes(clamp(dto.repeatedErrorWindowMinutes(), 1, 1440, 15));
        settings.setAlertCooldownSeconds(clamp(dto.alertCooldownSeconds(), 60, 86400, 1800));
        settings.setUpdatedBy(username);
        settings.setUpdatedAt(LocalDateTime.now());
        SystemMonitoringSettings saved = repository.save(settings);
        cachedSettings = copy(saved);
        auditLogService.ifPresent(service -> service.createAuditLog(
                "UPDATED", "SYSTEM_SETTING", SETTINGS_ID, "Monitoring settings updated", null, username, null, null));
        return toDto(saved);
    }

    @Transactional
    public void recordTest(boolean success, String messageAr) {
        SystemMonitoringSettings settings = getOrCreate();
        settings.setLastTestAt(LocalDateTime.now());
        settings.setLastTestStatus(success ? "SUCCESS" : "FAILED");
        settings.setLastTestMessage(safeLength(messageAr, 500));
        cachedSettings = copy(repository.save(settings));
    }

    /** Best-effort: the scheduler must keep running even if this write fails. */
    public void recordAutoCheck(String status, String messageAr, LocalDateTime checkedAt) {
        LocalDateTime checkTime = checkedAt == null ? LocalDateTime.now() : checkedAt;
        try {
            SystemMonitoringSettings settings = getOrCreate();
            settings.setLastAutoCheckAt(checkTime);
            settings.setLastAutoCheckStatus(status);
            settings.setLastAutoCheckMessage(safeLength(messageAr, 500));
            cachedSettings = copy(repository.save(settings));
        } catch (Exception ignored) {
            if (cachedSettings != null) {
                SystemMonitoringSettings snapshot = copy(cachedSettings);
                snapshot.setLastAutoCheckAt(checkTime);
                snapshot.setLastAutoCheckStatus(status);
                snapshot.setLastAutoCheckMessage(safeLength(messageAr, 500));
                cachedSettings = snapshot;
            }
        }
    }

    @Transactional
    public void recordExternalHeartbeat(String source, String status) {
        SystemMonitoringSettings settings = getOrCreate();
        settings.setLastExternalHeartbeatAt(LocalDateTime.now());
        settings.setLastExternalHeartbeatSource(safeLength(defaultIfBlank(source, "external-monitor"), 120));
        settings.setLastExternalHeartbeatStatus(safeLength(defaultIfBlank(status, "UP"), 30));
        cachedSettings = copy(repository.save(settings));
    }

    public MonitoringSettingsDto toDto(SystemMonitoringSettings settings) {
        boolean tokenConfigured = settings.getTelegramBotToken() != null && !settings.getTelegramBotToken().isBlank();
        return new MonitoringSettingsDto(
                settings.getTelegramEnabled(),
                tokenConfigured,
                tokenConfigured ? maskToken(decryptForDisplay(settings.getTelegramBotToken())) : "",
                null, // botToken: never echoed back to the client
                settings.getTelegramChatId(),
                settings.getTelegramThreadId(),
                settings.getAlertEnvironment(),
                settings.getMinIntervalSeconds(),
                settings.getRecoveryEnabled(),
                settings.getUpdatedAt(),
                settings.getUpdatedBy(),
                settings.getLastTestAt(),
                settings.getLastTestStatus(),
                settings.getLastTestMessage(),
                settings.getAutomaticMonitoringEnabled(),
                safeCheckIntervalSeconds(settings),
                safeDiskWarningPercent(settings),
                safeDiskCriticalPercent(settings),
                safeMaxBackupAgeHours(settings),
                safeRepeatedErrorThreshold(settings),
                safeRepeatedErrorWindowMinutes(settings),
                safeAlertCooldownSeconds(settings),
                settings.getLastAutoCheckAt(),
                settings.getLastAutoCheckStatus(),
                settings.getLastAutoCheckMessage(),
                settings.getLastExternalHeartbeatAt(),
                settings.getLastExternalHeartbeatSource(),
                settings.getLastExternalHeartbeatStatus(),
                latestAlertState()
        );
    }

    public int safeCheckIntervalSeconds(SystemMonitoringSettings settings) {
        return clamp(settings.getCheckIntervalSeconds(), 60, 86400, 300);
    }

    public int safeDiskWarningPercent(SystemMonitoringSettings settings) {
        return clamp(settings.getDiskWarningPercent(), 1, 99, 80);
    }

    public int safeDiskCriticalPercent(SystemMonitoringSettings settings) {
        int warning = safeDiskWarningPercent(settings);
        int critical = clamp(settings.getDiskCriticalPercent(), 2, 100, 90);
        return critical <= warning ? Math.min(100, warning + 1) : critical;
    }

    public int safeMaxBackupAgeHours(SystemMonitoringSettings settings) {
        return clamp(settings.getMaxBackupAgeHours(), 1, 8760, 72);
    }

    public int safeRepeatedErrorThreshold(SystemMonitoringSettings settings) {
        return clamp(settings.getRepeatedErrorThreshold(), 1, 10000, 10);
    }

    public int safeRepeatedErrorWindowMinutes(SystemMonitoringSettings settings) {
        return clamp(settings.getRepeatedErrorWindowMinutes(), 1, 1440, 15);
    }

    public int safeAlertCooldownSeconds(SystemMonitoringSettings settings) {
        return clamp(settings.getAlertCooldownSeconds(), 60, 86400, 1800);
    }

    private String decryptForDisplay(String ciphertext) {
        try {
            return secretEncryptionService.decrypt(ciphertext);
        } catch (Exception e) {
            // A key rotation or corrupted row must not break the settings screen.
            return "";
        }
    }

    private RecentAlertStateDto latestAlertState() {
        return alertStateRepository.findTop20ByOrderByUpdatedAtDesc().stream()
                .findFirst()
                .map(state -> new RecentAlertStateDto(
                        state.getAlertKey(),
                        state.getStatus() == null ? null : state.getStatus().name(),
                        state.getSeverity(),
                        state.getFirstDetectedAt(),
                        state.getLastDetectedAt(),
                        state.getLastSentAt(),
                        state.getRecoveredAt(),
                        state.getLastSummary(),
                        state.getAlertCount(),
                        state.getUpdatedAt()
                ))
                .orElse(null);
    }

    private String env(String key, String fallback) {
        String value = environment.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String activeProfile() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "local" : profiles[0];
    }

    private static String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        if (token.length() <= 10) {
            return "••••••";
        }
        return token.substring(0, 6) + "••••••" + token.substring(token.length() - 4);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int clamp(Integer value, int min, int max, int fallback) {
        int number = value == null ? fallback : value;
        if (number < min) {
            return min;
        }
        return Math.min(number, max);
    }

    private static String safeLength(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private static SystemMonitoringSettings copy(SystemMonitoringSettings source) {
        if (source == null) {
            return null;
        }
        return SystemMonitoringSettings.builder()
                .id(source.getId())
                .telegramEnabled(source.getTelegramEnabled())
                .telegramBotToken(source.getTelegramBotToken())
                .telegramChatId(source.getTelegramChatId())
                .telegramThreadId(source.getTelegramThreadId())
                .alertEnvironment(source.getAlertEnvironment())
                .minIntervalSeconds(source.getMinIntervalSeconds())
                .recoveryEnabled(source.getRecoveryEnabled())
                .updatedAt(source.getUpdatedAt())
                .updatedBy(source.getUpdatedBy())
                .lastTestAt(source.getLastTestAt())
                .lastTestStatus(source.getLastTestStatus())
                .lastTestMessage(source.getLastTestMessage())
                .automaticMonitoringEnabled(source.getAutomaticMonitoringEnabled())
                .checkIntervalSeconds(source.getCheckIntervalSeconds())
                .diskWarningPercent(source.getDiskWarningPercent())
                .diskCriticalPercent(source.getDiskCriticalPercent())
                .maxBackupAgeHours(source.getMaxBackupAgeHours())
                .repeatedErrorThreshold(source.getRepeatedErrorThreshold())
                .repeatedErrorWindowMinutes(source.getRepeatedErrorWindowMinutes())
                .alertCooldownSeconds(source.getAlertCooldownSeconds())
                .lastAutoCheckAt(source.getLastAutoCheckAt())
                .lastAutoCheckStatus(source.getLastAutoCheckStatus())
                .lastAutoCheckMessage(source.getLastAutoCheckMessage())
                .lastExternalHeartbeatAt(source.getLastExternalHeartbeatAt())
                .lastExternalHeartbeatSource(source.getLastExternalHeartbeatSource())
                .lastExternalHeartbeatStatus(source.getLastExternalHeartbeatStatus())
                .build();
    }
}
