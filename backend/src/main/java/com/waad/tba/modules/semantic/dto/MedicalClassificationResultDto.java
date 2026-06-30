package com.waad.tba.modules.semantic.dto;

import lombok.Data;
import java.util.List;

@Data
public class MedicalClassificationResultDto {
    private String originalServiceName;
    private String normalizedServiceName;
    private String detectedLanguage;

    private String medicalMeaningAr;
    private String medicalMeaningEn;

    private String medicalSpecialty;
    private String bodySystem;
    private String procedureType;
    private String procedureComplexity;

    private String likelyEncounterType;
    private String suggestedInsuranceCategoryCode;
    private String suggestedInsuranceCategoryName;

    private Double confidenceScore;
    private String confidenceLevel;

    private Boolean requiresReview;
    private List<String> reviewReasons;

    private String classificationSource;
    private List<String> matchedKeywords;
    private List<String> warnings;

    private String explanationAr;
}
