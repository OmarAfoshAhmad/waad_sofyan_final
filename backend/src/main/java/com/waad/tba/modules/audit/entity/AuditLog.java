package com.waad.tba.modules.audit.entity;

import com.waad.tba.modules.audit.enums.AuditAction;
import com.waad.tba.modules.audit.enums.AuditSource;
import com.waad.tba.modules.audit.enums.EntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Immutable legal-grade audit log for medical claim platform.
 *
 * Insert-only entity: no updates and no deletes are allowed.
 */
@Entity
@Table(name = "medical_audit_logs", indexes = {
        @Index(name = "idx_med_audit_entity_id", columnList = "entity_id"),
        @Index(name = "idx_med_audit_timestamp", columnList = "event_timestamp"),
        @Index(name = "idx_med_audit_correlation", columnList = "correlation_id")
})
@Immutable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(updatable = false, nullable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false, length = 40)
    private EntityType entityType;

    @Column(name = "entity_id", nullable = false, updatable = false, length = 128)
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 50)
    private AuditAction action;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "user_role", nullable = false, updatable = false, length = 100)
    private String role;

    @Column(name = "event_timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    @Column(name = "reason", columnDefinition = "text", updatable = false)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", columnDefinition = "jsonb", updatable = false)
    private String beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", columnDefinition = "jsonb", updatable = false)
    private String afterState;

    @Column(name = "correlation_id", nullable = false, updatable = false, length = 100)
    private String correlationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false, length = 20)
    private AuditSource source;

    @Column(name = "schema_version", nullable = false, updatable = false)
    @Builder.Default
    private Integer version = 1;

    @PrePersist
    void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (version == null) {
            version = 1;
        }
    }

    @PreUpdate
    void blockUpdate() {
        throw new UnsupportedOperationException("AuditLog is immutable and cannot be updated");
    }

    @PreRemove
    void blockDelete() {
        throw new UnsupportedOperationException("AuditLog is immutable and cannot be deleted");
    }
}
