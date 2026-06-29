package com.waad.tba.modules.settlement.dto;

import com.waad.tba.modules.settlement.entity.PaymentMethod;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentRecordDto {
    private Long id;
    private Long employerId;
    private String employerName;
    private Long providerId;
    private String providerName;
    private Integer targetYear;
    private Integer targetMonth;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private PaymentMethod paymentMethod;
    private String paymentMethodLabel;
    private String referenceNumber;
    private String notes;
    private String attachmentPath;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
