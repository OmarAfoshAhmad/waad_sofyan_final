package com.waad.tba.modules.systembackup.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_backup_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemBackupJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BackupType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BackupStatus status;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(length = 128)
    private String checksum;

    @Column(name = "manifest_path", length = 1000)
    private String manifestPath;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(length = 40)
    private String environment;

    @Column(name = "git_commit", length = 80)
    private String gitCommit;

    @Column(nullable = false)
    @Builder.Default
    private Boolean encrypted = false;

    @Column(name = "destination_path", length = 1000)
    private String destinationPath;

    @Column(name = "backup_format", length = 30)
    private String backupFormat;

    @Column(name = "warnings", columnDefinition = "TEXT")
    private String warnings;
}
