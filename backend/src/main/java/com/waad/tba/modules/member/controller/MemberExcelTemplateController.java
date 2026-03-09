package com.waad.tba.modules.member.controller;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.common.excel.dto.ExcelImportResult;
import com.waad.tba.modules.member.dto.ExcelColumnDetectionDto;
import com.waad.tba.modules.member.dto.MemberImportPreviewDto;
import com.waad.tba.modules.member.dto.MemberImportResultDto;
import com.waad.tba.modules.member.entity.MemberImportLog;
import com.waad.tba.modules.member.repository.MemberImportErrorRepository;
import com.waad.tba.modules.member.repository.MemberImportLogRepository;
import com.waad.tba.modules.member.service.ExcelColumnMappingService;
import com.waad.tba.modules.member.service.MemberExcelImportService;
import com.waad.tba.modules.member.service.MemberExcelTemplateService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller for Members Excel template download and import
 * 
 * NEW ARCHITECTURE:
 * - System-generated templates only
 * - Create-only imports (Phase 1)
 * - Strict validation with detailed error reporting
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/unified-members/import")
@RequiredArgsConstructor
@Tag(name = "Member Excel Import", description = "System-generated Excel template download and import")
public class MemberExcelTemplateController {
    
    private final MemberExcelTemplateService templateService;
    private final MemberExcelImportService importService;
    private final ExcelColumnMappingService columnMappingService;
    private final MemberImportLogRepository importLogRepository;
    private final MemberImportErrorRepository importErrorRepository;
    
    /**
     * Download Excel template for members import
     * 
     * GET /api/members/import/template
     */
    @GetMapping("/template")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY')")
    @Operation(
        summary = "Download Members Import Template",
        description = "Downloads a system-generated Excel template for importing members. " +
                     "Only files downloaded from this endpoint are accepted for import."
    )
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        log.info("[MemberImport] Template download requested");
        
