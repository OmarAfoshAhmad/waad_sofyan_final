package com.waad.tba.modules.medicaldictionary.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PriceListSessionPostResponse {
    Long sessionId;
    Long contractId;
    String contractCode;
    int created;
    int updated;
    int superseded;
    int skipped;
    int rejected;
    PriceListSessionResponse session;
    List<ItemResult> results;

    @Value
    @Builder
    public static class ItemResult {
        Long itemId;
        Integer rowNumber;
        String serviceCode;
        String serviceName;
        String result;
        String message;
        Long pricingItemId;
    }
}
