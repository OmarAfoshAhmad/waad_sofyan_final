package com.waad.tba.modules.medicaltaxonomy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "DTO for bulk moving medical categories")
public class MedicalCategoryBulkMoveDto {

    @NotEmpty(message = "يجب تحديد تصنيف واحد على الأقل")
    @Schema(description = "List of category IDs to move", required = true)
    private List<Long> ids;

    @Schema(description = "The new parent category ID (null for root)", required = false)
    private Long newParentId;
}
