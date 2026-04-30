package com.waad.tba.modules.providercontract.dto;

import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.entity.ProviderContract.PricingModel;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for creating a new Provider Contract.
 * 
 * @version 1.0
 * @since 2024-12-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderContractCreateDto {

    /**
     * Provider ID (required)
     */
    @NotNull(message = "Provider ID is required")
    @Positive(message = "Provider ID must be positive")
    private Long providerId;

    /**
     * Custom contract code (optional - auto-generated if not provided)
     */
    @Size(max = 50, message = "Contract code must not exceed 50 characters")
    private String contractCode;

    /**
     * Initial status (defaults to DRAFT)
     */
    private ContractStatus status;

    /**
     * Pricing model type (defaults to DISCOUNT)
     */
    private PricingModel pricingModel;

    /**
     * Default discount percentage
     */
    @DecimalMin(value = "0.00", message = "Discount must be >= 0")
    @DecimalMax(value = "100.00", message = "Discount must be <= 100")
    private BigDecimal discountPercent;

    /**
     * هل يُطبَّق خصم العقد قبل خصم المرفوض؟ (الافتراضي: false = بعد)
     */
    @Builder.Default
    private Boolean discountBeforeRejection = false;

    /**
     * Contract start date (required)
     */
    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    /**
     * Contract end date (optional for open-ended contracts)
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
    @Builder.Default
    private String currency = "LYD";

    /**
     * Payment terms
     */
    @Size(max = 100)
    private String paymentTerms;

    /**
     * Auto renewal flag
     */
    @Builder.Default
    private Boolean autoRenew = false;

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
