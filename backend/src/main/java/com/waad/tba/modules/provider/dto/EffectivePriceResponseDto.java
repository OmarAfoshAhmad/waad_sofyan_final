package com.waad.tba.modules.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for Effective Price Response
 * 
 * Returns the contract price for a service on a specific date
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EffectivePriceResponseDto {

    /**
     * Provider ID
     */
    private Long providerId;

    /**
     * Provider Name
     */
    private String providerName;

    /**
     * Employer/work entity used for resolving employer-specific pricing.
     */
    private Long employerId;

    /**
     * GLOBAL or EMPLOYER_SPECIFIC.
     */
    private String pricingScope;

    /**
     * true when no employer-specific price was found and global provider pricing
     * was used instead.
     */
    private Boolean usedGlobalFallback;

    /**
     * Service Code
     */
    private String serviceCode;

    /**
     * Service Name (unified field)
     */
    private String serviceName;

    /**
     * Contract Price
     * NULL if no contract found
     */
    private BigDecimal contractPrice;

    /**
     * Optional upper bound for ranged pricing.
     */
    private BigDecimal maxContractPrice;

    /**
     * Currency
     */
    private String currency;

    /**
     * Date for which price is queried
     */
    private LocalDate effectiveDate;

    /**
     * Contract ID
     * NULL if no contract found
     */
    private Long contractId;

    /**
     * Contract Effective From
     */
    private LocalDate effectiveFrom;

    /**
     * Contract Effective To
     */
    private LocalDate effectiveTo;

    /**
     * Pricing Item ID (from provider_contract_pricing_items)
     */
    private Long pricingItemId;

    /**
     * Has Contract
     * true if contract was found
     */
    private boolean hasContract;

    /**
     * Message
     * Info message (e.g., "No contract found", "Contract found")
     */
    private String message;
}
