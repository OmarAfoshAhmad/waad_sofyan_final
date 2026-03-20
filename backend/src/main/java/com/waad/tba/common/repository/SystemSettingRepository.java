package com.waad.tba.common.repository;

import com.waad.tba.common.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for SystemSetting entity.
 * 
 * @since Phase 1 - SLA Implementation
 */
@Repository
public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {
    
    /**
     * Find a setting by its unique key.
     */
    Optional<SystemSetting> findBySettingKey(String settingKey);
    
    /**
     * Find all settings in a category.
     */
    List<SystemSetting> findByCategory(String category);
    
    /**
     * Find all active settings.
     */
    List<SystemSetting> findByActiveTrue();
    
    /**
     * Find all editable settings.
     */
    @Query("SELECT s FROM SystemSetting s WHERE s.isEditable = true AND s.active = true")
    List<SystemSetting> findEditableSettings();

    /**
     * Upsert a setting by key — inserts if not exists, updates value if exists.
     */
    @Modifying
    @Query(value = """
            INSERT INTO system_settings
                (setting_key, setting_value, value_type, description, category,
                 is_editable, default_value, active, updated_by, created_at, updated_at)
            VALUES
                (:key, :value, 'STRING', :key, 'UI',
                 true, :value, true, :updatedBy, NOW(), NOW())
            ON CONFLICT (setting_key)
            DO UPDATE SET
                setting_value = EXCLUDED.setting_value,
                updated_by    = EXCLUDED.updated_by,
                updated_at    = NOW()
            """, nativeQuery = true)
    void upsertSetting(String key, String value, String updatedBy);
}
