package com.waad.tba.modules.systembackup.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_backup_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemBackupSettings {

    @Id
    private Long id;

    @Column(name = "local_enabled", nullable = false)
    @Builder.Default
    private Boolean localEnabled = true;

    @Column(name = "local_display_name", nullable = false, length = 150)
    @Builder.Default
    private String localDisplayName = "المسار المحلي الأساسي";

    @Column(name = "local_path", nullable = false, length = 1000)
    private String localPath;

    @Column(name = "retention_days", nullable = false)
    @Builder.Default
    private Integer retentionDays = 30;

    @Column(name = "auto_backup_enabled", nullable = false)
    @Builder.Default
    private Boolean autoBackupEnabled = false;

    @Column(name = "auto_backup_type", nullable = false, length = 40)
    @Builder.Default
    private String autoBackupType = "FULL_SYSTEM";

    @Column(name = "auto_backup_hour", nullable = false)
    @Builder.Default
    private Integer autoBackupHour = 2;

    @Column(name = "auto_backup_minute", nullable = false)
    @Builder.Default
    private Integer autoBackupMinute = 0;

    @Column(name = "last_auto_backup_at")
    private LocalDateTime lastAutoBackupAt;

    @Column(name = "last_auto_backup_status", length = 30)
    private String lastAutoBackupStatus;

    @Column(name = "last_auto_backup_message", length = 500)
    private String lastAutoBackupMessage;

    @Column(name = "last_purge_at")
    private LocalDateTime lastPurgeAt;

    @Column(name = "last_purge_status", length = 30)
    private String lastPurgeStatus;

    @Column(name = "last_purge_message", length = 500)
    private String lastPurgeMessage;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
