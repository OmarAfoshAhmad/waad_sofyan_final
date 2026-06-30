package com.waad.tba.modules.providercontract.dto;

import lombok.Data;

import java.util.List;

@Data
public class PricingImportConfirmRequest {
    private String importSessionId;
    
    // A list of modifications made by the user before confirming
    private List<PricingImportModificationDto> modifications;
    
    // Optional flag to skip zero price items
    private boolean skipZeroPriceItems;
}
