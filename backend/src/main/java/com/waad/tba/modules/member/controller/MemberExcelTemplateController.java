package com.waad.tba.modules.member.controller;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.member.dto.ExcelColumnDetectionDto;
import com.waad.tba.modules.member.dto.MemberImportLogSummaryDto;
import com.waad.tba.modules.member.dto.MemberImportPreviewDto;
import com.waad.tba.modules.member.dto.MemberImportResultDto;
import com.waad.tba.modules.member.dto.MemberImportRollbackPreviewDto;
import com.waad.tba.modules.member.dto.MemberImportRollbackResultDto;
import com.waad.tba.modules.member.entity.MemberImportLog;
import com.waad.tba.modules.member.repository.MemberImportErrorRepository;
import com.waad.tba.modules.member.repository.MemberImportLogRepository;
import com.waad.tba.modules.member.repository.MemberImportBatchRowRepository;
import com.waad.tba.modules.member.security.MemberImportAccessPolicy;
import com.waad.tba.modules.member.service.ExcelColumnMappingService;
import com.waad.tba.modules.member.service.MemberExcelImportService;
import com.waad.tba.modules.member.service.MemberExcelTemplateService;
import com.waad.tba.modules.member.service.MemberImportPreviewTicketService;
import com.waad.tba.modules.member.service.MemberImportRollbackService;

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
@PreAuthorize("@permissionGuard.has('MEMBER_IMPORT')")
public class MemberExcelTemplateController {
    
    private final MemberExcelTemplateService templateService;
    private final MemberExcelImportService importService;
    private final ExcelColumnMappingService columnMappingService;
    private final MemberImportLogRepository importLogRepository;
    private final MemberImportBatchRowRepository importBatchRowRepository;
    private final MemberImportAccessPolicy importAccessPolicy;

    /**
     * How long a batch may sit on PROCESSING before the history screen stops
     * calling it a running job. The import is one synchronous request inside
     * one transaction, so anything past this is a process that died: the row
     * will never be written to again, and the members it was writing rolled
     * back with it.
     */
    @Value("${waad.member-import.stale-after:PT30M}")
    private Duration importStaleAfter;
    private final MemberImportErrorRepository importErrorRepository;
    private final MemberImportPreviewTicketService previewTicketService;
    private final MemberImportRollbackService rollbackService;
    private final ObjectMapper objectMapper;
    
    /**
     * Download Excel template for members import
     * 
     * GET /api/members/import/template
     */
    @GetMapping("/template")
    @PreAuthorize("@permissionGuard.has('MEMBER_IMPORT')")
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
    
    // Import members from Excel: the non-atomic direct POST /import route
    // (MemberExcelTemplateService.importFromExcel) has been removed --
    // it was never called by the frontend (which always uses /preview then
    // /execute, below), had no other caller anywhere in the codebase, and
    // could not be brought up to the same atomicity/audit/idempotency
    // standard as the live pipeline without maintaining two parallel import
    // engines. Use POST .../preview then POST .../execute instead.

    // ==================== COLUMN DETECTION ====================

