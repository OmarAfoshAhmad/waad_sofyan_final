package com.waad.tba.modules.medicaldictionary.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class PriceListSessionDiffResponse {
    Long sessionId;
    Long contractId;
    String contractCode;
    int total;
    int createCount;
    int updateCount;
    int identicalCount;
    int rejectedCount;
    boolean hasChanges;
    List<ItemDiff> items;

    @Value
    @Builder
    public static class ItemDiff {
        Long itemId;
        Integer rowNumber;
        String serviceCode;
        String serviceName;
        String action;
        String message;
        Long pricingItemId;
        BigDecimal currentMinPrice;
        BigDecimal currentMaxPrice;
        BigDecimal newMinPrice;
        BigDecimal newMaxPrice;
        String currentCategoryCode;
        String newCategoryCode;
    }
}
