package com.waad.tba.modules.member.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Excel Column Mapping Suggestion DTO
 * 
 * Represents a suggested mapping between Excel column and Member field
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelMappingSuggestionDto {

    /**
     * Excel column index (0-based)
     */
    private Integer columnIndex;

    /**
     * Excel column name/header
     */
    private String columnName;

    /**
     * Suggested Member field name
     * Examples: "nationalNumber", "fullName", "email", "phone", etc.
     */
    private String suggestedField;

    /**
     * Arabic label for the suggested field
     */
    private String suggestedFieldLabelAr;

    /**
     * English label for the suggested field
     */
    private String suggestedFieldLabelEn;

    /**
     * Confidence score (0.0 - 1.0)
     * 1.0 = exact match
     * 0.8+ = high confidence
     * 0.5-0.8 = medium confidence
     * <0.5 = low confidence
     */
    private Double confidence;

    /**
     * Matching reason/explanation
     * Examples: "Exact match", "Similar header name", "Data pattern match"
     */
    private String matchReason;

    /**
     * Whether this suggestion is automatically accepted
     * (e.g., confidence >= 0.9)
     */
    private Boolean autoAccepted;

    /**
     * Sample value from this column (for user verification)
     */
    private String sampleValue;
}