    /**
     * Detect columns and suggest mapping
     * 
     * POST /api/v1/unified-members/import/detect-columns
     */
    @PostMapping(value = "/detect-columns", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionGuard.has('MEMBER_IMPORT')")
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
    @PreAuthorize("@permissionGuard.has('MEMBER_IMPORT')")
    @Operation(
        summary = "Preview Excel import",
        description = "Upload Excel file and preview data before import"
    )
    public ResponseEntity<ApiResponse<MemberImportPreviewDto>> previewImport(
            @Parameter(description = "Excel file (.xlsx)")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Custom column mappings (optional)")
            @RequestParam(value = "customMappingsJson", required = false) String customMappingsJson,
            @Parameter(description = "Selected Employer ID (optional fallback for empty/invalid employer values)")
            @RequestParam(value = "employerId", required = false) Long employerId,
            @RequestParam(value = "benefitPolicyId", required = false) Long benefitPolicyId,
            @RequestParam(value = "clearOldMembers", required = false, defaultValue = "false") Boolean clearOldMembers,
            @Parameter(description = "Header row number (optional, 0-indexed)")
            @RequestParam(value = "headerRowNumber", required = false) Integer headerRowNumber) {
        
        Map<String, String> customMappings = parseCustomMappings(customMappingsJson);
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
            preview.setBatchId(previewTicketService.issue(file, employerId, benefitPolicyId,
                    preview.getResolvedHeaderRowNumber(),
                    clearOldMembers, customMappings, preview.getResolvedEmployerIds()));
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
    @PreAuthorize("@permissionGuard.has('MEMBER_IMPORT')")
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
            @RequestParam(value = "customMappingsJson", required = false) String customMappingsJson,
            @Parameter(description = "Import policy: CREATE_ONLY, UPDATE_ONLY, CREATE_OR_UPDATE")
            @RequestParam(value = "importPolicy", required = false) String importPolicy,
            @Parameter(description = "Replace the scoped member list: absent memberships are terminated logically; history is preserved")
            @RequestParam(value = "clearOldMembers", required = false, defaultValue = "false") Boolean clearOldMembers) {
        
        Map<String, String> customMappings = parseCustomMappings(customMappingsJson);
        log.info("📥 Execute import: file={}, employer={}, policy={}, batch={}, clearOldMembers={}", 
                file.getOriginalFilename(), employerId, benefitPolicyId, batchId, clearOldMembers);
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("الملف فارغ"));
        }
        
