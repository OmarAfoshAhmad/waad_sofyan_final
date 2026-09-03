package com.waad.tba.modules.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StandardServiceDto {
    private Long id;
    private String code;
    private String name;
    private String categoryCode;
    private String categoryName;
}
