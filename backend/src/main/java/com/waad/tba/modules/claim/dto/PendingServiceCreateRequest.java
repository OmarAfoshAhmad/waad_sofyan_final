package com.waad.tba.modules.claim.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PendingServiceCreateRequest {
    @Size(max = 50) private String serviceCode;
    @NotBlank @Size(max = 255) private String serviceName;
    @NotNull @Positive private Long proposedCategoryId;
    @NotNull @DecimalMin(value = "0.01") private BigDecimal proposedUnitPrice;
}
