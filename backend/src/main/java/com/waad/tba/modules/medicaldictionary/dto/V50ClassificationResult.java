package com.waad.tba.modules.medicaldictionary.dto;

import com.waad.tba.modules.medicaldictionary.enums.V50ClassificationStatus;

import java.math.BigDecimal;

public record V50ClassificationResult(
        Long releaseId,
        String dictionaryVersion,
        String conceptCode,
        String categoryCode,
        String categoryName,
        String unifiedAr,
        String unifiedEn,
        String abbreviation,
        String specialty,
        String procedureType,
        String parentContext,
        String matchMethod,
        BigDecimal confidence,
        V50ClassificationStatus status,
        String reason,
        String exceptionType,
        boolean excludeFromPrecision,
        Long evidenceId) {

    public boolean mayPostToContract() {
        return status.mayPostToContract();
    }
}
