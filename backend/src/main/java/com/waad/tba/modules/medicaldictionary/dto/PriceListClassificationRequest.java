package com.waad.tba.modules.medicaldictionary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PriceListClassificationRequest {

    @NotEmpty(message = "قائمة الخدمات مطلوبة")
    @Size(max = 1000, message = "الحد الأقصى للتصنيف في الطلب الواحد 1000 خدمة")
    @Valid
    private List<Row> rows;

    @Data
    public static class Row {
        private Integer rowNumber;

        @NotBlank(message = "اسم الخدمة مطلوب")
        @Size(max = 500, message = "اسم الخدمة طويل جداً")
        private String serviceName;

        private String serviceCode;
        private BigDecimal price;
        private String sourceSheet;
    }
}
