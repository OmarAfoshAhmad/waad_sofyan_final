package com.waad.tba.modules.semantic.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class MedicalClassificationRequestDto {
    private String serviceName;
    private String serviceCode;
    private String sourceMainCategory;
    private String sourceSubCategory;
    private BigDecimal price;
    private String providerName;
    private Long providerId;
    private Long contractId;
    private String preferredEncounterType;
}
