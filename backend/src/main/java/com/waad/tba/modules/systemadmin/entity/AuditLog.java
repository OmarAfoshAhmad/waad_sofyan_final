package com.waad.tba.modules.systemadmin.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Audit Log Entity
 * Phase 2 - System Administration
 * 
 * Comprehensive tracking of all system actions.
 * Used by SUPER_ADMIN to monitor user activities.
 */
@Entity(name = "SystemAuditLog")
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_user", columnList = "user_id"),
        @Index(name = "idx_entity", columnList = "entity_type,entity_id"),
        @Index(name = "idx_timestamp", columnList = "timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * When the action occurred
     */
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * User who performed the action
     */
    @Column(name = "user_id")
    private Long userId;

    @Column(length = 50)
    private String username;

    /**
     * Action type
     * Examples: USER_CREATED, USER_UPDATED, USER_DELETED, ROLE_ASSIGNED, etc.
     */
    @Column(nullable = false, length = 100)
    private String action;

    /**
     * Entity affected by the action
     */
    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    /**
     * Detailed description of the action
     */
    @Column(columnDefinition = "TEXT")
    private String details;

    /**
     * IP address of the user
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * User's browser/client information
     */
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
}
