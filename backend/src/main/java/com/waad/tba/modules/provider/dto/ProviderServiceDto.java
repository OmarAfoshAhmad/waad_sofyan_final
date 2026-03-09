package com.waad.tba.modules.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Simplified DTO for provider portal service dropdown and selection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderServiceDto {
    private Long serviceId;
    private String serviceCode;
    private String serviceName;
    private String serviceNameArabic;
    private String categoryCode;
    private String categoryName;
    private BigDecimal contractPrice;
    private String currency;
    private Boolean requiresPA;
}
