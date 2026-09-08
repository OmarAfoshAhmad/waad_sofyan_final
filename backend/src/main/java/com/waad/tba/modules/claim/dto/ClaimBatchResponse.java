package com.waad.tba.modules.claim.dto;

import com.waad.tba.modules.claim.entity.ClaimBatch;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ClaimBatchResponse {
    private Long id;
    private String batchCode;
    private Long providerId;
    private Long employerId;
    private Integer year;
    private Integer month;
    private String monthLabel;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private String status;
    private String statusLabel;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
    private Long claimsCount;
    private BigDecimal totalClaimsAmount;
    private BigDecimal totalApprovedAmount;
    private BigDecimal totalRefusedAmount;
    private BigDecimal totalPatientShare;

    public static ClaimBatchResponse from(ClaimBatch batch) {
        if (batch == null) return null;
        
        return ClaimBatchResponse.builder()
            .id(batch.getId())
            .batchCode(batch.getBatchCode())
            .providerId(batch.getProviderId())
            .employerId(batch.getEmployerId())
            .year(batch.getBatchYear())
            .month(batch.getBatchMonth())
            .monthLabel(getMonthLabelAr(batch.getBatchMonth()))
            .periodStart(batch.getPeriodStart())
            .periodEnd(batch.getPeriodEnd())
            .status(batch.getStatus().name())
            .statusLabel(batch.getStatus().getArabicLabel())
            .createdAt(batch.getCreatedAt())
            .closedAt(batch.getClosedAt())
            .build();
    }

    public static ClaimBatchResponse from(ClaimBatch batch, Object[] totals) {
        ClaimBatchResponse response = from(batch);
        if (response == null) return null;
        totals = unwrapSingleResultRow(totals);
        response.setClaimsCount(numberAt(totals, 0).longValue());
        response.setTotalClaimsAmount(decimalAt(totals, 1));
        response.setTotalApprovedAmount(decimalAt(totals, 2));
        response.setTotalRefusedAmount(decimalAt(totals, 3));
        response.setTotalPatientShare(decimalAt(totals, 4));
        return response;
    }

    private static Object[] unwrapSingleResultRow(Object[] totals) {
        if (totals != null && totals.length == 1 && totals[0] instanceof Object[] row) {
            return row;
        }
        return totals;
    }

    private static Number numberAt(Object[] totals, int index) {
        if (totals == null || totals.length <= index || totals[index] == null) return 0L;
        return (Number) totals[index];
    }

    private static BigDecimal decimalAt(Object[] totals, int index) {
        if (totals == null || totals.length <= index || totals[index] == null) return BigDecimal.ZERO;
        if (totals[index] instanceof BigDecimal value) return value;
        if (totals[index] instanceof Number value) return BigDecimal.valueOf(value.doubleValue());
        return BigDecimal.ZERO;
    }

    private static String getMonthLabelAr(int month) {
        String[] months = {
            "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
        };
        if (month >= 1 && month <= 12) return months[month - 1];
        return String.valueOf(month);
    }
}
