package com.waad.tba.common.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * System Settings Entity.
 * 
 * Stores configurable system-wide settings that can be modified by admins
 * without code changes or redeployment.
 * 
 * Each setting has a unique key (e.g., "CLAIM_SLA_DAYS") and a value.
 * Settings are cached for performance and reloaded on update.
 * 
 * @since Phase 1 - SLA Implementation
 */
@Entity
@Table(name = "system_settings", uniqueConstraints = {
    @UniqueConstraint(name = "uk_setting_key", columnNames = "setting_key")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class SystemSetting {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Unique setting key.
     * 
     * Convention: UPPERCASE_WITH_UNDERSCORES
     * 
     * Examples:
     * - CLAIM_SLA_DAYS
     * - MAX_FILE_UPLOAD_SIZE_MB
     * - SESSION_TIMEOUT_MINUTES
     */
    @Column(name = "setting_key", length = 100, nullable = false, unique = true)
    private String settingKey;
    
    /**
     * Setting value (stored as string).
     * 
     * For numeric values, convert when retrieving.
     * For boolean, use "true"/"false".
     * For JSON, store as valid JSON string.
     */
    @Column(name = "setting_value", columnDefinition = "TEXT", nullable = false)
    private String settingValue;
    
    /**
     * Setting data type for proper parsing.
     * 
     * Types: INTEGER, DECIMAL, BOOLEAN, STRING, JSON
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", length = 20, nullable = false)
    @Builder.Default
    private SettingValueType valueType = SettingValueType.STRING;
    
    /**
     * Human-readable description of the setting.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    /**
     * Category for grouping settings.
     * 
     * Examples: CLAIMS, MEMBERS, SECURITY, INTEGRATIONS
     */
    @Column(name = "category", length = 50)
    private String category;
    
    /**
     * Whether this setting is editable by admins.
     * 
     * Some critical settings may be read-only to prevent system breakage.
     */
    @Column(name = "is_editable", nullable = false)
    @Builder.Default
    private Boolean isEditable = true;
    
    /**
     * Default value for reset functionality.
     */
    @Column(name = "default_value", columnDefinition = "TEXT")
    private String defaultValue;
    
    /**
     * Validation rules (e.g., "min:1,max:30" for numeric values).
     */
    @Column(name = "validation_rules", columnDefinition = "TEXT")
    private String validationRules;
    
    /**
     * Whether this setting is active.
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "updated_by", length = 100)
    private String updatedBy;
    
    /**
     * Setting value types.
     */
    public enum SettingValueType {
        INTEGER,
        DECIMAL,
        BOOLEAN,
        STRING,
        JSON
    }
    
    /**
     * Get setting value as Integer.
     */
    @Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Integer getValueAsInteger() {
        if (settingValue == null) {
            return null;
        }
        try {
            return Integer.parseInt(settingValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Get setting value as Boolean.
     */
    @Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Boolean getValueAsBoolean() {
        if (settingValue == null) {
            return null;
        }
        return Boolean.parseBoolean(settingValue);
    }
}
