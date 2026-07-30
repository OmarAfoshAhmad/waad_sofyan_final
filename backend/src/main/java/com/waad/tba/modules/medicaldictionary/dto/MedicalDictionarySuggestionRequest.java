package com.waad.tba.modules.medicaldictionary.dto;

import com.waad.tba.modules.medicaldictionary.enums.DictionarySuggestionSource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MedicalDictionarySuggestionRequest {
    @NotBlank
    private String originalText;

    private Long suggestedEntryId;

    private Long suggestedCategoryId;

    @NotNull
    private DictionarySuggestionSource source;

    @Min(0)
    @Max(100)
    private Integer confidence;

    private String sourceReference;
}
