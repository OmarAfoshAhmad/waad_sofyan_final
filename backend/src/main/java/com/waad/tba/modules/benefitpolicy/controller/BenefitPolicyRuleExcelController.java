package com.waad.tba.modules.benefitpolicy.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.common.excel.dto.ExcelImportResult;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyRuleExcelService;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

/**
 * REST Controller for Benefit Policy Rules Excel import/export.
 *
 * Endpoints:
 * GET /api/v1/benefit-policies/{policyId}/rules/import/template → download
 * Excel template
 * POST /api/v1/benefit-policies/{policyId}/rules/import → upload filled
 * template
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/benefit-policies/{policyId}/rules")
@RequiredArgsConstructor
@Tag(name = "Benefit Policy Rules Excel", description = "Import/export coverage rules via Excel")
@PreAuthorize("isAuthenticated()")
public class BenefitPolicyRuleExcelController {

    private final BenefitPolicyRuleExcelService excelService;
    private final BenefitPolicyService policyService;

    /**
     * Download Excel template pre-filled with all unified medical categories
     * and any existing rules for this policy.
     *
     * GET /api/v1/benefit-policies/{policyId}/rules/import/template
     */
    @GetMapping("/import/template")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "تحميل قالب استيراد قواعد التغطية", description = "يولّد ملف Excel يحتوي على جميع التصنيفات الطبية الموحّدة "
            +
            "مع القواعد الحالية للوثيقة (إن وُجدت) جاهزاً للتعديل والاستيراد.")
    public ResponseEntity<byte[]> downloadTemplate(
            @PathVariable Long policyId) throws IOException {

        log.info("[BPRuleExcel] Template download requested: policyId={}", policyId);
        byte[] data = excelService.generateTemplate(policyId);

        String filename = "قواعد_التغطية_وثيقة_" + policyId + "_" + LocalDate.now() + ".xlsx";
        String encodedFilename = java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encodedFilename);
        headers.setContentLength(data.length);

        log.info("[BPRuleExcel] Template ready: policyId={}, size={} bytes", policyId, data.length);
        return ResponseEntity.ok().headers(headers).body(data);
    }

    /**
     * Import coverage rules from a filled Excel template (upsert).
     *
     * POST /api/v1/benefit-policies/{policyId}/rules/import
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "استيراد قواعد التغطية من Excel", description = "يستورد قواعد التغطية من ملف Excel معبأ بالقالب الرسمي. "
            +
            "القواعد الموجودة تُحدَّث، والجديدة تُضاف (upsert).")
    public ResponseEntity<ApiResponse<ExcelImportResult>> importRules(
            @PathVariable Long policyId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "clearOld", defaultValue = "false") boolean clearOld) {

        policyService.assertDraftConfiguration(policyId);

        log.info("[BPRuleExcel] Import requested: policyId={}, file={}, size={}, clearOld={}",
                policyId, file.getOriginalFilename(), file.getSize(), clearOld);

        if (file.isEmpty()) {
            ExcelImportResult emptyResult = ExcelImportResult.builder()
                    .success(false)
                    .messageAr("الملف فارغ، يرجى رفع ملف Excel صحيح")
                    .build();
            return ResponseEntity.badRequest()
                    .body(ApiResponse.success("خطأ في الاستيراد", emptyResult));
        }

        String originalName = file.getOriginalFilename();
        if (originalName != null && !originalName.endsWith(".xlsx")) {
            ExcelImportResult fmtResult = ExcelImportResult.builder()
                    .success(false)
                    .messageAr("صيغة الملف غير مدعومة — يجب أن يكون الملف بصيغة .xlsx")
                    .build();
            return ResponseEntity.badRequest()
                    .body(ApiResponse.success("خطأ في الاستيراد", fmtResult));
        }

        ExcelImportResult result = excelService.importRules(policyId, file, clearOld);

        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(result.getMessageAr(), result));
        } else {
            // Partial success or complete failure — return 200 with error details in body
            return ResponseEntity.ok(ApiResponse.success(result.getMessageAr(), result));
        }
    }
}
