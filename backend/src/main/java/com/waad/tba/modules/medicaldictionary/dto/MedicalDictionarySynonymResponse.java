package com.waad.tba.modules.medicaldictionary.dto;

import com.waad.tba.modules.medicaldictionary.enums.DictionarySynonymType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MedicalDictionarySynonymResponse {
    private Long id;
    private Long entryId;
    private String synonym;
    private String normalizedSynonym;
    private DictionarySynonymType synonymType;
    private String language;
    private boolean active;
    private Long usageCount;
}
