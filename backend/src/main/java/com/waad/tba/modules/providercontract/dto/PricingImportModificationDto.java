package com.waad.tba.modules.providercontract.dto;

import com.waad.tba.modules.providercontract.enums.EncounterType;
import lombok.Data;

@Data
public class PricingImportModificationDto {
    private String rowId;
    private Long manualCategoryId;
    private EncounterType encounterType;
    private boolean saveAsRule;
}
