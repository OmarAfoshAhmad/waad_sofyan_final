package com.waad.tba.modules.settlement.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentAuditLogDto {
    private Long id;
    private Long paymentId;
    private String userId;
    private String actionType;
    private BigDecimal oldAmount;
    private BigDecimal newAmount;
    private String reason;
    private LocalDateTime timestamp;
}
