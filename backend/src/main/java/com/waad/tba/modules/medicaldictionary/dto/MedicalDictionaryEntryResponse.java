package com.waad.tba.modules.medicaldictionary.dto;

import com.waad.tba.modules.medicaldictionary.enums.DictionaryEntryStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class MedicalDictionaryEntryResponse {
    private Long id;
    private String canonicalName;
    private String normalizedCanonicalName;
    private Long medicalCategoryId;
    private String medicalCategoryCode;
    private String medicalCategoryName;
    private DictionaryEntryStatus status;
    private Integer defaultConfidence;
    private String notes;
    private long synonymCount;
    private List<MedicalDictionarySynonymResponse> synonyms;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
