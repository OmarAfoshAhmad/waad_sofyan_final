package com.waad.tba.modules.medicaldictionary.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MedicalDictionarySuggestionReviewRequest {
    private Long targetEntryId;
    private Long targetCategoryId;
    private String canonicalName;
    private boolean approveAsSynonym = true;

    @NotBlank
    private String reviewNote;
}
