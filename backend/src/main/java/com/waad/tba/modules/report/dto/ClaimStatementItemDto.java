package com.waad.tba.modules.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimStatementItemDto {
    private String medicalService;     // اسم الخدمة
    private LocalDate serviceDate;       // تاريخ الخدمة
    private BigDecimal grossAmount;      // الإجمالي (المطلوب)
    private BigDecimal netAmount;        // الصافي (المعتمد)
    private BigDecimal rejectedAmount;   // المرفوض
    private String rejectionReason;    // سبب الرفض
    private String rejectionReasonArabic;
}
