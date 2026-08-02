package com.waad.tba.modules.medicaldictionary.dto;

import com.waad.tba.modules.medicaldictionary.enums.PriceListSessionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PriceListSessionSummaryResponse {
    private Long id;
    private String sessionName;
    private String originalFileName;
    private Long providerId;
    private String providerName;
    private Long contractId;
    private String contractCode;
    private PriceListSessionStatus status;
    private Integer totalRows;
    private Integer highConfidence;
    private Integer needsReview;
    private Integer unknown;
    private Integer duplicates;
    private Integer rangedPrices;
    private Integer posted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
