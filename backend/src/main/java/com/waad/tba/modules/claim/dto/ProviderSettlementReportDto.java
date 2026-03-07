package com.waad.tba.modules.claim.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Provider Settlement Report (Line-Level Detail).
 * 
 * تقرير تسوية مقدم الخدمة على مستوى الخدمة/السطر:
 * 
 * ╔═══════════════════════════════════════════════════════════════════════════════╗
 * ║                    PROVIDER SETTLEMENT REPORT DTO                            ║
 * ║───────────────────────────────────────────────────────────────────────────────║
 * ║ Purpose: Detailed financial report for settlement with healthcare providers  ║
 * ║ Source: claims + claim_lines + members + medical_services + pre_auths       ║
 * ║ Calculation: ALL amounts calculated in Backend (NO client-side math)         ║
 * ╚═══════════════════════════════════════════════════════════════════════════════╝
 * 
 * Endpoint: GET /api/reports/provider-settlements
 * 
 * Columns match the paper reports:
 * - رقم المطالبة (Claim Number)
 * - رقم الموافقة (Pre-Auth Number)
 * - اسم المريض (Patient Name)
 * - رقم التأمين (Insurance Number)
 * - الخدمة الطبية (Medical Service)
 * - تاريخ الخدمة (Service Date)
 * - المبلغ الإجمالي Gross (unit_price × quantity)
 * - المبلغ المعتمد Net (approved_amount)
 * - المبلغ المرفوض (gross - approved)
 * - سبب الرفض (rejection_reason)
 * - حصة المؤمن عليه (patient_share)
 * - حالة السطر (APPROVED / PARTIAL / REJECTED)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderSettlementReportDto {
    
    // ==================== REPORT HEADER ====================
    
    /**
     * Report reference number (e.g., LCC25-00603-12/2025)
     */
    private String reportNumber;
    
    /**
     * Report generation date
     */
    private LocalDate reportDate;
    
    /**
     * Report period
     */
    private LocalDate fromDate;
    private LocalDate toDate;
    
    /**
     * Provider information
     */
    private Long providerId;
    private String providerName;
    private String providerCode;
    
    // ==================== SUMMARY TOTALS (HEADER-LEVEL) ====================
    
    /**
     * إجمالي المطالبات المقدمة
     * Total claims submitted
     */
    private Long totalClaimsCount;
    
    /**
     * إجمالي المبلغ المطلوب (Gross)
     * Total requested amount = SUM(claim_lines.total_price)
     */
    private BigDecimal totalRequestedAmount;
    
    /**
     * إجمالي المبلغ المعتمد (Net)
     * Total approved amount = SUM(claim_lines.approved_amount)
     */
    private BigDecimal totalApprovedAmount;
    
    /**
     * إجمالي المبلغ المرفوض
     * Total rejected = totalRequested - totalApproved
     */
    private BigDecimal totalRejectedAmount;
    
    /**
     * إجمالي ما دُفع من المؤمن عليه (Patient Share / CoPay)
     * Total patient share = SUM(claims.patient_copay)
     */
    private BigDecimal totalPatientShare;
    
    /**
     * صافي المستحق لمقدم الخدمة
     * Net provider amount = totalApproved - totalPatientShare
     */
    private BigDecimal netProviderAmount;
    
    // ==================== CLAIM DETAILS (GROUPED) ====================
    
    /**
     * Claims with their line-level details
     */
    @Builder.Default
    private List<ClaimDetail> claims = new ArrayList<>();
    
    // ==================== NESTED CLASSES ====================
    
    /**
     * Claim-level detail with nested service lines
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimDetail {
        
        /**
         * رقم المطالبة
         */
        private Long claimId;
        private String claimNumber;
        
        /**
         * رقم الموافقة المسبقة (إن وجدت)
         */
        private String preAuthNumber;
        
        /**
         * بيانات المريض
         */
        private String patientName;
        private String insuranceNumber;
        
        /**
         * التشخيص
         */
        private String diagnosisCode;
        private String diagnosisDescription;
        
        /**
         * تاريخ الخدمة
         */
        private LocalDate serviceDate;
        
        /**
         * الحالة
         */
        private String status;
        private String statusArabic;
        
        /**
         * إجماليات المطالبة
         */
        private BigDecimal grossAmount;      // إجمالي المطلوب
        private BigDecimal netAmount;        // المعتمد
        private BigDecimal rejectedAmount;   // المرفوض
        private BigDecimal patientShare;     // حصة المريض
        
        /**
         * تفاصيل الخدمات (السطور)
         */
        @Builder.Default
        private List<ServiceLineDetail> lines = new ArrayList<>();
    }
    
    /**
     * Service line-level detail (each row in the report)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceLineDetail {
        
        /**
         * Line ID
         */
        private Long lineId;
        
        /**
         * Medical Service
         */
        private Long medicalServiceId;
        private String serviceCode;
        private String serviceName;
        private String serviceCategory;
        
        /**
         * تاريخ الخدمة
         */
        private LocalDate serviceDate;
        
        /**
         * الكمية
         */
        private Integer quantity;
        
        /**
         * سعر الوحدة (من عقد المقدم)
         */
        private BigDecimal unitPrice;
        
        /**
         * المبلغ الإجمالي (Gross) = quantity × unitPrice
         */
        private BigDecimal grossAmount;
        
        /**
         * المبلغ المعتمد (Net/Approved)
         */
        private BigDecimal approvedAmount;
        
        /**
         * المبلغ المرفوض = gross - approved
         */
        private BigDecimal rejectedAmount;
        
        /**
         * سبب الرفض
         */
        private String rejectionReason;
        
        /**
         * حصة المؤمن عليه (Patient Share)
         * Calculated at line level if applicable
         */
        private BigDecimal patientShare;
        
        /**
         * حالة السطر
         */
        private LineStatus lineStatus;
        private String lineStatusArabic;
    }
    
    /**
     * Line status enum
     */
    public enum LineStatus {
        APPROVED("معتمد"),
        PARTIAL("معتمد جزئياً"),
        REJECTED("مرفوض"),
        PENDING("قيد المراجعة");
        
        private final String arabicLabel;
        
        LineStatus(String arabicLabel) {
            this.arabicLabel = arabicLabel;
        }
        
        public String getArabicLabel() {
            return arabicLabel;
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Calculate line status based on amounts
     */
    public static LineStatus calculateLineStatus(BigDecimal gross, BigDecimal approved) {
        if (gross == null || approved == null) {
            return LineStatus.PENDING;
        }
        
        int comparison = approved.compareTo(BigDecimal.ZERO);
        if (comparison == 0) {
            return LineStatus.REJECTED;
        }
        
        if (approved.compareTo(gross) >= 0) {
            return LineStatus.APPROVED;
        }
        
        return LineStatus.PARTIAL;
    }
    
    /**
     * Get Arabic summary for PDF header
     */
    public String getReportHeaderArabic() {
        return String.format(
            "══════════════════════════════════════════════════════════════════\n" +
            "                    تقرير تسوية مقدم الخدمة                         \n" +
            "══════════════════════════════════════════════════════════════════\n" +
            "📋 رقم التقرير: %s\n" +
            "📅 التاريخ: %s\n" +
            "🏥 مقدم الخدمة: %s\n" +
            "══════════════════════════════════════════════════════════════════\n" +
            "📝 عدد المطالبات المستلمة: %d\n" +
            "💰 إجمالي القيمة المقدمة من المرفق: %.3f د.ل\n" +
            "❌ إجمالي القيمة الغير مستحقة: %.3f د.ل\n" +
            "👤 إجمالي القيمة المدفوعة من المؤمن: %.3f د.ل\n" +
            "✅ صافي القيمة المستحق للمرفق: %.3f د.ل\n" +
            "══════════════════════════════════════════════════════════════════",
            reportNumber != null ? reportNumber : "-",
            reportDate != null ? reportDate.toString() : "-",
            providerName != null ? providerName : "-",
            totalClaimsCount != null ? totalClaimsCount : 0,
            totalRequestedAmount != null ? totalRequestedAmount : java.math.BigDecimal.ZERO,
            totalRejectedAmount != null ? totalRejectedAmount : java.math.BigDecimal.ZERO,
            totalPatientShare != null ? totalPatientShare : java.math.BigDecimal.ZERO,
            netProviderAmount != null ? netProviderAmount : java.math.BigDecimal.ZERO
        );
    }
}
