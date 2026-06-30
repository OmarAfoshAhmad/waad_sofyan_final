package com.waad.tba.modules.providercontract.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PricingImportPreviewDto {
    private String importSessionId;
    
    private int totalItems;
    private int highConfidenceCount;
    private int mediumConfidenceCount;
    private int lowConfidenceCount;
    private int manualReviewCount;
    private int zeroPriceCount;
    
    private List<PricingImportPreviewItemDto> items;
}
