package com.waad.tba.modules.settlement.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class MonthlySettlementSummaryDto {
    private Long employerId;
    private String employerName;
    private Long providerId;
    private String providerName;
    
    private Integer targetYear;
    private Integer targetMonth;
    
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal remainingAmount;
    
    private LocalDate lastPaymentDate;
    private String paymentStatus; // UNPAID, PARTIALLY_PAID, FULLY_PAID
    private String paymentStatusLabel;
}
