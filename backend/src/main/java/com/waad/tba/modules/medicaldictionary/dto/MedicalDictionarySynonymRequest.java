package com.waad.tba.modules.medicaldictionary.dto;

import com.waad.tba.modules.medicaldictionary.enums.DictionarySynonymType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MedicalDictionarySynonymRequest {
    @NotBlank
    private String synonym;

    private DictionarySynonymType synonymType = DictionarySynonymType.COMMON;

    private String language = "ar";
}
