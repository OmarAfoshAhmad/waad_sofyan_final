package com.waad.tba.modules.claim.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PendingServiceCreateRequest {
    @Size(max = 50) private String serviceCode;
    @NotBlank @Size(max = 255) private String serviceName;
    @Positive private Long proposedCategoryId;
    @Size(max = 50) private String proposedCategoryCode;
    @Size(max = 200) private String proposedCategoryName;
    private Boolean newCategoryRequested;
    @NotNull @DecimalMin(value = "0.01") private BigDecimal proposedUnitPrice;
}
