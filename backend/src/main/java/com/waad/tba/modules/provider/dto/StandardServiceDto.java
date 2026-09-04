package com.waad.tba.modules.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StandardServiceDto {
    private Long id;
    private String code;
    private String name;
    private String nameAr;
    private String nameEn;
    private Long categoryId;
    private String categoryCode;
    private String categoryName;
    private boolean active;
    @Builder.Default
    private List<com.waad.tba.modules.provider.entity.Provider.ProviderType> defaultProviderTypes = List.of();
}
