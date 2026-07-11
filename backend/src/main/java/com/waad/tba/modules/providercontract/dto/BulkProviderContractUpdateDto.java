package com.waad.tba.modules.providercontract.dto;

import com.waad.tba.modules.providercontract.entity.ProviderContract.ContractStatus;
import com.waad.tba.modules.providercontract.entity.ProviderContract.PricingModel;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkProviderContractUpdateDto {

    @NotEmpty(message = "يجب تحديد العقود المراد تحديثها")
    private List<Long> contractIds;

    // Fields to update
    private ContractStatus status;
    private PricingModel pricingModel;
    private BigDecimal discountPercent;
    private Boolean discountBeforeRejection;
    private LocalDate startDate;
    private LocalDate endDate;

    // Flags to indicate which fields should actually be updated
    private boolean updateStatus;
    private boolean updatePricingModel;
    private boolean updateDiscountPercent;
    private boolean updateDiscountTiming;
    private boolean updateStartDate;
    private boolean updateEndDate;
}
