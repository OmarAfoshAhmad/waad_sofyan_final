package com.waad.tba.modules.medicaltaxonomy.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating a Medical Category.
 * 
 * Note: 'code' is immutable and cannot be changed.
 * All fields are optional (partial update).
 * 
 * PHASE 8: Unified name field only (Arabic system).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalCategoryUpdateDto {

    /**
     * Category name (unified)
     */
    @Size(max = 200, message = "Category name must not exceed 200 characters")
    @JsonAlias({ "nameAr", "name" })
    private String name;

    /**
     * Parent category ID (null to make root category)
     */
    @JsonAlias({ "parentCategoryId", "parentId" })
    private Long parentId;

    /**
     * Clinical care-setting context (optional).
     * Accepted values: INPATIENT, OUTPATIENT, OPERATING_ROOM, EMERGENCY, SPECIAL,
     * ANY
     */
    private String context;

    /**
     * Active status
     */
    private Boolean active;

    @jakarta.validation.constraints.DecimalMax(value = "100.0", message = "Coverage percent must be <= 100")
    private java.math.BigDecimal coveragePercent;

    /**
     * Multiple parent root IDs for cross-context support (Phase 10)
     */
    @com.fasterxml.jackson.annotation.JsonAlias({ "multiParentIds", "rootIds" })
    private java.util.List<Long> multiParentIds;
}
