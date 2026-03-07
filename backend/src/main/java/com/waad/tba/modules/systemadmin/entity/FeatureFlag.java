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
 * Feature Flag Entity
 * Phase 2 - System Administration
 * 
 * Controls feature availability at module level.
 * SUPER_ADMIN can toggle features on/off dynamically.
 */
@Entity
@Table(name = "feature_flags")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class FeatureFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier for the feature
     * Examples: MEMBERS_MODULE_ENABLED, CLAIMS_MODULE_ENABLED
     */
    @Column(name = "flag_key", unique = true, nullable = false, length = 100)
    private String flagKey;

    /**
     * Human-readable feature name
     */
    @Column(name = "flag_name", nullable = false)
    private String flagName;

    /**
     * Detailed description of what this feature controls
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Whether the feature is currently enabled
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * JSON array of roles that this flag applies to
     * Example: ["EMPLOYER", "ADMIN"]
     * If null/empty, applies to all roles
     */
    @Column(name = "role_filters", columnDefinition = "text")
    private String roleFilters;

    /**
     * Audit fields
     */
    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
