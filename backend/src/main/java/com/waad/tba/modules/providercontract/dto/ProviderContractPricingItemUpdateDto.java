package com.waad.tba.modules.providercontract.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for updating an existing Provider Contract Pricing Item.
 * 
 * @version 1.0
 * @since 2024-12-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderContractPricingItemUpdateDto {

    /**
     * Optional medical service link/update from unified catalog.
     */
    private Long medicalServiceId;

    /**
     * Optional category override
     */
    private Long medicalCategoryId;

    /**
     * Standard/list price
     */
    @DecimalMin(value = "0.00", message = "Base price must be >= 0")
    private BigDecimal basePrice;

    /**
     * Negotiated contract price
     */
    @DecimalMin(value = "0.00", message = "Contract price must be >= 0")
    private BigDecimal contractPrice;

    /**
     * Optional upper bound for ranged pricing.
     */
    @DecimalMin(value = "0.00", message = "Max contract price must be >= 0")
    private BigDecimal maxContractPrice;

    /**
     * Unit of service
     */
    @Size(max = 50)
    private String unit;

    /**
     * Currency code
     */
    @Size(max = 3)
    private String currency;

    /**
     * Date this pricing becomes effective
     */
    private LocalDate effectiveFrom;

    /**
     * Date this pricing expires
     */
    private LocalDate effectiveTo;

    /**
     * Notes
     */
    @Size(max = 2000)
    private String notes;
}
