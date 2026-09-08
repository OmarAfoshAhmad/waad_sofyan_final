package com.waad.tba.modules.providercontract.dto;

import com.waad.tba.common.validation.ValidDateRange;
import com.waad.tba.common.validation.ValidPricingRange;
import com.waad.tba.modules.medicaltaxonomy.enums.PricingMode;
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
     * Optional provider ID for admin users adding pricing context
     */
    private Long providerId;

    /**
     * Medical service ID (DEPRECATED - use serviceCode + medicalCategoryId instead)
     */
    @Deprecated
    private Long medicalServiceId;

    /**
     * Service name (required when medicalServiceId is not set)
     */
    @Size(max = 255)
    private String serviceName;

    /**
     * Service code (optional - for reference and lookup)
     */
    @Size(max = 50)
    private String serviceCode;

    /**
     * Optional category override
     */
    private Long medicalCategoryId;

    /**
     * CONTRACT_PRICE stores an enforceable contract price cap.
     * CLAIM_UNIT_PRICE belongs to the provider contract but the clerk enters
     * the unit price per claim, so base/contract prices may be zero.
     */
    @Builder.Default
    private PricingMode pricingMode = PricingMode.CONTRACT_PRICE;

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
     * Optional upper bound for ranged pricing. contractPrice remains the
     * minimum/default price.
     */
    @DecimalMin(value = "0.00", message = "Max contract price must be >= 0")
    private BigDecimal maxContractPrice;

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
