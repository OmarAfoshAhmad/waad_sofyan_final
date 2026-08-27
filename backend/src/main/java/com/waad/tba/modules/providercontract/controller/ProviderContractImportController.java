package com.waad.tba.modules.providercontract.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.providercontract.dto.ContractImportConfirmResultDto;
import com.waad.tba.modules.providercontract.dto.ContractImportPreviewResultDto;
import com.waad.tba.modules.providercontract.service.ProviderContractImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
import java.nio.charset.StandardCharsets;

/**
 * Bulk Excel import for new provider contracts (as opposed to
 * {@link ProviderContractPricingExcelController}, which imports pricing items
 * into an already-existing contract).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/provider-contracts/import")
@RequiredArgsConstructor
@Tag(name = "Provider Contract Bulk Import", description = "Template download and two-stage bulk import of provider contracts")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("@permissionGuard.has('CONTRACT_MANAGE')")
public class ProviderContractImportController {

    private final ProviderContractImportService importService;

    @GetMapping("/template")
    @PreAuthorize("@permissionGuard.has('CONTRACT_MANAGE')")
    @Operation(summary = "Download provider-contract bulk import template")
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        byte[] content = importService.generateTemplate();
        String fileName = "قالب_استيراد_عقود_مقدمي_الخدمة.xlsx";
        String encodedFileName = java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .body(content);
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionGuard.has('CONTRACT_MANAGE')")
    @Operation(summary = "Preview & validate a contract import file without persisting anything")
    public ResponseEntity<ApiResponse<ContractImportPreviewResultDto>> preview(@RequestParam("file") MultipartFile file) {
        ContractImportPreviewResultDto result = importService.preview(file);
        String message = String.format("تم تحليل %d صفاً: %d صالح، %d بها أخطاء", result.getTotalRows(),
                result.getValidCount(), result.getInvalidCount());
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    @PostMapping("/confirm")
    @PreAuthorize("@permissionGuard.has('CONTRACT_MANAGE')")
    @Operation(summary = "Persist the valid rows from a previously previewed import session")
    public ResponseEntity<ApiResponse<ContractImportConfirmResultDto>> confirm(@RequestParam("sessionId") String sessionId) {
        ContractImportConfirmResultDto result = importService.confirm(sessionId);
        String message = String.format("تم إنشاء %d من %d عقداً بنجاح", result.getSuccessCount(), result.getTotalRows());
        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    @GetMapping("/errors/{sessionId}")
    @PreAuthorize("@permissionGuard.has('CONTRACT_MANAGE')")
    @Operation(summary = "Download an Excel report of the rows that failed validation")
    public ResponseEntity<byte[]> downloadErrorReport(@PathVariable String sessionId) throws IOException {
        byte[] content = importService.generateErrorReport(sessionId);
        String fileName = "تقرير_أخطاء_استيراد_العقود.xlsx";
        String encodedFileName = java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .body(content);
    }
}
