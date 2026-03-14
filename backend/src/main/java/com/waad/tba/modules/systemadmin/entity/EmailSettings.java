package com.waad.tba.modules.systemadmin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email_address")
    private String emailAddress;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "smtp_host")
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_username")
    private String smtpUsername;

    @Column(name = "smtp_password")
    private String smtpPassword;

    @Column(name = "imap_host")
    private String imapHost;

    @Column(name = "imap_port")
    private Integer imapPort;

    @Column(name = "imap_username")
    private String imapUsername;

    @Column(name = "imap_password")
    private String imapPassword;

    @Column(name = "encryption_type")
    private String encryptionType;

    @Column(name = "listener_enabled")
    private Boolean listenerEnabled;

    @Column(name = "sync_interval_mins")
    private Integer syncIntervalMins;

    @Column(name = "subject_filter")
    private String subjectFilter;

    @Column(name = "only_from_providers")
    private Boolean onlyFromProviders;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @Column(name = "is_active")
    private Boolean isActive;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_by")
    private String updatedBy;
}
