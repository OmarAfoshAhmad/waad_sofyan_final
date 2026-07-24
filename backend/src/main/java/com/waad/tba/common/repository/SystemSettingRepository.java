package com.waad.tba.common.repository;

import com.waad.tba.common.entity.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
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

}
