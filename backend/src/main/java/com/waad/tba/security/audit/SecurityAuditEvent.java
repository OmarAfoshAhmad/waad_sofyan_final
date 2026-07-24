package com.waad.tba.security.audit;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "security_audit_events", indexes = {
    @Index(name = "idx_audit_actor", columnList = "actor_id"),
    @Index(name = "idx_audit_action", columnList = "action_type"),
    @Index(name = "idx_audit_timestamp", columnList = "event_timestamp"),
    @Index(name = "idx_audit_correlation", columnList = "correlation_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long actorId;

    @Column(nullable = false, length = 100)
    private String actorUsername;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AuditActionType actionType;

    @Column(length = 50)
    private String targetType;

    @Column
    private Long targetId;

    @Column(length = 255)
    private String targetIdentifier;

    @Column(length = 45)
    private String requestIp;

    @Column(columnDefinition = "text")
    private String userAgent;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AuditResult result;

    @Column(columnDefinition = "text")
    private String safeReason;

    @Column(columnDefinition = "jsonb")
    private String beforeState;

    @Column(columnDefinition = "jsonb")
    private String afterState;

    @Column(nullable = false, length = 36)
    private String correlationId;

    @Column(name = "event_timestamp", nullable = false)
    private LocalDateTime eventTimestamp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (eventTimestamp == null) {
            eventTimestamp = LocalDateTime.now();
        }
    }

    public enum AuditActionType {
        LOGIN_SUCCESS,
        LOGIN_FAILED,
        LOGOUT,
        SESSION_REVOKED,
        SESSION_EXPIRED,
        PASSWORD_CHANGED,
        PASSWORD_RESET,
        PASSWORD_RESET_REQUESTED,
        ACCOUNT_CREATED,
        ACCOUNT_UPDATED,
        ACCOUNT_ACTIVATED,
        ACCOUNT_DEACTIVATED,
        ACCOUNT_LOCKED,
        PERMISSION_GRANTED,
        PERMISSION_REVOKED,
        ROLE_ASSIGNED,
        ROLE_REMOVED,
        FILE_UPLOADED,
        FILE_DOWNLOADED,
        FILE_DELETED,
        FILE_ACCESS_DENIED,
        SETTING_CHANGED,
        SETTING_ACCESSED,
        SECURITY_VIOLATION,
        CONFIGURATION_CHANGED,
        FAILED_LOGIN_ATTEMPT,
        ACCOUNT_LOCKOUT
    }

    public enum AuditResult {
        SUCCESS,
        DENIED,
        ERROR
    }
}
