package com.waad.tba.modules.medicaldictionary.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MedicalDictionaryMatchResponse {
    private Long entryId;
    private String canonicalName;
    private Long medicalCategoryId;
    private String medicalCategoryCode;
    private String medicalCategoryName;
    private String matchedText;
    private String matchType;
    private Integer confidence;
}
