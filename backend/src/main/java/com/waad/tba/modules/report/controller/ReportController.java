package com.waad.tba.modules.report.controller;

import com.waad.tba.modules.report.dto.ClaimReportDto;
import com.waad.tba.modules.report.service.PdfExportService;
import com.waad.tba.modules.report.service.ReportDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;

@Controller
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportDataService reportDataService;
    private final PdfExportService pdfExportService;
    private final TemplateEngine templateEngine;

    /** معاينة HTML في iframe */
    @GetMapping("/claims/html")
    public String getClaimReportHtml(
            @RequestParam List<Long> claimIds,
            @RequestParam(required = false, defaultValue = "false") Boolean onlyRejected,
            @RequestParam(required = false) String batchCode,
            Model model) {
        ClaimReportDto reportData = reportDataService.getClaimReportData(claimIds, onlyRejected, batchCode);
        model.addAttribute("report", reportData);
        return "reports/claim-report";
    }

    /** تنزيل PDF - الآلية الموحدة (HTML → PDF) */
    @GetMapping("/claims/pdf")
    @ResponseBody
    public ResponseEntity<byte[]> getClaimReportPdf(
            @RequestParam List<Long> claimIds,
            @RequestParam(required = false, defaultValue = "false") Boolean onlyRejected) {
        try {
            ClaimReportDto reportData = reportDataService.getClaimReportData(claimIds, onlyRejected);

            Context context = new Context();
            context.setVariable("report", reportData);
            String htmlString = templateEngine.process("reports/claim-report", context);

            byte[] pdfBytes = pdfExportService.generatePdfFromHtml(htmlString);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("filename",
                    "claim_statement_" + (reportData.getBatchCode() != null ? reportData.getBatchCode() : "report")
                            + ".pdf");

            return ResponseEntity.ok().headers(headers).body(pdfBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
