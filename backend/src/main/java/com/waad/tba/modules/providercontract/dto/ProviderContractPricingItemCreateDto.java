package com.waad.tba.modules.providercontract.dto;

import com.waad.tba.common.validation.ValidDateRange;
import com.waad.tba.common.validation.ValidPricingRange;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for creating a new Provider Contract Pricing Item.
 * 
 * @version 1.0
 * @since 2024-12-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidPricingRange
@ValidDateRange(startField = "effectiveFrom", endField = "effectiveTo")
public class ProviderContractPricingItemCreateDto {

    /**
     * Medical service ID (required)
     */
    @NotNull(message = "Medical service ID is required")
    private Long medicalServiceId;

    /**
     * Optional category override
     */
    private Long medicalCategoryId;

    /**
     * Standard/list price (required)
     */
    @NotNull(message = "Base price is required")
    @DecimalMin(value = "0.00", message = "Base price must be >= 0")
    private BigDecimal basePrice;

    /**
     * Negotiated contract price (required)
     */
    @NotNull(message = "Contract price is required")
    @DecimalMin(value = "0.00", message = "Contract price must be >= 0")
    private BigDecimal contractPrice;

    /**
     * Unit of service
     */
    @Size(max = 50)
    @Builder.Default
    private String unit = "service";

    /**
     * Currency code
     */
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code (e.g. LYD, USD)")
    @Size(max = 3)
    @Builder.Default
    private String currency = "LYD";

    /**
     * Date this pricing becomes effective (optional)
     */
    private LocalDate effectiveFrom;

    /**
     * Date this pricing expires (optional)
     */
    private LocalDate effectiveTo;

    /**
     * Notes
     */
    @Size(max = 2000)
    private String notes;
}
