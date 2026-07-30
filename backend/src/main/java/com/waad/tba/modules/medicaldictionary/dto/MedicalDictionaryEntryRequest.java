package com.waad.tba.modules.medicaldictionary.dto;

import com.waad.tba.modules.medicaldictionary.enums.DictionaryEntryStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MedicalDictionaryEntryRequest {
    @NotBlank
    private String canonicalName;

    @NotNull
    private Long medicalCategoryId;

    private DictionaryEntryStatus status = DictionaryEntryStatus.DRAFT;

    @Min(0)
    @Max(100)
    private Integer defaultConfidence = 80;

    private String notes;
}
