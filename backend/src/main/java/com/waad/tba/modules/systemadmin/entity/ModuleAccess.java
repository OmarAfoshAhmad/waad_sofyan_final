package com.waad.tba.modules.systemadmin.entity;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Module Access Control Entity
 * Phase 2 - System Administration
 * 
 * Defines which roles can access specific modules.
 * Linked to Feature Flags for dynamic enabling/disabling.
 */
@Entity
@Table(name = "module_access")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ModuleAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Human-readable module name
     * Example: "Members Management", "Claims Processing"
     */
    @Column(name = "module_name", nullable = false, length = 100)
    private String moduleName;

    /**
     * Unique module identifier
     * Example: MEMBERS_MODULE, CLAIMS_MODULE
     */
    @Column(name = "module_key", unique = true, nullable = false, length = 100)
    private String moduleKey;

    /**
     * Module description
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * JSON array of roles allowed to access this module
     * Example: ["SUPER_ADMIN", "ADMIN", "EMPLOYER"]
     */
    @Column(name = "allowed_roles", nullable = false, columnDefinition = "text")
    private String allowedRoles;

    /**
     * JSON array of required permissions
     * Example: ["VIEW_MEMBERS", "MANAGE_MEMBERS"]
     */
    @Column(name = "required_permissions", columnDefinition = "text")
    private String requiredPermissions;

    /**
     * Associated feature flag key
     * If feature flag is disabled, module is inaccessible
     */
    @Column(name = "feature_flag_key", length = 100)
    private String featureFlagKey;

    /**
     * Whether this module is currently active
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Audit fields
     */
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
