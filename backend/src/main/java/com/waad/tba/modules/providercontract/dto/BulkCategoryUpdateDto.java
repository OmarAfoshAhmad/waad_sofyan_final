package com.waad.tba.modules.providercontract.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class BulkCategoryUpdateDto {
    @NotEmpty(message = "يجب تحديد عنصر واحد على الأقل")
    private List<Long> pricingItemIds;

    @NotNull(message = "معرف التصنيف مطلوب")
    private Long categoryId;
}
