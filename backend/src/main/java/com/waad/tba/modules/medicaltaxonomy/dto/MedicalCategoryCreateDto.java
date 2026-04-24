package com.waad.tba.modules.medicaltaxonomy.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating a new Medical Category.
 * 
 * Field Mapping:
 * - Frontend: categoryCode → Backend: code
 * - Frontend: name → Backend: name (unified)
 * - Frontend: parentCategoryId → Backend: parentId
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalCategoryCreateDto {

    /**
     * Unique category code (immutable).
     * Optional on create: server auto-generates values like CAT001, CAT002...
     */
    @Size(max = 50, message = "Category code must not exceed 50 characters")
    @JsonAlias({ "categoryCode", "code" })
    private String code;

    /**
     * Category name (unified field)
     */
    @NotBlank(message = "Category name is required")
    @Size(max = 200, message = "Category name must not exceed 200 characters")
    private String name;

    /**
     * Parent category ID (null for root categories)
     */
    @JsonAlias({ "parentCategoryId", "parentId" })
    private Long parentId;

    /**
     * Clinical care-setting context.
     * Accepted values: INPATIENT, OUTPATIENT, OPERATING_ROOM, EMERGENCY, SPECIAL,
     * ANY
     * Defaults to ANY if not provided.
     */
    private String context;

    /**
     * Active status (defaults to true)
     */
    @Builder.Default
    private Boolean active = true;

    @jakarta.validation.constraints.DecimalMax(value = "100.0", message = "Coverage percent must be <= 100")
    private java.math.BigDecimal coveragePercent;

    /**
     * Multiple parent root IDs for cross-context support (Phase 10)
     */
    @com.fasterxml.jackson.annotation.JsonAlias({ "multiParentIds", "rootIds" })
    private java.util.List<Long> multiParentIds;
}
