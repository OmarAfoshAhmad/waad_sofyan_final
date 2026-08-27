package com.waad.tba.modules.benefitpolicy.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

/** One row of an opening-consumption import file, before or after execution. */
@Value
@Builder
public class OpeningConsumptionImportRowResult {
    int rowNumber;
    Long memberId;
    String memberName;
    BigDecimal amount;
    Integer times;
    String sourceReference;
    boolean valid;
    List<String> errors;
}
