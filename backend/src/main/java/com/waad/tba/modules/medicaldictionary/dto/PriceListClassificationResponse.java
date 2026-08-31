package com.waad.tba.modules.medicaldictionary.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PriceListClassificationResponse {
    private Summary summary;
    private List<Item> items;

    @Data
    @Builder
    public static class Summary {
        private int total;
        private int highConfidence;
        private int needsReview;
        private int unknown;
        private int duplicateNames;
    }

    @Data
    @Builder
    public static class Item {
        private Integer rowNumber;
        private String sourceSheet;
        private String sourceClassification;
        private String serviceCode;
        private String serviceName;
        private BigDecimal price;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private String priceLabel;
        private String status;
        private String statusLabel;
        private MedicalDictionaryMatchResponse bestMatch;
        private List<MedicalDictionaryMatchResponse> matches;
        private boolean duplicateName;
        private Long dictionaryReleaseId;
        private String dictionaryVersion;
        private String conceptCode;
        private String categoryCode;
        private String categoryName;
        private String canonicalName;
        private String matchMethod;
        private BigDecimal confidenceValue;
        private String reason;
        private String exceptionType;
        private boolean excludeFromPrecision;
        private Long evidenceId;
        private boolean postable;
    }
}
