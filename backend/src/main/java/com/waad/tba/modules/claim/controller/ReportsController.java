package com.waad.tba.modules.claim.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.claim.dto.AdjudicationReportDto;
import com.waad.tba.modules.claim.dto.ClaimFinancialSummaryDto;
import com.waad.tba.modules.claim.dto.ProviderSettlementReportDto;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.service.AdjudicationReportService;
import com.waad.tba.modules.claim.service.ClaimFinancialSummaryService;
import com.waad.tba.modules.claim.service.ProviderSettlementExcelExporter;
import com.waad.tba.modules.claim.service.ProviderSettlementReportService;
import com.waad.tba.security.AuthorizationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reports Controller - Adjudication & Settlement Reports.
 * 
 * تقارير التدقيق المالي والتسويات.
 * 
 * القاعدة: المطلوب = تحمل المريض + المستحق للمستشفى
 * RequestedAmount = PatientCoPay + NetProviderAmount
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Financial Reports - Adjudication & Settlement")
public class ReportsController {
    
    private final AdjudicationReportService adjudicationReportService;
    private final ClaimFinancialSummaryService claimFinancialSummaryService;
    private final ProviderSettlementReportService providerSettlementReportService;
    private final ProviderSettlementExcelExporter providerSettlementExcelExporter;
    private final AuthorizationService authorizationService;
    
