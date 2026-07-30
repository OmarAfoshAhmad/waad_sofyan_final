package com.waad.tba.modules.medicaldictionary.dto;

import com.waad.tba.modules.medicaldictionary.enums.DictionarySuggestionSource;
import com.waad.tba.modules.medicaldictionary.enums.DictionarySuggestionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MedicalDictionarySuggestionResponse {
    private Long id;
    private String originalText;
    private String normalizedOriginalText;
    private Long suggestedEntryId;
    private String suggestedEntryName;
    private Long suggestedCategoryId;
    private String suggestedCategoryCode;
    private String suggestedCategoryName;
    private DictionarySuggestionSource source;
    private DictionarySuggestionStatus status;
    private Integer confidence;
    private String sourceReference;
    private String reviewNote;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
