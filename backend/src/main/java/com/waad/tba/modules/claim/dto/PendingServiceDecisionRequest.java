package com.waad.tba.modules.claim.dto;

import com.waad.tba.modules.claim.entity.PendingServiceStatus;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PendingServiceDecisionRequest {
    @NotNull private PendingServiceStatus decision;
    @NotBlank @Size(max = 2000) private String reason;
    private String finalServiceCode;
    private String finalServiceName;
    private Long finalCategoryId;
    @DecimalMin(value = "0.01") private BigDecimal finalUnitPrice;
    private Long linkedPricingItemId;
    private LocalDate contractEffectiveFrom;
}
