package com.waad.tba.modules.medicaldictionary.dto;

import com.waad.tba.modules.medicaldictionary.enums.DictionarySynonymType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MedicalDictionarySynonymSearchResponse {
    private Long synonymId;
    private String synonym;
    private String normalizedSynonym;
    private DictionarySynonymType synonymType;
    private String language;
    private boolean active;
    private Long usageCount;
    private Long entryId;
    private String canonicalName;
    private Long medicalCategoryId;
    private String medicalCategoryCode;
    private String medicalCategoryName;
}
