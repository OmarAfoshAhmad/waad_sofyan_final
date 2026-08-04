package com.waad.tba.modules.monitoring.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_monitoring_alert_state")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemMonitoringAlertState {

    @Id
    @Column(name = "alert_key", length = 80)
    private String alertKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private MonitoringAlertStatus status = MonitoringAlertStatus.HEALTHY;

    @Column(nullable = false)
    @Builder.Default
    private Integer severity = 0;

    @Column(name = "first_detected_at")
    private LocalDateTime firstDetectedAt;

    @Column(name = "last_detected_at")
    private LocalDateTime lastDetectedAt;

    @Column(name = "last_sent_at")
    private LocalDateTime lastSentAt;

    @Column(name = "recovered_at")
    private LocalDateTime recoveredAt;

    @Column(name = "last_summary", length = 1000)
    private String lastSummary;

    @Column(name = "alert_count", nullable = false)
    @Builder.Default
    private Integer alertCount = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
