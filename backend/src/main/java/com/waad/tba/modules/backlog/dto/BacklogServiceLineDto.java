package com.waad.tba.modules.backlog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacklogServiceLineDto {
    private String serviceCode;
    private String serviceName;

    @NotNull(message = "الكمية مطلوبة")
    @Min(value = 1, message = "يجب أن تكون الكمية 1 على الأقل")
    private Integer quantity;

    @NotNull(message = "سعر الوحدة مطلوب")
    @DecimalMin(value = "0.0", message = "سعر الوحدة لا يمكن أن يكون سالباً")
    private BigDecimal grossAmount;

    private BigDecimal coveredAmount;
    private BigDecimal netAmount;
    private Integer coveragePercent;
    private Integer timesLimit;
    private BigDecimal amountLimit;
    private BigDecimal refusedAmount;
    private Boolean rejected;
    private String rejectionReason;
}
