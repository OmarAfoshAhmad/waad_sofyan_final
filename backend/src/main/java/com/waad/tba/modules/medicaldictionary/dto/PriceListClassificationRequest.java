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

    private Long providerId;
    private String providerName;

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
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private String priceLabel;
        private String sourceSheet;
        private String sourceClassification;
        private String secondaryName;
        private List<String> alternateNames;
        private String sectionName;
        private List<String> sectionNames;
        private String notes;
        private String facilityName;
    }
}
