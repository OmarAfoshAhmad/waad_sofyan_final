package com.waad.tba.modules.benefitpolicy.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** The batch a file's opening balances landed under, and every row it posted. */
@Value
@Builder
public class OpeningConsumptionImportExecuteResultDto {
    Long batchId;
    String batchReference;
    int postedRows;
    List<OpeningConsumptionImportRowResult> rows;
}
