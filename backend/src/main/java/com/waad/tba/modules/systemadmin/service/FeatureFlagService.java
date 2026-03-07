package com.waad.tba.modules.systemadmin.service;

import com.waad.tba.modules.systemadmin.dto.FeatureFlagDto;
import com.waad.tba.modules.systemadmin.entity.FeatureFlag;
import com.waad.tba.modules.systemadmin.repository.FeatureFlagRepository;
import com.waad.tba.modules.systemadmin.repository.AuditLogRepository;
import com.waad.tba.modules.systemadmin.entity.AuditLog;
import com.waad.tba.common.exception.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Feature Flag Service
 * Phase 2 - System Administration
 * 
 * Manages feature toggles for dynamic module enabling/disabling
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /**
     * Get all feature flags
     */
    @Transactional(readOnly = true)
    public List<FeatureFlagDto> getAllFeatureFlags() {
        log.info("Fetching all feature flags");
        return featureFlagRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Get feature flag by key
     */
    @Transactional(readOnly = true)
    public FeatureFlagDto getFeatureFlagByKey(String flagKey) {
        log.info("Fetching feature flag by key: {}", flagKey);
        FeatureFlag flag = featureFlagRepository.findByFlagKey(flagKey)
                .orElseThrow(() -> new ResourceNotFoundException("Feature flag not found: " + flagKey));
        return toDto(flag);
    }

    /**
     * Create new feature flag
     */
    @Transactional
    public FeatureFlagDto createFeatureFlag(FeatureFlagDto dto, String createdBy) {
        log.info("Creating feature flag: {}", dto.getFlagKey());

        if (featureFlagRepository.existsByFlagKey(dto.getFlagKey())) {
            throw new IllegalArgumentException("Feature flag already exists: " + dto.getFlagKey());
        }

        FeatureFlag flag = FeatureFlag.builder()
                .flagKey(dto.getFlagKey())
                .flagName(dto.getFlagName())
                .description(dto.getDescription())
                .enabled(dto.getEnabled())
                .roleFilters(toJson(dto.getRoleFilters()))
                .createdBy(createdBy)
                .build();

        FeatureFlag saved = featureFlagRepository.save(flag);

        // Audit log
        createAuditLog("FEATURE_FLAG_CREATED", "FeatureFlag", saved.getId(),
                "Created feature flag: " + saved.getFlagKey(), createdBy);

        return toDto(saved);
    }

    /**
     * Toggle feature flag (enable/disable)
     */
    @Transactional
    public FeatureFlagDto toggleFeatureFlag(String flagKey, boolean enabled, String updatedBy) {
        log.info("Toggling feature flag: {} to {}", flagKey, enabled);

        FeatureFlag flag = featureFlagRepository.findByFlagKey(flagKey)
                .orElseThrow(() -> new ResourceNotFoundException("Feature flag not found: " + flagKey));

        flag.setEnabled(enabled);
        flag.setUpdatedBy(updatedBy);

        FeatureFlag updated = featureFlagRepository.save(flag);

        // Audit log
        createAuditLog("FEATURE_FLAG_TOGGLED", "FeatureFlag", updated.getId(),
                String.format("Feature flag '%s' %s", flagKey, enabled ? "enabled" : "disabled"),
                updatedBy);

        return toDto(updated);
    }

    /**
     * Update feature flag
     */
    @Transactional
    public FeatureFlagDto updateFeatureFlag(String flagKey, FeatureFlagDto dto, String updatedBy) {
        log.info("Updating feature flag: {}", flagKey);

        FeatureFlag flag = featureFlagRepository.findByFlagKey(flagKey)
                .orElseThrow(() -> new ResourceNotFoundException("Feature flag not found: " + flagKey));

        flag.setFlagName(dto.getFlagName());
        flag.setDescription(dto.getDescription());
        flag.setEnabled(dto.getEnabled());
        flag.setRoleFilters(toJson(dto.getRoleFilters()));
        flag.setUpdatedBy(updatedBy);

        FeatureFlag updated = featureFlagRepository.save(flag);

        // Audit log
        createAuditLog("FEATURE_FLAG_UPDATED", "FeatureFlag", updated.getId(),
                "Updated feature flag: " + flagKey, updatedBy);

        return toDto(updated);
    }

    /**
     * Delete feature flag
     */
    @Transactional
    public void deleteFeatureFlag(String flagKey, String deletedBy) {
        log.info("Deleting feature flag: {}", flagKey);

        FeatureFlag flag = featureFlagRepository.findByFlagKey(flagKey)
                .orElseThrow(() -> new ResourceNotFoundException("Feature flag not found: " + flagKey));

        Long flagId = flag.getId();
        featureFlagRepository.delete(flag);

        // Audit log
        createAuditLog("FEATURE_FLAG_DELETED", "FeatureFlag", flagId,
                "Deleted feature flag: " + flagKey, deletedBy);
    }

    /**
     * Check if a feature flag is enabled (DB lookup, no audit log).
     * Used by FeatureGuard for runtime checks.
     * Returns defaultValue if the flag does not exist in DB.
     */
    @Transactional(readOnly = true)
    public boolean isFlagEnabled(String flagKey, boolean defaultValue) {
        return featureFlagRepository.findByFlagKey(flagKey)
                .map(FeatureFlag::getEnabled)
                .orElse(defaultValue);
    }

    /**
     * Check if feature is enabled for specific role
     */
    @Transactional(readOnly = true)
    public boolean isFeatureEnabledForRole(String flagKey, String role) {
        FeatureFlag flag = featureFlagRepository.findByFlagKey(flagKey).orElse(null);
        if (flag == null || !flag.getEnabled()) {
            return false;
        }

        // If no role filters, feature is enabled for all roles
        if (flag.getRoleFilters() == null || flag.getRoleFilters().isEmpty()) {
            return true;
        }

        // Check if role is in filters
        List<String> roleFilters = fromJson(flag.getRoleFilters());
        return roleFilters.contains(role);
    }

    // Helper methods
    private FeatureFlagDto toDto(FeatureFlag flag) {
        return FeatureFlagDto.builder()
                .id(flag.getId())
                .flagKey(flag.getFlagKey())
                .flagName(flag.getFlagName())
                .description(flag.getDescription())
                .enabled(flag.getEnabled())
                .roleFilters(fromJson(flag.getRoleFilters()))
                .createdBy(flag.getCreatedBy())
                .updatedBy(flag.getUpdatedBy())
                .createdAt(flag.getCreatedAt())
                .updatedAt(flag.getUpdatedAt())
                .build();
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.error("Error converting list to JSON", e);
            return null;
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON", e);
            return List.of();
        }
    }

    private void createAuditLog(String action, String entityType, Long entityId, String details, String username) {
        AuditLog auditLog = AuditLog.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .username(username)
                .build();
        auditLogRepository.save(auditLog);
    }
}
