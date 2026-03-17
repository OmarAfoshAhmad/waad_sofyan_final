package com.waad.tba.modules.member.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
import com.waad.tba.modules.member.dto.ExcelColumnDetectionDto;
import com.waad.tba.modules.member.dto.MemberImportPreviewDto;
import com.waad.tba.modules.member.dto.MemberImportResultDto;
import com.waad.tba.modules.member.entity.MemberImportLog;
import com.waad.tba.modules.member.repository.MemberImportErrorRepository;
import com.waad.tba.modules.member.repository.MemberImportLogRepository;
import com.waad.tba.modules.member.service.ExcelColumnMappingService;
import com.waad.tba.modules.member.service.MemberExcelImportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * LEGACY Controller for member bulk import from Excel.
 * 
 * @deprecated Use MemberExcelTemplateController for new template-based import
 *             system
 *             Supports Odoo hr.employee.public exports.
 */
@Deprecated
@SuppressWarnings("dep-ann")
@Slf4j
@RestController
@RequestMapping("/api/v1/members/legacy-import")
@RequiredArgsConstructor
@Tag(name = "Member Import (Legacy)", description = "Bulk member import from Excel - DEPRECATED, use /api/members/import instead")
@PreAuthorize("isAuthenticated()")
public class MemberImportController {

        private final MemberExcelImportService importService;
        private final ExcelColumnMappingService columnMappingService;
        private final MemberImportLogRepository importLogRepository;
        private final MemberImportErrorRepository importErrorRepository;