    /**
     * Generate Adjudication Report.
     * 
     * تقرير التدقيق المالي:
     * - المبالغ المطلوبة من كل مقدم خدمة
     * - المبالغ المستقطعة (تحمل المريض)
     * - المبالغ المستحقة للدفع
     */
    @GetMapping("/adjudication")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER', 'EMPLOYER_ADMIN')")
    @Operation(
        summary = "تقرير التدقيق المالي",
        description = "يُظهر: المطلوب | المستقطع (تحمل المريض) | المستحق للمستشفى"
    )
    public ResponseEntity<ApiResponse<AdjudicationReportDto>> getAdjudicationReport(
            @Parameter(description = "تاريخ البداية (YYYY-MM-DD)")
            @RequestParam(name = "fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            
            @Parameter(description = "تاريخ النهاية (YYYY-MM-DD)")
            @RequestParam(name = "toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            
            @Parameter(description = "فلترة حسب مقدم الخدمة")
            @RequestParam(name = "providerName", required = false) String providerName,
            
            @Parameter(description = "فلترة حسب حالة المطالبة")
            @RequestParam(name = "statuses", required = false) List<ClaimStatus> statuses) {
        
        AdjudicationReportDto report = adjudicationReportService.generateReport(
            fromDate, toDate, providerName, statuses);
        
        return ResponseEntity.ok(ApiResponse.success("تم إنشاء تقرير التدقيق المالي", report));
    }
    
    /**
     * Generate Provider Settlement Report.
     * 
     * تقرير التسوية لمقدم خدمة معين:
     * - المطالبات الموافق عليها والجاهزة للدفع
     */
    @GetMapping("/provider-settlement")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER', 'EMPLOYER_ADMIN')")
    @Operation(
        summary = "تقرير تسوية مقدم الخدمة",
        description = "المطالبات الموافق عليها والجاهزة للتسوية"
    )
    public ResponseEntity<ApiResponse<AdjudicationReportDto>> getProviderSettlementReport(
            @Parameter(description = "اسم مقدم الخدمة")
            @RequestParam(name = "providerName", required = false) String providerName) {
        
        AdjudicationReportDto report = adjudicationReportService.generateProviderSettlementReport(providerName);
        return ResponseEntity.ok(ApiResponse.success("تم إنشاء تقرير التسوية", report));
    }
    
    /**
     * Generate Member Statement.
     * 
     * كشف حساب العضو:
     * - جميع المطالبات للعضو
     * - إجمالي المدفوعات والتحملات
     */
    @GetMapping("/member-statement/{memberId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER', 'EMPLOYER_ADMIN')")
    @Operation(
        summary = "كشف حساب العضو",
        description = "جميع مطالبات العضو مع الإجماليات"
    )
    public ResponseEntity<ApiResponse<AdjudicationReportDto>> getMemberStatement(
            @PathVariable("memberId") Long memberId,
            
            @Parameter(description = "تاريخ البداية (اختياري)")
            @RequestParam(name = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            
            @Parameter(description = "تاريخ النهاية (اختياري)")
            @RequestParam(name = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        
        // Default to current year if dates not specified
        if (fromDate == null) {
            fromDate = LocalDate.now().withDayOfYear(1);
        }
        if (toDate == null) {
            toDate = LocalDate.now();
        }
        
        AdjudicationReportDto report = adjudicationReportService.generateMemberStatement(
            memberId, fromDate, toDate);
        
        return ResponseEntity.ok(ApiResponse.success("تم إنشاء كشف حساب العضو", report));
    }
    
    /**
     * Get Summary Statistics for Dashboard.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER', 'EMPLOYER_ADMIN')")
    @Operation(
        summary = "ملخص الإحصائيات",
        description = "إحصائيات سريعة للوحة التحكم"
    )
    public ResponseEntity<ApiResponse<AdjudicationReportDto>> getSummary() {
        // Current month
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        
        AdjudicationReportDto report = adjudicationReportService.generateReport(
            monthStart, today, null, null);
        
        return ResponseEntity.ok(ApiResponse.success("ملخص الشهر الحالي", report));
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // FINANCIAL SUMMARY ENDPOINTS - SINGLE SOURCE OF TRUTH
    // ═══════════════════════════════════════════════════════════════════════════════
    // These endpoints provide authoritative financial totals from database.
    // Frontend MUST use these - NO client-side .reduce() calculations allowed.
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Get comprehensive financial summary - AUTHORITATIVE totals.
     * 
     * تقرير مالي شامل:
     * - الإجماليات محسوبة من قاعدة البيانات مباشرة
     * - يمنع الواجهة من حساب المبالغ محلياً
     */
    @GetMapping("/financial-summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER', 'EMPLOYER_ADMIN')")
    @Operation(
        summary = "الملخص المالي الشامل",
        description = "إجماليات مالية محسوبة من قاعدة البيانات - المصدر الوحيد للحقيقة"
    )
    public ResponseEntity<ApiResponse<ClaimFinancialSummaryDto>> getFinancialSummary(
            @Parameter(description = "فلتر حسب جهة العمل (اختياري)")
            @RequestParam(name = "employerOrgId", required = false) Long employerOrgId,
            
            @Parameter(description = "تاريخ البداية (اختياري)")
            @RequestParam(name = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            
            @Parameter(description = "تاريخ النهاية (اختياري)")
            @RequestParam(name = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        
        ClaimFinancialSummaryDto summary = claimFinancialSummaryService.getFinancialSummary(
            employerOrgId, fromDate, toDate);
        
        return ResponseEntity.ok(ApiResponse.success("تم استرجاع الملخص المالي", summary));
    }
    
    /**
     * Get settlement-focused summary for Settlement Inbox.
     * 
     * ملخص التسويات:
     * - المطالبات الموافق عليها والجاهزة للتسوية
     * - المبالغ المعلقة للدفع
     */
    @GetMapping("/settlement-summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(
        summary = "ملخص التسويات",
        description = "إجماليات للمطالبات الموافق عليها والمسددة"
    )
    public ResponseEntity<ApiResponse<ClaimFinancialSummaryDto>> getSettlementSummary(
            @Parameter(description = "فلتر حسب جهة العمل (اختياري)")
            @RequestParam(name = "employerOrgId", required = false) Long employerOrgId) {
        
        ClaimFinancialSummaryDto summary = claimFinancialSummaryService.getSettlementSummary(employerOrgId);
        
        return ResponseEntity.ok(ApiResponse.success("تم استرجاع ملخص التسويات", summary));
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // PROVIDER SETTLEMENT REPORTS - LINE-LEVEL DETAIL (CANONICAL)
    // ═══════════════════════════════════════════════════════════════════════════════
    // These endpoints provide detailed settlement reports for providers.
    // Matches paper reports: Gross, Net, Rejected, Patient Share per service line.
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Generate Provider Settlement Report with line-level detail.
     * 
     * تقرير تسوية مقدم الخدمة:
     * - تفاصيل على مستوى الخدمة/السطر
     * - المبلغ الإجمالي (Gross) والمعتمد (Net) والمرفوض
     * - حصة المؤمن عليه وصافي المستحق للمقدم
     * 
     * Access:
     * - ADMIN/FINANCE: Can view any provider
     * - PROVIDER: Can only view their own provider (providerId ignored, uses token)
     */
    @GetMapping("/provider-settlements")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(
        summary = "تقرير تسوية مقدم الخدمة",
        description = "تقرير مفصل على مستوى الخدمة/السطر يطابق التقارير الورقية"
    )
    public ResponseEntity<ApiResponse<ProviderSettlementReportDto>> getProviderSettlementReport(
            @Parameter(description = "معرف مقدم الخدمة (إجباري للأدمن، يُتجاهل للمقدمين)")
            @RequestParam(name = "providerId", required = false) Long providerId,

            @Parameter(description = "فلترة حسب جهة العمل (اختياري)")
            @RequestParam(name = "employerOrgId", required = false) Long employerOrgId,
            
            @Parameter(description = "تاريخ البداية (YYYY-MM-DD)")
            @RequestParam(name = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            
            @Parameter(description = "تاريخ النهاية (YYYY-MM-DD)")
            @RequestParam(name = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            
            @Parameter(description = "فلترة حسب حالة المطالبة (APPROVED, SETTLED)")
            @RequestParam(name = "statuses", required = false) List<ClaimStatus> statuses,
            
            @Parameter(description = "فلترة حسب رقم المطالبة")
            @RequestParam(name = "claimNumber", required = false) String claimNumber,
            
            @Parameter(description = "فلترة حسب رقم الموافقة المسبقة")
            @RequestParam(name = "preAuthNumber", required = false) String preAuthNumber,
            
            @Parameter(description = "فلترة حسب المريض")
            @RequestParam(name = "memberId", required = false) Long memberId) {
        
        // Security: PROVIDER users can only see their own provider
        Long effectiveProviderId = providerId;
        var currentUser = authorizationService.getCurrentUser();
        
        if (currentUser != null) {
            boolean isAdmin = authorizationService.isSuperAdmin(currentUser) || 
                             authorizationService.isInsuranceAdmin(currentUser);
            
            if (!isAdmin && currentUser.getProviderId() != null) {
                // Provider user: force their own provider ID
                effectiveProviderId = currentUser.getProviderId();
            }
        }
        
        // Validate provider ID
        if (effectiveProviderId == null) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("معرف مقدم الخدمة مطلوب"));
        }
        
        ProviderSettlementReportDto report = providerSettlementReportService.generateReport(
            effectiveProviderId, employerOrgId, fromDate, toDate, statuses, claimNumber, preAuthNumber, memberId);
        
        return ResponseEntity.ok(ApiResponse.success("تم إنشاء تقرير التسوية", report));
    }
    
    /**
     * Get list of providers available for settlement reports.
     * 
     * Admin: returns all providers
     * Provider: returns only their provider
     */
    @GetMapping("/provider-settlements/providers")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(
        summary = "قائمة مقدمي الخدمة للتقارير",
        description = "قائمة مقدمي الخدمة المتاحين لتقارير التسوية"
    )
    public ResponseEntity<ApiResponse<List<ProviderSettlementReportService.ProviderInfo>>> getProvidersForReport() {
        
        var currentUser = authorizationService.getCurrentUser();
        Long userProviderId = currentUser != null ? currentUser.getProviderId() : null;
        
        boolean isAdmin = currentUser != null && (
            authorizationService.isSuperAdmin(currentUser) || 
            authorizationService.isInsuranceAdmin(currentUser)
        );
        
        List<ProviderSettlementReportService.ProviderInfo> providers = 
            providerSettlementReportService.getAvailableProviders(userProviderId, isAdmin);
        
        return ResponseEntity.ok(ApiResponse.success("قائمة مقدمي الخدمة", providers));
    }
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // EXCEL EXPORT ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Export Provider Settlement Report to Excel.
     * 
     * تصدير تقرير تسوية مقدم الخدمة إلى Excel:
     * - نفس البيانات المعروضة على الشاشة
     * - لا يوجد إعادة حساب للأرقام
     * - يتم التحقق من تناسق الأرقام المالية (تحذير فقط)
     * 
     * Access:
     * - ADMIN/FINANCE: Can export any provider
     * - PROVIDER: Can only export their own provider
     */
    @GetMapping("/provider-settlements/export/excel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT', 'FINANCE_VIEWER', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(
        summary = "تصدير تقرير التسوية إلى Excel",
        description = "تصدير تقرير تسوية مقدم الخدمة بصيغة Excel - نفس أرقام الشاشة"
    )
    public ResponseEntity<byte[]> exportProviderSettlementToExcel(
            @Parameter(description = "معرف مقدم الخدمة (إجباري للأدمن، يُتجاهل للمقدمين)")
            @RequestParam(name = "providerId", required = false) Long providerId,

            @Parameter(description = "فلترة حسب جهة العمل (اختياري)")
            @RequestParam(name = "employerOrgId", required = false) Long employerOrgId,
            
            @Parameter(description = "تاريخ البداية (YYYY-MM-DD)")
            @RequestParam(name = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            
            @Parameter(description = "تاريخ النهاية (YYYY-MM-DD)")
            @RequestParam(name = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            
            @Parameter(description = "فلترة حسب حالة المطالبة (APPROVED, SETTLED)")
            @RequestParam(name = "statuses", required = false) List<ClaimStatus> statuses,
            
            @Parameter(description = "فلترة حسب رقم المطالبة")
            @RequestParam(name = "claimNumber", required = false) String claimNumber,
            
            @Parameter(description = "فلترة حسب رقم الموافقة المسبقة")
            @RequestParam(name = "preAuthNumber", required = false) String preAuthNumber,
            
            @Parameter(description = "فلترة حسب المريض")
            @RequestParam(name = "memberId", required = false) Long memberId) {
        
        log.info("📊 [EXCEL-EXPORT] Export request for provider: {}", providerId);
        
        // Security: PROVIDER users can only see their own provider
        Long effectiveProviderId = providerId;
        var currentUser = authorizationService.getCurrentUser();
        
        if (currentUser != null) {
            boolean isAdmin = authorizationService.isSuperAdmin(currentUser) || 
                             authorizationService.isInsuranceAdmin(currentUser);
            
            if (!isAdmin && currentUser.getProviderId() != null) {
                // Provider user: force their own provider ID
                effectiveProviderId = currentUser.getProviderId();
            }
        }
        
        // Validate provider ID
        if (effectiveProviderId == null) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            // Generate report using SAME service as UI (no recalculation)
            ProviderSettlementReportDto report = providerSettlementReportService.generateReport(
                effectiveProviderId, employerOrgId, fromDate, toDate, statuses, claimNumber, preAuthNumber, memberId);
            
            // Export to Excel
            byte[] excelBytes = providerSettlementExcelExporter.exportToExcel(report);
            
            // Generate filename
            String filename = generateExcelFilename(report);
            
            log.info("📊 [EXCEL-EXPORT] Export completed: {} bytes, filename: {}", 
                excelBytes.length, filename);
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(excelBytes.length)
                .body(excelBytes);
                
        } catch (IOException e) {
            log.error("❌ [EXCEL-EXPORT] Failed to export report", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Generate Excel filename with provider and date range.
     */
    private String generateExcelFilename(ProviderSettlementReportDto report) {
        String providerName = report.getProviderName() != null ? 
            report.getProviderName().replaceAll("[^a-zA-Z0-9\\u0600-\\u06FF]", "_") : "Provider";
        String fromDate = report.getFromDate() != null ? 
            report.getFromDate().format(DateTimeFormatter.ofPattern("yyyyMMdd")) : "start";
        String toDate = report.getToDate() != null ? 
            report.getToDate().format(DateTimeFormatter.ofPattern("yyyyMMdd")) : "end";
        
        return String.format("Settlement_Report_%s_%s_%s.xlsx", providerName, fromDate, toDate);
    }
}

