package com.waad.tba.modules.providercontract.dto;

import com.waad.tba.modules.medicaltaxonomy.entity.MedicalCategory;
import com.waad.tba.modules.providercontract.enums.ConfidenceLevel;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ClassificationResult {
    private final MedicalCategory category;
    private final EncounterType encounterType;
    private final ConfidenceLevel confidenceLevel;
    private final boolean requiresReview;
    private final String reviewReason;
    private final String classificationSource;

    public static ClassificationResult unclassified() {
        return ClassificationResult.builder()
                .category(null)
                .encounterType(EncounterType.ANY)
                .confidenceLevel(ConfidenceLevel.LOW)
                .requiresReview(true)
                .reviewReason("No matching classification rule found")
                .classificationSource("NONE")
                .build();
    }

    public static ClassificationResult inpatientGeneral(MedicalCategory category, String source) {
        return ClassificationResult.builder()
                .category(category)
                .encounterType(EncounterType.INPATIENT)
                .confidenceLevel(ConfidenceLevel.LOW)
                .requiresReview(true)
                .reviewReason("Fallback to general inpatient")
                .classificationSource(source)
                .build();
    }
}
