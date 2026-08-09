package com.waad.tba.modules.medicaldictionary.dto;

import java.util.List;

public record V50ClassificationInput(
        String rawName,
        String secondaryName,
        List<String> alternateNames,
        String serviceCode,
        String sectionName,
        List<String> sectionNames,
        String notes,
        String facilityName) {

    public V50ClassificationInput {
        alternateNames = alternateNames == null ? List.of() : List.copyOf(alternateNames);
        sectionNames = sectionNames == null ? List.of() : List.copyOf(sectionNames);
    }
}
