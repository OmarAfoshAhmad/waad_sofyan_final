package com.waad.tba.modules.providercontract.dto;

import com.waad.tba.modules.providercontract.entity.ProviderContract.PricingModel;
import com.waad.tba.modules.providercontract.entity.ProviderContract.PricingScope;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for updating an existing Provider Contract.
 * 
 * Note: Status changes should be done via dedicated endpoints (activate, suspend, terminate)
 * 
 * @version 1.0
 * @since 2024-12-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderContractUpdateDto {

    /**
     * Pricing scope can be changed while the contract is still in draft.
     */
    private PricingScope pricingScope;

    /**
     * Employer ID is required when pricingScope = EMPLOYER_SPECIFIC.
     */
    @Positive(message = "Employer ID must be positive")
    private Long employerId;

    /**
     * Pricing model type
     */
    private PricingModel pricingModel;

    /**
     * Default discount percentage
     */
    @DecimalMin(value = "0.00", message = "Discount must be >= 0")
    @DecimalMax(value = "100.00", message = "Discount must be <= 100")
    private BigDecimal discountPercent;

    private Boolean discountBeforeRejection;

    /** First service date affected by a financial-terms amendment. Defaults to today. */
    private LocalDate termsEffectiveFrom;

    @Size(max = 1000)
    private String termsChangeReason;

    /**
     * Contract start date
     */
    private LocalDate startDate;

    /**
     * Contract end date
     */
    private LocalDate endDate;

    /**
     * Date contract was signed
     */
    private LocalDate signedDate;

    /**
     * Total estimated contract value
     */
    @DecimalMin(value = "0.00", message = "Total value must be >= 0")
    private BigDecimal totalValue;

    /**
     * Currency code
     */
    @Size(max = 3)
    private String currency;

    /**
     * Payment terms
     */
    @Size(max = 100)
    private String paymentTerms;

    /**
     * Auto renewal flag
     */
    private Boolean autoRenew;

    /**
     * Contact person name
     */
    @Size(max = 100)
    private String contactPerson;

    /**
     * Contact phone
     */
    @Size(max = 50)
    private String contactPhone;

    /**
     * Contact email
     */
    @Email(message = "Invalid email format")
    @Size(max = 100)
    private String contactEmail;

    /**
     * Notes
     */
    @Size(max = 2000)
    private String notes;
}
