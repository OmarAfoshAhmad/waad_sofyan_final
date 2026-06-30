package com.waad.tba.modules.providercontract.dto;

import com.waad.tba.modules.providercontract.enums.ConfidenceLevel;
import com.waad.tba.modules.providercontract.enums.EncounterType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PricingImportPreviewItemDto {
    private String rowId; // unique identifier for the row within the session
    private String serviceName;
    private String serviceCode;
    private BigDecimal contractPrice;
    private String currency;
    
    // Imported original
    private String importedMainCategory;
    private String importedSubCategory;
    
    // Proposed classification
    private Long proposedCategoryId;
    private String proposedCategoryName;
    private String proposedCategoryCode;
    
    private EncounterType encounterType;
    private ConfidenceLevel confidenceLevel;
    private boolean requiresReview;
    private String reviewReason;
    private String classificationSource;
    
    // Flags
    private boolean isPriceZero;
}
