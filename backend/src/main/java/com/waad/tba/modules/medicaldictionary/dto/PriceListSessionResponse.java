package com.waad.tba.modules.medicaldictionary.dto;

import com.waad.tba.modules.medicaldictionary.enums.PriceListItemStatus;
import com.waad.tba.modules.medicaldictionary.enums.PriceListSessionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PriceListSessionResponse {
    private Long id;
    private String sessionName;
    private String originalFileName;
    private Long providerId;
    private String providerName;
    private Long contractId;
    private String contractCode;
    private PriceListSessionStatus status;
    private Summary summary;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Item> items;

    @Data
    @Builder
    public static class Summary {
        private Integer totalRows;
        private Integer highConfidence;
        private Integer needsReview;
        private Integer unknown;
        private Integer duplicates;
        private Integer rangedPrices;
        private Integer posted;
    }

    @Data
    @Builder
    public static class Item {
        private Long id;
        private Integer rowNumber;
        private String sourceSheet;
        private String serviceCode;
        private String serviceName;
        private String canonicalName;
        private Long dictionaryEntryId;
        private Long medicalCategoryId;
        private String medicalCategoryCode;
        private String medicalCategoryName;
        private Integer confidence;
        private PriceListItemStatus status;
        private BigDecimal price;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private String priceLabel;
        private Boolean duplicateName;
        private Boolean mergedDuplicate;
        private Integer mergedSourceCount;
        private String mergeNotes;
        private String manualReviewNote;
        private Long postedPricingItemId;
        private LocalDateTime postedAt;
    }
}
