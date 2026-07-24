package com.waad.tba.common.dto;

import java.time.LocalDateTime;

import com.waad.tba.common.entity.SystemSetting;

import lombok.Builder;

@Builder
public record SystemSettingDto(
        Long id,
        String settingKey,
        String settingValue,
        SystemSetting.SettingValueType valueType,
        String description,
        String category,
        Boolean isEditable,
        String defaultValue,
        String validationRules,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String updatedBy) {

    public static SystemSettingDto from(SystemSetting setting) {
        return SystemSettingDto.builder()
                .id(setting.getId())
                .settingKey(setting.getSettingKey())
                .settingValue(setting.getSettingValue())
                .valueType(setting.getValueType())
                .description(setting.getDescription())
                .category(setting.getCategory())
                .isEditable(setting.getIsEditable())
                .defaultValue(setting.getDefaultValue())
                .validationRules(setting.getValidationRules())
                .active(setting.getActive())
                .createdAt(setting.getCreatedAt())
                .updatedAt(setting.getUpdatedAt())
                .updatedBy(setting.getUpdatedBy())
                .build();
    }
}