        byte[] excelData = templateService.generateTemplate();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "Members_Import_Template.xlsx");
        headers.setContentLength(excelData.length);
        
        log.info("[MemberImport] Template generated: {} bytes", excelData.length);
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(excelData);
    }
    
    /**
     * Import members from Excel file
     * 
     * POST /api/members/import
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY')")
    @Operation(
        summary = "Import Members from Excel",
        description = "Imports members from a system-generated Excel template. " +
                     "Only creates new members (no updates in Phase 1). " +
                     "Card numbers are auto-generated. Employer lookup is mandatory."
    )
    public ResponseEntity<ApiResponse<ExcelImportResult>> importMembers(
            @RequestParam("file") MultipartFile file
    ) {
        log.info("[MemberImport] Import requested: {}", file.getOriginalFilename());
        
        ExcelImportResult result = templateService.importFromExcel(file);
        
        log.info("[MemberImport] Import completed - Created: {}, Rejected: {}, Failed: {}",
            result.getSummary().getCreated(),
            result.getSummary().getRejected(),
            result.getSummary().getFailed());
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(result.getMessageEn(), result));
        } else {
            // FIX: Return 200 OK even for validation errors so frontend can display the error report
            return ResponseEntity.ok()
                .body(ApiResponse.<ExcelImportResult>builder()
                    .status("error")
                    .message(result.getMessageEn())
                    .data(result)
                    .timestamp(java.time.LocalDateTime.now())
                    .build());
        }
    }

    // ==================== COLUMN DETECTION ====================

    /**
     * Detect columns and suggest mapping
     * 
     * POST /api/v1/unified-members/import/detect-columns
     */
    @PostMapping(value = "/detect-columns", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY')")
    @Operation(
        summary = "Detect Excel columns and suggest mappings",
        description = "Analyzes Excel file structure and intelligently suggests column-to-field mappings"
    )
    public ResponseEntity<ApiResponse<ExcelColumnDetectionDto>> detectColumns(
            @Parameter(description = "Excel file (.xlsx or .xls)")
            @RequestParam("file") MultipartFile file) {
        
        log.info("🔍 [Column Detection] Request for file: {}", file.getOriginalFilename());
        
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("الملف فارغ"));
            }
            
            String fileName = file.getOriginalFilename();
            if (fileName == null || (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("يجب رفع ملف Excel (.xlsx أو .xls)"));
            }
            
            ExcelColumnDetectionDto detection = columnMappingService.detectColumns(file);
            
            log.info("✅ [Column Detection] Success: {} columns detected", detection.getTotalColumns());
            
            return ResponseEntity.ok(ApiResponse.success("تم تحليل الملف واكتشاف الأعمدة بنجاح", detection));
            
        } catch (Exception e) {
            log.error("❌ [Column Detection] Error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("خطأ في تحليل الملف: " + e.getMessage()));
        }
    }

    // ==================== PREVIEW ====================

    /**
     * Preview Excel import
     * 
     * POST /api/v1/unified-members/import/preview
     */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY')")
    @Operation(
        summary = "Preview Excel import",
        description = "Upload Excel file and preview data before import"
    )
    public ResponseEntity<ApiResponse<MemberImportPreviewDto>> previewImport(
            @Parameter(description = "Excel file (.xlsx)")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Custom column mappings (optional)")
            @RequestParam(value = "customMappings", required = false) Map<String, String> customMappings,
            @Parameter(description = "Selected Employer ID (optional fallback for empty/invalid employer values)")
            @RequestParam(value = "employerId", required = false) Long employerId,
            @Parameter(description = "Header row number (optional, 0-indexed)")
            @RequestParam(value = "headerRowNumber", required = false) Integer headerRowNumber) {
        
        log.info("📊 Preview import request: {} (mappings: {}, headerRow: {})", 
                file.getOriginalFilename(), 
                customMappings != null ? "yes" : "auto",
                headerRowNumber);
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("الملف فارغ"));
        }
        
        String fileName = file.getOriginalFilename();
        if (fileName == null || (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("يجب رفع ملف Excel (.xlsx أو .xls)"));
        }
        
        try {
            MemberImportPreviewDto preview = importService.parseAndPreview(file, customMappings, headerRowNumber, employerId);
            String message = preview.getValidRows() > 0
                    ? "تم تحليل الملف بنجاح"
                    : "تم تحليل الملف: لا توجد صفوف صالحة حاليًا، يمكن اختيار جهة عمل موحدة ثم التنفيذ";
            return ResponseEntity.ok(ApiResponse.success(message, preview));
        } catch (Exception e) {
            log.error("❌ Preview failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("فشل تحليل الملف: " + e.getMessage()));
        }
    }

    // ==================== EXECUTE IMPORT ====================

    /**
     * Execute import after confirmation
     * 
     * POST /api/v1/unified-members/import/execute
     */
    @PostMapping(value = "/execute", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY')")
    @Operation(
        summary = "Execute Excel import",
        description = "Import members from Excel file with selected employer and benefit policy"
    )
    public ResponseEntity<ApiResponse<MemberImportResultDto>> executeImport(
            @Parameter(description = "Excel file (.xlsx)")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Selected Employer ID (optional when employer column exists in file)")
            @RequestParam(value = "employerId", required = false) Long employerId,
            @Parameter(description = "Selected Benefit Policy ID")
            @RequestParam(value = "benefitPolicyId", required = false) Long benefitPolicyId,
            @Parameter(description = "Batch ID from preview")
            @RequestParam(value = "batchId", required = false) String batchId,
            @Parameter(description = "Header row number (0-indexed)")
            @RequestParam(value = "headerRowNumber", required = false) Integer headerRowNumber,
            @Parameter(description = "Import policy: CREATE_ONLY, UPDATE_ONLY, CREATE_OR_UPDATE")
            @RequestParam(value = "importPolicy", required = false) String importPolicy) {
        
        log.info("📥 Execute import: file={}, employer={}, policy={}, batch={}", 
                file.getOriginalFilename(), employerId, benefitPolicyId, batchId);
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("الملف فارغ"));
        }
        
        if (batchId == null || batchId.isBlank()) {
            batchId = UUID.randomUUID().toString();
        }
        
        try {
            MemberImportResultDto result = importService.executeImport(
                file, batchId, employerId, benefitPolicyId, headerRowNumber);
            
            String status = result.getStatus();
            if ("COMPLETED".equals(status)) {
                return ResponseEntity.ok(ApiResponse.success(result.getMessage(), result));
            } else if ("PARTIAL".equals(status)) {
                return ResponseEntity.ok(ApiResponse.success(
                        "تم الاستيراد مع بعض الأخطاء: " + result.getMessage(), result));
            } else {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("فشل الاستيراد: " + result.getMessage()));
            }
            
        } catch (Exception e) {
            log.error("❌ Import failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("فشل الاستيراد: " + e.getMessage()));
        }
    }

    // ==================== IMPORT STATUS & LOGS ====================

    /**
     * Get import status by batch ID
     * 
     * GET /api/v1/unified-members/import/status/{batchId}
     */
    @GetMapping("/status/{batchId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY')")
    @Operation(summary = "Get import status by batch ID")
    public ResponseEntity<ApiResponse<MemberImportLog>> getImportStatus(
            @PathVariable("batchId") String batchId) {
        
        return importLogRepository.findByImportBatchId(batchId)
                .map(log -> ResponseEntity.ok(ApiResponse.success("Import status found", log)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get errors for an import batch
     * 
     * GET /api/v1/unified-members/import/errors/{batchId}
     */
    @GetMapping("/errors/{batchId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY')")
    @Operation(summary = "Get errors for import batch")
    public ResponseEntity<ApiResponse<?>> getImportErrors(
            @PathVariable("batchId") String batchId) {
        
        var errors = importErrorRepository.findByImportBatchId(batchId);
        return ResponseEntity.ok(ApiResponse.success("Import errors retrieved", errors));
    }

    /**
     * Get import logs with pagination
     * 
     * GET /api/v1/unified-members/import/logs
     */
    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY')")
    @Operation(summary = "Get import logs")
    public ResponseEntity<ApiResponse<Page<MemberImportLog>>> getImportLogs(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        
        Page<MemberImportLog> logs = importLogRepository.findAll(
                PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "createdAt")));
        
        return ResponseEntity.ok(ApiResponse.success("Import logs retrieved", logs));
    }
}

