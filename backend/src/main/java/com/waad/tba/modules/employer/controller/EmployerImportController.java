package com.waad.tba.modules.employer.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.employer.dto.EmployerImportConfirmResultDto;
import com.waad.tba.modules.employer.dto.EmployerImportPreviewResultDto;
import com.waad.tba.modules.employer.service.EmployerImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Bulk Excel import for employers (جهات العمل), each ending up with exactly
 * one ACTIVE default insurance policy — see {@link EmployerImportService}.
 */
@RestController
@RequestMapping("/api/v1/employers/import")
@RequiredArgsConstructor
@Tag(name = "Employer Bulk Import", description = "Template download and two-stage bulk import of employers")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class EmployerImportController {

    private final EmployerImportService importService;

    @GetMapping("/template")
    @Operation(summary = "Download the employer bulk import template")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] content = importService.generateTemplate();
        return fileResponse(content, "قالب_استيراد_جهات_العمل.xlsx");
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Preview & validate an employer import file without persisting anything")
    public ResponseEntity<ApiResponse<EmployerImportPreviewResultDto>> preview(@RequestParam("file") MultipartFile file) {
        EmployerImportPreviewResultDto result = importService.preview(file);
        String message = String.format("تم تحليل %d صفاً: %d صالح، %d بها أخطاء", result.getTotalRows(),
                result.getValidCount(), result.getInvalidCount());
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    @PostMapping("/confirm")
    @Operation(summary = "Apply the valid rows from a previously previewed import session")
    public ResponseEntity<ApiResponse<EmployerImportConfirmResultDto>> confirm(@RequestParam("sessionId") String sessionId) {
        EmployerImportConfirmResultDto result = importService.confirm(sessionId);
        String message = String.format("تمت معالجة %d من %d جهة عمل بنجاح", result.getSuccessCount(), result.getTotalRows());
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    @GetMapping("/errors/{sessionId}")
    @Operation(summary = "Download an Excel report of the rows that failed validation")
    public ResponseEntity<byte[]> downloadErrorReport(@PathVariable String sessionId) throws IOException {
        byte[] content = importService.generateErrorReport(sessionId);
        return fileResponse(content, "تقرير_أخطاء_استيراد_جهات_العمل.xlsx");
    }

    private ResponseEntity<byte[]> fileResponse(byte[] content, String fileName) {
        String encodedFileName = java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .body(content);
    }
}
