package com.waad.tba.modules.claim.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EligiblePreAuthorizationDto(
        Long id, String number, String status, String serviceName,
        LocalDate expectedServiceDate, LocalDate expiryDate, BigDecimal approvedAmount) {
}