        if (batchId == null || batchId.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("يجب إجراء معاينة صالحة قبل التنفيذ"));
        }
        
        try {
            MemberImportResultDto result = importService.executeConfirmedImport(
                file, batchId, employerId, benefitPolicyId, headerRowNumber, clearOldMembers, customMappings);
            
            String status = result.getStatus();
            if ("COMPLETED".equals(status)) {
                return ResponseEntity.ok(ApiResponse.success(result.getMessage(), result));
            } else if ("PARTIAL".equals(status)) {
                return ResponseEntity.ok(ApiResponse.success(
                        "تم الاستيراد مع بعض الأخطاء: " + result.getMessage(), result));
            } else {
                // The result travels with the failure. The service has already
                // worked out which rows failed and why; returning only a
                // sentence leaves the dialog with four zeroes and no reason,
                // which reads as an empty successful run.
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("فشل الاستيراد: " + result.getMessage(), result));
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
    @PreAuthorize("@permissionGuard.has('MEMBER_IMPORT')")
    @Operation(summary = "Get import status by batch ID")
    public ResponseEntity<ApiResponse<MemberImportLog>> getImportStatus(
              @PathVariable("batchId") String batchId) {
          var importLog = importLogRepository.findByImportBatchId(batchId);
          importLog.ifPresent(this::authorizeHistory);
          return importLog
                .map(foundLog -> ResponseEntity.ok(ApiResponse.success("Import status found", foundLog)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get errors for an import batch
     * 
     * GET /api/v1/unified-members/import/errors/{batchId}
     */
    @GetMapping("/errors/{batchId}")
    @PreAuthorize("@permissionGuard.has('MEMBER_IMPORT')")
    @Operation(summary = "Get errors for import batch")
    public ResponseEntity<ApiResponse<?>> getImportErrors(
              @PathVariable("batchId") String batchId) {
        authorizeHistory(importLogRepository.findByImportBatchId(batchId)
                .orElseThrow(() -> new com.waad.tba.common.exception.BusinessRuleException("سجل الاستيراد غير موجود")));
        var errors = importErrorRepository.findByImportBatchId(batchId);
        return ResponseEntity.ok(ApiResponse.success("Import errors retrieved", errors));
    }

    /**
     * Get import logs with pagination
     * 
     * GET /api/v1/unified-members/import/logs
     */
    @GetMapping("/logs")
    @PreAuthorize("@permissionGuard.has('MEMBER_IMPORT')")
    @Operation(summary = "Get import logs")
    public ResponseEntity<ApiResponse<Page<MemberImportLogSummaryDto>>> getImportLogs(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "from", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(name = "to", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {

        var authorised = importAccessPolicy.requireHistoryScope();
        int pageIndex = Math.max(0, page - 1);

        MemberImportLog.ImportStatus statusFilter = parseStatus(status);
        // One pattern, lowercased once, rather than three concat() calls the
        // database has to build per row.
        String searchFilter = (search == null || search.isBlank()) ? null
                : "%" + search.trim().toLowerCase(java.util.Locale.ROOT) + "%";
        LocalDateTime fromFilter = from == null ? null : from.atStartOfDay();
        // Half-open on the day boundary: "to = 2026-08-29" has to include
        // everything that happened on the 29th, so the bound is the start of
        // the 30th and the comparison is strictly less-than.
        LocalDateTime toFilter = to == null ? null : to.plusDays(1).atStartOfDay();

        Page<MemberImportLog> logs = authorised.isGlobal()
                ? importLogRepository.findFiltered(statusFilter, searchFilter, fromFilter, toFilter,
                        PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                // Unsorted on purpose: the scoped read is a native query that
                // orders itself, and a Sort appended to it reaches Postgres as
                // the JPA property name rather than the column.
                : importLogRepository.findVisibleToEmployers(authorised.employerIds(),
                        statusFilter == null ? null : statusFilter.name(),
                        searchFilter, fromFilter, toFilter, PageRequest.of(pageIndex, size));

        // One clock reading for the whole page, so two rows started a second
        // apart cannot be judged against two different "now"s.
        LocalDateTime now = LocalDateTime.now();
        return ResponseEntity.ok(ApiResponse.success("Import logs retrieved",
                logs.map(row -> MemberImportLogSummaryDto.from(row, importStaleAfter, now))));
    }

    /**
     * An unknown status name is a filter that can never match, not a request
     * for everything: silently widening it would show the operator rows they
     * did not ask for and give no sign why.
     */
    private MemberImportLog.ImportStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return MemberImportLog.ImportStatus.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new com.waad.tba.common.exception.BusinessRuleException("قيمة الحالة غير معروفة");
        }
    }

    @GetMapping("/{batchId}/rollback/preview")
    @PreAuthorize("@permissionGuard.has('MEMBER_IMPORT') and @permissionGuard.has('DANGER_ZONE_EXECUTE')")
    @Operation(summary = "معاينة التراجع الآمن عن دفعة استيراد")
    public ResponseEntity<ApiResponse<MemberImportRollbackPreviewDto>> previewRollback(
            @PathVariable String batchId) {
        Long logId = importLogRepository.findByImportBatchId(batchId)
                .orElseThrow(() -> new com.waad.tba.common.exception.BusinessRuleException(
                        "سجل الاستيراد غير موجود"))
                .getId();
        return ResponseEntity.ok(ApiResponse.success("تم حساب أثر التراجع", rollbackService.preview(logId)));
    }

    @PostMapping("/{batchId}/rollback")
    @PreAuthorize("@permissionGuard.has('MEMBER_IMPORT') and @permissionGuard.has('DANGER_ZONE_EXECUTE')")
    @Operation(summary = "تنفيذ التراجع الآمن عن دفعة استيراد")
    public ResponseEntity<ApiResponse<MemberImportRollbackResultDto>> executeRollback(
            @PathVariable String batchId, @RequestBody RollbackRequest request) {
        Long logId = importLogRepository.findByImportBatchId(batchId)
                .orElseThrow(() -> new com.waad.tba.common.exception.BusinessRuleException(
                        "سجل الاستيراد غير موجود"))
                .getId();
        var result = rollbackService.execute(logId, request.reason());
        return ResponseEntity.ok(ApiResponse.success(result.getMessage(), result));
    }

    public record RollbackRequest(String reason) {}

    private void authorizeHistory(MemberImportLog log) {
        var employerIds = importBatchRowRepository.findByImportLogId(log.getId()).stream()
                .map(row -> {
                    try {
                        return objectMapper.readTree(row.getImportedSnapshot()).path("employerId").longValue();
                    } catch (IOException ex) {
                        throw new com.waad.tba.common.exception.BusinessRuleException(
                                "تعذر التحقق من نطاق دفعة الاستيراد");
                    }
                })
                .filter(id -> id != 0L)
                .collect(java.util.stream.Collectors.toSet());
        importAccessPolicy.requireHistory(employerIds);
    }

    private Map<String, String> parseCustomMappings(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (IOException ex) {
            throw new com.waad.tba.common.exception.BusinessRuleException(
                    "تنسيق مطابقة أعمدة الاستيراد غير صالح");
        }
    }
}

