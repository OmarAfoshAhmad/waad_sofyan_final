package com.waad.tba.modules.providercontract.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategorySummaryDto {
    private Long categoryId;
    private String categoryName;
    private Long count;
}
