package com.waad.tba.modules.monitoring.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_monitoring_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemMonitoringSettings {

    @Id
    private Long id;

    @Column(name = "telegram_enabled", nullable = false)
    @Builder.Default
    private Boolean telegramEnabled = false;

    @Column(name = "telegram_bot_token", length = 500)
    private String telegramBotToken;

    @Column(name = "telegram_chat_id", length = 120)
    private String telegramChatId;

    @Column(name = "telegram_thread_id", length = 120)
    private String telegramThreadId;

    @Column(name = "alert_environment", length = 80, nullable = false)
    @Builder.Default
    private String alertEnvironment = "local";

    @Column(name = "min_interval_seconds", nullable = false)
    @Builder.Default
    private Integer minIntervalSeconds = 300;

    @Column(name = "recovery_enabled", nullable = false)
    @Builder.Default
    private Boolean recoveryEnabled = true;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_test_at")
    private LocalDateTime lastTestAt;

    @Column(name = "last_test_status", length = 30)
    private String lastTestStatus;

    @Column(name = "last_test_message", length = 500)
    private String lastTestMessage;

    @Column(name = "automatic_monitoring_enabled", nullable = false)
    @Builder.Default
    private Boolean automaticMonitoringEnabled = false;

    @Column(name = "check_interval_seconds", nullable = false)
    @Builder.Default
    private Integer checkIntervalSeconds = 300;

    @Column(name = "disk_warning_percent", nullable = false)
    @Builder.Default
    private Integer diskWarningPercent = 80;

    @Column(name = "disk_critical_percent", nullable = false)
    @Builder.Default
    private Integer diskCriticalPercent = 90;

    @Column(name = "max_backup_age_hours", nullable = false)
    @Builder.Default
    private Integer maxBackupAgeHours = 72;

    @Column(name = "repeated_error_threshold", nullable = false)
    @Builder.Default
    private Integer repeatedErrorThreshold = 10;

    @Column(name = "repeated_error_window_minutes", nullable = false)
    @Builder.Default
    private Integer repeatedErrorWindowMinutes = 15;

    @Column(name = "alert_cooldown_seconds", nullable = false)
    @Builder.Default
    private Integer alertCooldownSeconds = 1800;

    @Column(name = "last_auto_check_at")
    private LocalDateTime lastAutoCheckAt;

    @Column(name = "last_auto_check_status", length = 30)
    private String lastAutoCheckStatus;

    @Column(name = "last_auto_check_message", length = 500)
    private String lastAutoCheckMessage;

    @Column(name = "last_external_heartbeat_at")
    private LocalDateTime lastExternalHeartbeatAt;

    @Column(name = "last_external_heartbeat_source", length = 120)
    private String lastExternalHeartbeatSource;

    @Column(name = "last_external_heartbeat_status", length = 30)
    private String lastExternalHeartbeatStatus;
}
