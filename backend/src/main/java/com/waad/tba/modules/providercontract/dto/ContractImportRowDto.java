package com.waad.tba.modules.providercontract.dto;

import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.entity.ProviderContract.PricingModel;
import com.waad.tba.modules.providercontract.entity.ProviderContract.PricingScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One parsed row of a provider-contract bulk import file, carrying both the
 * resolved data (ready to persist) and the validation errors found for it.
 * Cached between the preview and confirm steps via {@link PricingImportSessionCache}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractImportRowDto implements Serializable {

    private int rowNumber;

    // Raw values as read from the sheet (kept for display in the preview/error report)
    private String providerNameRaw;
    private String employerNameRaw;
    private String contractCode;
    private String statusRaw;
    private String pricingScopeRaw;
    private String pricingModelRaw;
    private String discountTimingRaw;

    // Resolved values, populated only when valid
    private Long providerId;
    private String providerName;
    private Long employerId;
    private PricingScope pricingScope;
    private ContractStatus status;
    private PricingModel pricingModel;
    private BigDecimal discountPercent;
    private Boolean discountBeforeRejection;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate signedDate;
    private BigDecimal totalValue;
    private String currency;
    private String paymentTerms;
    private Boolean autoRenew;
    private String contactPerson;
    private String contactPhone;
    private String contactEmail;
    private String notes;

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public boolean isValid() {
        return errors == null || errors.isEmpty();
    }

    public void addError(String message) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(message);
    }
}
