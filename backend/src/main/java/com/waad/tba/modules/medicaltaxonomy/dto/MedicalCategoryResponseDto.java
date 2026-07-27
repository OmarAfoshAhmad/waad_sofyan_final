package com.waad.tba.modules.medicaltaxonomy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for Medical Category responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalCategoryResponseDto {

    private Long id;
    private String code;
    private String name;
    /** @deprecated Always null. Categories are canonical flat reference data. */
    @Deprecated
    private Long parentId;
    /** @deprecated Always null. Categories are canonical flat reference data. */
    @Deprecated
    private String parentName;
    private String context; // Clinical care-setting: INPATIENT, OUTPATIENT, OPERATING_ROOM, EMERGENCY,
                            // SPECIAL, ANY
    private java.util.List<String> contexts;
    private boolean active;
    private java.math.BigDecimal coveragePercent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** @deprecated Always empty. Legacy multi-parent hierarchy removed. */
    @Deprecated
    private java.util.List<Long> multiParentIds;
    /** @deprecated Always empty. Legacy multi-parent hierarchy removed. */
    @Deprecated
    private java.util.List<String> multiParentNames;

    /** @deprecated Always empty. Category tree hierarchy removed. */
    @Deprecated
    private List<MedicalCategoryResponseDto> children;

    /**
     * عدد الخدمات الطبية النشطة المرتبطة بهذا التصنيف مباشرةً.
     * يُحسب من قاعدة البيانات عند بناء شجرة التصنيفات.
     */
    @Builder.Default
    private long serviceCount = 0L;

    /** @deprecated Always 0. Category hierarchy removed. */
    @Deprecated
    @Builder.Default
    private int childrenCount = 0;
}
