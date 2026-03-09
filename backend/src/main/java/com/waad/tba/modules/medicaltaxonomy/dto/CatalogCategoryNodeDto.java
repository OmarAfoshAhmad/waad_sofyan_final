package com.waad.tba.modules.medicaltaxonomy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Hierarchical catalog tree response:
 *
 * <pre>
 * CatalogCategoryNodeDto
 *   → specialties[]
 *      → services[]
 * </pre>
 *
 * Returned by {@code GET /api/v1/medical-catalog}
 */
@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CatalogCategoryNodeDto {

    Long   id;
    String code;
    String nameAr;
    String nameEn;
    int    specialtyCount;
    int    serviceCount;

    List<CatalogCategoryNodeDto> children;
    List<CatalogSpecialtyNodeDto> specialties;

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CatalogSpecialtyNodeDto {
        Long   id;
        String code;
        String nameAr;
        String nameEn;
        int    serviceCount;

        List<CatalogServiceNodeDto> services;
    }

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CatalogServiceNodeDto {
        Long    id;
        String  code;
        String  nameAr;
        String  nameEn;
        String  status;
        Boolean isMaster;
    }
}