        /**
         * Detect columns and suggest mapping (NEW: Column Mapping Feature).
         * 
         * POST /api/members/import/detect-columns
         * Content-Type: multipart/form-data
         * 
         * Analyzes Excel file and suggests column-to-field mappings.
         */
        @Deprecated
        @PostMapping(value = "/detect-columns", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY')")
        @Operation()
        public ResponseEntity<ApiResponse<ExcelColumnDetectionDto>> detectColumns(
                        @Parameter(description = "Excel file (.xlsx or .xls)") @RequestParam("file") MultipartFile file) {

                log.info("🔍 [Column Detection] Request for file: {}", file.getOriginalFilename());

                try {
                        // Validate file
                        if (file.isEmpty()) {
                                return ResponseEntity.badRequest()
                                                .body(ApiResponse.error("الملف فارغ"));
                        }

                        String fileName = file.getOriginalFilename();
                        if (fileName == null || (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls"))) {
                                return ResponseEntity.badRequest()
                                                .body(ApiResponse.error("يجب رفع ملف Excel (.xlsx أو .xls)"));
                        }

                        // Detect columns and suggest mappings
                        ExcelColumnDetectionDto detection = columnMappingService.detectColumns(file);

                        log.info("✅ [Column Detection] Success: {} columns detected, {} auto-accepted, {} need review",
                                        detection.getTotalColumns(),
                                        detection.getAutoAcceptedCount(),
                                        detection.getManualReviewCount());

                        return ResponseEntity.ok(ApiResponse.success(
                                        "تم تحليل الملف واكتشاف الأعمدة بنجاح",
                                        detection));

                } catch (IllegalArgumentException e) {
                        log.error("❌ [Column Detection] Validation error: {}", e.getMessage());
                        return ResponseEntity.badRequest()
                                        .body(ApiResponse.error(e.getMessage()));

                } catch (Exception e) {
                        log.error("❌ [Column Detection] Error analyzing file: {}", e.getMessage(), e);
                        return ResponseEntity.internalServerError()
                                        .body(ApiResponse.error("خطأ في تحليل الملف: " + e.getMessage()));
                }
        }

        /**
         * Upload Excel and get preview (without importing).
         * 
         * POST /api/members/import/preview
         * Content-Type: multipart/form-data
         * 
         * Returns parsed data for user confirmation.
         * 
         * @param file           Excel file
         * @param customMappings Optional: User-customized column mappings (Excel column
         *                       → system field)
         */
        @Deprecated
        @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY')")
        @Operation(description = "Upload Excel file and preview data before import. Returns validation errors and mapping.")
        public ResponseEntity<ApiResponse<MemberImportPreviewDto>> previewImport(
                        @Parameter(description = "Excel file (.xlsx)") @RequestParam("file") MultipartFile file,
                        @Parameter(description = "Custom column mappings (optional, JSON format)") @RequestParam(value = "customMappings", required = false) java.util.Map<String, String> customMappings) {

                log.info("📊 Preview import request: {} (custom mappings: {})",
                                file.getOriginalFilename(),
                                customMappings != null ? "yes" : "auto");

                // Validate file
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
                        MemberImportPreviewDto preview = importService.parseAndPreview(file, customMappings);
                        return ResponseEntity.ok(ApiResponse.success("تم تحليل الملف بنجاح", preview));
                } catch (Exception e) {
                        log.error("❌ Preview failed: {}", e.getMessage(), e);
                        return ResponseEntity.badRequest()
                                        .body(ApiResponse.error("فشل تحليل الملف: " + e.getMessage()));
                }
        }

        /**
         * Execute import after confirmation.
         * 
         * POST /api/members/import/execute
         * Content-Type: multipart/form-data
         * 
         * Creates/updates members and returns result.
         * 
         * REQUIRED: employerId (selected from preview)
         * OPTIONAL: benefitPolicyId (selected from preview)
         */
        @Deprecated
        @PostMapping(value = "/execute", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY')")
        @Operation(description = "Import members from Excel file with selected employer and benefit policy")
        public ResponseEntity<ApiResponse<MemberImportResultDto>> executeImport(
                        @Parameter(description = "Excel file (.xlsx)") @RequestParam("file") MultipartFile file,

                        @Parameter(description = "Selected Employer ID (REQUIRED)", required = true) @RequestParam("employerId") Long employerId,

                        @Parameter(description = "Selected Benefit Policy ID (OPTIONAL)") @RequestParam(value = "benefitPolicyId", required = false) Long benefitPolicyId,

                        @Parameter(description = "Batch ID from preview (optional)") @RequestParam(value = "batchId", required = false) String batchId) {

                log.info("📥 Execute import request: file={}, employer={}, policy={}, batch={}",
                                file.getOriginalFilename(), employerId, benefitPolicyId, batchId);

                // Validate file
                if (file.isEmpty()) {
                        return ResponseEntity.badRequest()
                                        .body(ApiResponse.error("الملف فارغ"));
                }

                // Validate employer is provided
                if (employerId == null) {
                        return ResponseEntity.badRequest()
                                        .body(ApiResponse.error("يجب تحديد صاحب العمل"));
                }

                // Generate batch ID if not provided
                if (batchId == null || batchId.isBlank()) {
                        batchId = UUID.randomUUID().toString();
                }

                try {
                        MemberImportResultDto result = importService.executeImport(
                                        file, batchId, employerId, benefitPolicyId);

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

        /**
         * Get import history (logs).
         * 
         * GET /api/members/import/logs
         */
        @Deprecated
        @GetMapping("/logs")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY')")
        @Operation(summary = "Get import history", description = "List all import logs with pagination")
        public ResponseEntity<ApiResponse<Page<MemberImportLog>>> getImportLogs(
                        @RequestParam(name = "page", defaultValue = "1") int page,
                        @RequestParam(name = "size", defaultValue = "20") int size) {

                Page<MemberImportLog> logs = importLogRepository.findAll(
                                PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "createdAt")));

                return ResponseEntity.ok(ApiResponse.success("Import logs retrieved", logs));
        }

        /**
         * Get import log by batch ID.
         * 
         * GET /api/members/import/logs/{batchId}
         */
        @Deprecated
        @GetMapping("/logs/{batchId}")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY')")
        @Operation(summary = "Get import log by batch ID")
        public ResponseEntity<ApiResponse<MemberImportLog>> getImportLog(
                        @PathVariable("batchId") String batchId) {

                return importLogRepository.findByImportBatchId(batchId)
                                .map(log -> ResponseEntity.ok(ApiResponse.success("Import log found", log)))
                                .orElse(ResponseEntity.notFound().build());
        }

        /**
         * Get errors for an import batch.
         * 
         * GET /api/members/import/logs/{batchId}/errors
         */
        @Deprecated
        @GetMapping("/logs/{batchId}/errors")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY')")
        @Operation(summary = "Get errors for import batch")
        public ResponseEntity<ApiResponse<?>> getImportErrors(
                        @PathVariable("batchId") String batchId) {

                var errors = importErrorRepository.findByImportBatchId(batchId);
                return ResponseEntity.ok(ApiResponse.success("Import errors retrieved", errors));
        }

        /**
         * Download import template.
         * 
         * GET /api/members/import/template
         */
        @Deprecated
        @GetMapping("/template")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY')")
        @Operation(summary = "Get import template info", description = "Returns expected column mappings")
        public ResponseEntity<ApiResponse<?>> getTemplate() {
                var template = java.util.Map.of(
                                "mandatory_columns", java.util.List.of(
                                                java.util.Map.of("name", "national_id", "aliases",
                                                                "identification_id, civil_id, الرقم الوطني"),
                                                java.util.Map.of("name", "full_name", "aliases",
                                                                "name, full_name_arabic, الاسم الكامل"),
                                                java.util.Map.of("name", "employer", "aliases",
                                                                "company, company_id, جهة العمل"),
                                                java.util.Map.of("name", "policy", "aliases",
                                                                "policy_number, policy_id, الوثيقة")),
                                "optional_columns", java.util.List.of(
                                                "full_name_english", "birth_date", "gender", "phone", "email",
                                                "nationality", "employee_number"),
                                "attribute_columns", java.util.List.of(
                                                java.util.Map.of("name", "job_title", "description", "الوظيفة"),
                                                java.util.Map.of("name", "department", "description", "القسم/الإدارة"),
                                                java.util.Map.of("name", "work_location", "description", "موقع العمل"),
                                                java.util.Map.of("name", "grade", "description", "الدرجة/المستوى"),
                                                java.util.Map.of("name", "manager", "description", "المدير"),
                                                java.util.Map.of("name", "cost_center", "description", "مركز التكلفة")),
                                "notes", java.util.List.of(
                                                "Any extra columns will be stored as member attributes",
                                                "Duplicate national_id → updates existing member",
                                                "Compatible with Odoo hr.employee.public exports"));

                return ResponseEntity.ok(ApiResponse.success("Import template info", template));
        }
}
