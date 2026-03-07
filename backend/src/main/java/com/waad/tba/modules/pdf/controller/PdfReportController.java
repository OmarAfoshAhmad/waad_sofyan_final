package com.waad.tba.modules.pdf.controller;

import com.waad.tba.services.pdf.HtmlToPdfService;
import com.waad.tba.services.pdf.PdfTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * PDF Report Generation Controller
 * 
 * Example controller demonstrating PDF generation
 * using Thymeleaf templates and Flying Saucer.
 * 
 * @since 2026-01-11
 * @deprecated PDF export disabled. Excel is the official reporting format.
 *             All endpoints in this controller are disabled.
 *             Kept for potential legal/compliance reports in the future.
 */
@Slf4j
// @RestController  // DISABLED - PDF export not active, Excel is official format
// @RequestMapping("/api/v1/pdf/reports")
@RequiredArgsConstructor
@Deprecated(since = "2026-01", forRemoval = false)
@SuppressWarnings("dep-ann")
public class PdfReportController {
    
    private final PdfTemplateService templateService;
    private final HtmlToPdfService htmlToPdfService;
    
    /**
     * Generate Claims Report PDF
     * 
     * Example endpoint showing how to:
     * 1. Prepare data
     * 2. Process Thymeleaf template
     * 3. Convert to PDF
     * 4. Return as downloadable file
     */
    @GetMapping("/claims/sample")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<byte[]> generateSampleClaimsReport() throws IOException {
        log.info("[PdfReportController] Generating sample claims report");
        
        // 1. Prepare data for template
        Map<String, Object> data = new HashMap<>();
        
        data.put("reportDate", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        data.put("claimsCount", 5);
        data.put("totalAmount", 15000.00);
        data.put("approvedAmount", 12000.00);
        data.put("rejectedAmount", 2000.00);
        data.put("pendingAmount", 1000.00);
        data.put("notes", "هذا تقرير تجريبي للمطالبات");
        
        // Sample claims list
        List<Map<String, Object>> claims = new ArrayList<>();
        claims.add(createSampleClaim("CLM-001", "محمد أحمد علي", "مستشفى النور", "2026-01-10", 3000.00, "معتمد", "-"));
        claims.add(createSampleClaim("CLM-002", "فاطمة سعيد", "عيادة الأمل", "2026-01-09", 1500.00, "معتمد", "-"));
        claims.add(createSampleClaim("CLM-003", "أحمد خالد", "مركز الرعاية", "2026-01-08", 2500.00, "مرفوض", "تجاوز الحد"));
        claims.add(createSampleClaim("CLM-004", "نورة عبدالله", "مستشفى الملك", "2026-01-07", 7000.00, "معتمد", "-"));
        claims.add(createSampleClaim("CLM-005", "عبدالرحمن محمد", "عيادة الحياة", "2026-01-06", 1000.00, "قيد المراجعة", "-"));
        
        data.put("claims", claims);
        
        // 2. Process Thymeleaf template
        String html = templateService.processTemplate("pdf/claims-report", data);
        
        // 3. Convert HTML to PDF
        byte[] pdfBytes = htmlToPdfService.convertHtmlToPdf(html);
        
        // 4. Return as downloadable PDF
        String filename = "claims-report-" + LocalDate.now() + ".pdf";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(pdfBytes.length);
        
        log.info("[PdfReportController] Claims report generated: {} bytes", pdfBytes.length);
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(pdfBytes);
    }
    
    /**
     * Generate Claims Report with parameters
     */
    @PostMapping("/claims")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<byte[]> generateClaimsReport(
            @RequestBody Map<String, Object> requestData
    ) throws IOException {
        log.info("[PdfReportController] Generating claims report with custom data");
        
        // Process template with provided data
        String html = templateService.processTemplate("pdf/claims-report", requestData);
        
        // Convert to PDF
        byte[] pdfBytes = htmlToPdfService.convertHtmlToPdf(html);
        
        // Generate filename
        String filename = "claims-report-" + 
            requestData.getOrDefault("reportDate", LocalDate.now()) + 
            ".pdf";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", filename);
        headers.setContentLength(pdfBytes.length);
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(pdfBytes);
    }
    
    /**
     * Preview HTML (for debugging)
     */
    @GetMapping("/claims/preview-html")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<String> previewClaimsHtml() {
        log.info("[PdfReportController] Generating HTML preview");
        
        Map<String, Object> data = new HashMap<>();
        data.put("reportDate", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        data.put("claimsCount", 2);
        data.put("totalAmount", 4500.00);
        data.put("approvedAmount", 3000.00);
        data.put("rejectedAmount", 1500.00);
        data.put("pendingAmount", 0.00);
        
        List<Map<String, Object>> claims = new ArrayList<>();
        claims.add(createSampleClaim("CLM-001", "محمد أحمد", "مستشفى النور", "2026-01-10", 3000.00, "معتمد", "-"));
        claims.add(createSampleClaim("CLM-002", "فاطمة سعيد", "عيادة الأمل", "2026-01-09", 1500.00, "مرفوض", "تجاوز الحد"));
        data.put("claims", claims);
        
        String html = templateService.processTemplate("pdf/claims-report", data);
        
        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_HTML)
            .body(html);
    }
    
    // ========== Helper Methods ==========
    
    private Map<String, Object> createSampleClaim(
            String claimNumber,
            String patientName,
            String providerName,
            String claimDate,
            Double amount,
            String status,
            String action
    ) {
        Map<String, Object> claim = new HashMap<>();
        claim.put("claimNumber", claimNumber);
        claim.put("patientName", patientName);
        claim.put("providerName", providerName);
        claim.put("claimDate", LocalDate.parse(claimDate));
        claim.put("amount", amount);
        claim.put("status", status);
        claim.put("action", action);
        return claim;
    }
}
