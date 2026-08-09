package com.waad.tba.modules.medicaldictionary.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PriceListSessionSaveRequest {

    private Long sessionId;

    @NotBlank(message = "اسم الجلسة مطلوب")
    @Size(max = 300)
    private String sessionName;

    @Size(max = 500)
    private String originalFileName;

    private Long providerId;
    private String providerName;
    private Long contractId;
    private String contractCode;

    @Size(max = 2000)
    private String notes;

    @NotEmpty(message = "سطور قائمة الأسعار مطلوبة")
    @Size(max = 10000, message = "الحد الأقصى لحفظ الجلسة 10000 خدمة")
    @Valid
    private List<Item> items;

    @Data
    public static class Item {
        private Integer rowNumber;
        private String sourceSheet;
        private String serviceCode;

        @NotBlank(message = "اسم خدمة المرفق مطلوب")
        @Size(max = 500)
        private String serviceName;

        private String canonicalName;
        private Long dictionaryEntryId;
        private Long medicalCategoryId;
        private String medicalCategoryCode;
        private String medicalCategoryName;
        private Integer confidence;
        private Long dictionaryReleaseId;
        private String dictionaryVersion;
        private String dictionaryConceptCode;
        private String classificationMethod;
        private String classificationReason;
        private String classificationExceptionType;
        private Long classificationEvidenceId;
        private Boolean classificationExcludePrecision;
        private String status;
        private BigDecimal price;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private String priceLabel;
        private Boolean duplicateName;
        private Boolean mergedDuplicate;
        private Integer mergedSourceCount;
        private String mergeNotes;
        private String manualReviewNote;
    }
}
