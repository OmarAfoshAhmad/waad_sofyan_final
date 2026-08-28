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
@PreAuthorize("@permissionGuard.has('FINANCIAL_REPORT_VIEW')")
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
    // NOTE (2026-07-27): restricted to internal finance staff. This report
    // aggregates across ALL employers/providers with no scoping mechanism
    // (AdjudicationReportService has no employerId/providerId scope param at
    // all) — EMPLOYER_ADMIN/PROVIDER_STAFF/MEDICAL_REVIEWER previously saw
    // every other tenant's adjudication totals via the free-text
    // providerName filter.
    @GetMapping("/adjudication")
    @PreAuthorize("@permissionGuard.has('FINANCIAL_REPORT_VIEW')")
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
    // Same scoping gap as /adjudication above — restricted to internal
    // finance staff.
    @GetMapping("/provider-settlement")
    @PreAuthorize("@permissionGuard.has('FINANCIAL_REPORT_VIEW')")
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
    @PreAuthorize("@claimAccessGuard.canReadMemberFor('FINANCIAL_REPORT_VIEW', #memberId)")
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

        // ACCOUNTANT/FINANCE_VIEWER/MEDICAL_REVIEWER are legitimately org-wide
        // internal staff (matches canAccessMember's own convention elsewhere);
        // only EMPLOYER_ADMIN must be confined to their own employer's members.
        var currentUser = authorizationService.getCurrentUser();
        if (authorizationService.isEmployerAdmin(currentUser)
                && !authorizationService.canAccessMember(currentUser, memberId)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("لا تملك صلاحية الوصول إلى كشف حساب هذا العضو"));
        }

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
    // Same scoping gap as /adjudication above — restricted to internal
    // finance staff (unscoped system-wide monthly summary).
    @GetMapping("/summary")
    @PreAuthorize("@permissionGuard.has('FINANCIAL_REPORT_VIEW')")
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
    @PreAuthorize("@permissionGuard.has('FINANCIAL_REPORT_VIEW')")
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
        
        Long scopedEmployerId = authorizationService.resolveEmployerScope(
            authorizationService.getCurrentUser(), employerOrgId);
        ClaimFinancialSummaryDto summary = claimFinancialSummaryService.getFinancialSummary(
            scopedEmployerId, fromDate, toDate);
        
        return ResponseEntity.ok(ApiResponse.success("تم استرجاع الملخص المالي", summary));
    }
    
    /**
     * Get settlement-focused summary for Settlement Inbox.
     * 
     * ملخص التسويات:
     * - المطالبات الموافق عليها والجاهزة للتسوية
     * - المبالغ المعلقة للدفع
     */
    // PROVIDER_STAFF removed (2026-07-27): this endpoint only accepts an
    // employerOrgId filter, with no providerId scoping mechanism at all, so
    // a provider caller previously saw every employer's AND every competing
    // provider's settlement totals with no way to confine it to their own.
    @GetMapping("/settlement-summary")
    @PreAuthorize("@permissionGuard.has('SETTLEMENT_VIEW')")
    @Operation(
        summary = "ملخص التسويات",
        description = "إجماليات للمطالبات الموافق عليها والمسددة"
    )
    public ResponseEntity<ApiResponse<ClaimFinancialSummaryDto>> getSettlementSummary(
            @Parameter(description = "فلتر حسب جهة العمل (اختياري)")
            @RequestParam(name = "employerOrgId", required = false) Long employerOrgId) {

        Long scopedEmployerId = authorizationService.resolveEmployerScope(
            authorizationService.getCurrentUser(), employerOrgId);
        ClaimFinancialSummaryDto summary = claimFinancialSummaryService.getSettlementSummary(scopedEmployerId);
        
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
    @PreAuthorize("@permissionGuard.has('SETTLEMENT_VIEW')")
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
        
        // Security: a PROVIDER_STAFF caller must always be forced to their own
        // bound provider — never to a raw request param. resolveProviderScope
        // returns null (not the requested id) for an unlinked provider account,
        // which correctly fails the validation below instead of falling
        // through to whatever providerId was passed.
        var currentUser = authorizationService.getCurrentUser();
        Long effectiveProviderId = authorizationService.resolveProviderScope(currentUser, providerId);
        Long scopedEmployerId = authorizationService.resolveEmployerScope(currentUser, employerOrgId);

        if (effectiveProviderId == null) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("معرف مقدم الخدمة مطلوب"));
        }

        ProviderSettlementReportDto report = providerSettlementReportService.generateReport(
            effectiveProviderId, scopedEmployerId, fromDate, toDate, statuses, claimNumber, preAuthNumber, memberId);

        return ResponseEntity.ok(ApiResponse.success("تم إنشاء تقرير التسوية", report));
    }
    
    /**
     * Get list of providers available for settlement reports.
     * 
     * Admin: returns all providers
     * Provider: returns only their provider
     */
    @GetMapping("/provider-settlements/providers")
    @PreAuthorize("@permissionGuard.has('SETTLEMENT_VIEW')")
    @Operation(
        summary = "قائمة مقدمي الخدمة للتقارير",
        description = "قائمة مقدمي الخدمة المتاحين لتقارير التسوية"
    )
    public ResponseEntity<ApiResponse<List<ProviderSettlementReportService.ProviderInfo>>> getProvidersForReport() {
        
        var currentUser = authorizationService.getCurrentUser();
        Long userProviderId = currentUser != null ? currentUser.getProviderId() : null;
        
        boolean isAdmin = currentUser != null && (
            authorizationService.isSuperAdmin(currentUser) || 
            authorizationService.isFinancialUser(currentUser)
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
    @PreAuthorize("@permissionGuard.has('FINANCIAL_REPORT_VIEW')")
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
        
        // Security: same resolveProviderScope/resolveEmployerScope pattern as
        // the view endpoint above — a PROVIDER_STAFF caller is always forced
        // to their own bound provider, never to the raw request param.
        var currentUser = authorizationService.getCurrentUser();
        Long effectiveProviderId = authorizationService.resolveProviderScope(currentUser, providerId);
        Long scopedEmployerId = authorizationService.resolveEmployerScope(currentUser, employerOrgId);

        if (effectiveProviderId == null) {
            return ResponseEntity.badRequest().build();
        }

        try {
            // Generate report using SAME service as UI (no recalculation)
            ProviderSettlementReportDto report = providerSettlementReportService.generateReport(
                effectiveProviderId, scopedEmployerId, fromDate, toDate, statuses, claimNumber, preAuthNumber, memberId);

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

