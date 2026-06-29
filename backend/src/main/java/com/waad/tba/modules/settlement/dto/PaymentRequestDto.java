package com.waad.tba.modules.settlement.dto;

import com.waad.tba.modules.settlement.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PaymentRequestDto {

    @NotNull(message = "الشركة مطلوبة")
    private Long employerId;

    @NotNull(message = "مقدم الخدمة مطلوب")
    private Long providerId;

    @NotNull(message = "سنة الاستحقاق مطلوبة")
    private Integer targetYear;

    @NotNull(message = "شهر الاستحقاق مطلوب")
    private Integer targetMonth;

    @NotNull(message = "مبلغ الدفعة مطلوب")
    @DecimalMin(value = "0.01", message = "مبلغ الدفعة يجب أن يكون أكبر من الصفر")
    private BigDecimal amount;

    @NotNull(message = "تاريخ الدفع مطلوب")
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate paymentDate;

    @NotNull(message = "طريقة الدفع مطلوبة")
    private PaymentMethod paymentMethod;

    private String referenceNumber;
    private String notes;
    private String attachmentPath;

    // Fields required for update/delete or overriding limits
    private String reason;
    private boolean overrideLimit;
}
