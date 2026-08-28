package com.waad.tba.modules.benefitpolicy.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Every row this file would post, and whether it's valid. {@code previewToken}
 * is the single-use proof {@code execute} requires -- it binds this exact
 * outcome to this exact file and reference date.
 */
@Value
@Builder
public class OpeningConsumptionImportPreviewDto {
    String previewToken;
    int totalRows;
    int validRows;
    int invalidRows;
    List<OpeningConsumptionImportRowResult> rows;
}
