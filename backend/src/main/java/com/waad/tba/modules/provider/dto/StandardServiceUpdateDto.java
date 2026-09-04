package com.waad.tba.modules.provider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Updates an existing standard service's display name, category, active
 * flag, and default facility types. code is immutable (see
 * MedicalService.code javadoc) and deliberately absent here.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StandardServiceUpdateDto {

    @NotBlank(message = "الاسم بالعربية إلزامي")
    private String nameAr;

    private String nameEn;

    @NotNull(message = "التصنيف الطبي إلزامي")
    private Long categoryId;

    @NotNull(message = "حالة التفعيل إلزامية")
    private Boolean active;

    @Builder.Default
    private List<com.waad.tba.modules.provider.entity.Provider.ProviderType> defaultProviderTypes = List.of();
}
