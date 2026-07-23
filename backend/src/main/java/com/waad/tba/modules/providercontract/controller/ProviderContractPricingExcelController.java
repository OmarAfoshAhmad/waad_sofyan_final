package com.waad.tba.modules.providercontract.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.common.excel.dto.ExcelImportResult;
import com.waad.tba.modules.medicaltaxonomy.dto.ExcelImportResultDto;
import com.waad.tba.modules.providercontract.dto.BulkImportResultDto;
import com.waad.tba.modules.providercontract.dto.PricingImportConfirmRequest;
import com.waad.tba.modules.providercontract.dto.PricingImportPreviewDto;
import com.waad.tba.modules.providercontract.service.BulkPriceListImportService;
import com.waad.tba.modules.providercontract.service.PriceListExcelTemplateService;
import com.waad.tba.modules.providercontract.service.ProviderContractPricingExcelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

/**
 * REST Controller for Price List Excel Template
 * 
 * ARCHITECTURAL FIX (2026-01-06):
 * - Template generation now uses DTO (ContractTemplateContext) instead of JPA Entity
 * - This prevents LazyInitializationException outside transactions
 * - Contract data is extracted within Service layer, then passed as DTO
 * 
 * Pattern:
 * 1. Service loads Entity + eager fetches required relations
 * 2. Service extracts data to DTO
 * 3. Service passes DTO (NOT Entity) to ExcelTemplateService
 * 
 * Endpoints:
 * - GET /api/provider-contracts/{contractId}/pricing/import/template (contract-specific template)
 * - POST /api/provider-contracts/{contractId}/pricing/import (import with contract context)
 * - POST /api/provider-contracts/bulk-import (NEW: multi-provider classified file)
 * 
 * @version 3.1
 * @since 2026-01-06
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/provider-contracts")
@RequiredArgsConstructor
@Tag(name = "Price List Excel Import", description = "System-generated Excel template download and import for contract pricing")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("isAuthenticated()")
public class ProviderContractPricingExcelController {

    private final PriceListExcelTemplateService templateService;
    private final ProviderContractPricingExcelService importService;
    private final BulkPriceListImportService bulkImportService;

    /**
     * Download contract-specific Excel template for pricing import
     * 
     * ARCHITECTURAL FIX: Service now uses DTO instead of Entity to prevent LazyInitializationException
     * 
     * GET /api/provider-contracts/{contractId}/pricing/import/template
     */
    @GetMapping("/{contractId}/pricing/import/template")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(
        summary = "Download Price List Template",
        description = "Downloads a contract-specific Excel template for importing pricing items. " +
                     "Template includes contract code and provider name in header row. " +
                     "Uses DTO internally to prevent LazyInitializationException. " +
                     "Only files downloaded from this endpoint are accepted for import."
    )
    public ResponseEntity<byte[]> downloadTemplate(
            @Parameter(description = "Provider contract ID", required = true)
            @PathVariable("contractId") Long contractId
    ) throws IOException {
        log.info("[PriceListImport] Template download requested for contract ID: {}", contractId);
        
        byte[] excelData = templateService.generateTemplate(contractId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", 
            String.format("Price_List_Contract_%d.xlsx", contractId));
        headers.setContentLength(excelData.length);
        
        log.info("[PriceListImport] Template generated: {} bytes", excelData.length);
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(excelData);
    }

    /**
     * Export existing pricing items to Excel
     * 
     * GET /api/provider-contracts/{contractId}/pricing/export
     */
    @GetMapping("/{contractId}/pricing/export")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(
        summary = "Export Price List to Excel",
        description = "Exports all existing pricing items for a contract into an Excel file."
    )
    public ResponseEntity<byte[]> exportPricing(
            @Parameter(description = "Provider contract ID", required = true)
            @PathVariable("contractId") Long contractId
    ) throws IOException {
        log.info("[PriceListExport] Export requested for contract ID: {}", contractId);
        
        byte[] excelData = templateService.exportPricingToExcel(contractId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", 
            String.format("Price_List_Export_Contract_%d.xlsx", contractId));
        headers.setContentLength(excelData.length);
        
        log.info("[PriceListExport] Export generated: {} bytes", excelData.length);
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(excelData);
    }

    /**
     * Phase 1: Preview Import (Three-Layer Classification)
     * 
     * POST /api/provider-contracts/{contractId}/pricing/import/preview
     */
    @PostMapping(
        value = "/{contractId}/pricing/import/preview",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(
        summary = "Preview Price List Import",
        description = "Analyzes Excel file and returns proposed classification for review."
    )
    public ResponseEntity<ApiResponse<PricingImportPreviewDto>> importPriceListPreview(
            @Parameter(description = "Provider contract ID", required = true)
            @PathVariable("contractId") Long contractId,
            
            @Parameter(description = "Excel template file", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        log.info("[PriceListImport] Preview request for contract: {}", contractId);
        
        PricingImportPreviewDto result = importService.importForPreview(contractId, file);
        
        return ResponseEntity.ok(ApiResponse.success("تم استخراج قائمة الأسعار، يرجى المراجعة", result));
    }

    /**
     * Phase 2: Confirm Import
     * 
     * POST /api/provider-contracts/{contractId}/pricing/import/confirm
     */
    @PostMapping("/{contractId}/pricing/import/confirm")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(
        summary = "Confirm Price List Import",
        description = "Applies user modifications and confirms the import."
    )
    public ResponseEntity<ApiResponse<ExcelImportResultDto>> confirmPriceListImport(
            @Parameter(description = "Provider contract ID", required = true)
            @PathVariable("contractId") Long contractId,
            
            @RequestBody PricingImportConfirmRequest request
    ) {
        log.info("[PriceListImport] Confirm request for contract: {}", contractId);
        
        ExcelImportResultDto result = importService.confirmImport(contractId, request);
        
        return ResponseEntity.ok(ApiResponse.success(result.getMessage(), result));
    }

    /**
     * Legacy Import pricing items from system-generated template
     * 
     * POST /api/provider-contracts/{contractId}/pricing/import
     */
    @PostMapping(
        value = "/{contractId}/pricing/import",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(
        summary = "Import Price List from Template (Legacy)",
        description = "Imports pricing items from a system-generated Excel template directly. " +
                     "Auto-calculates discount percentage."
    )
    public ResponseEntity<ApiResponse<ExcelImportResultDto>> importPriceList(
            @Parameter(description = "Provider contract ID", required = true)
            @PathVariable("contractId") Long contractId,
            
            @Parameter(description = "Excel template file", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        log.info("[PriceListImport] Legacy Import request for contract: {}", contractId);
        
        ExcelImportResultDto result = importService.importFromExcel(contractId, file);
        
        return ResponseEntity.ok(ApiResponse.success(result.getMessage(), result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BULK IMPORT (Multi-Provider Classified File)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Import the full classified price-list file that spans multiple providers.
     *
     * <p>Accepts the output of the classification script
     * (نتيجة_تصنيف_قوائم_الاسعار_بالقاموس_الموحد.xlsx) directly.
     *
     * <p>Behaviour:
     * <ul>
     *   <li>Rows are grouped by the "المرفق" (facility) column.</li>
     *   <li>For each facility: find or auto-create the Provider + its active Contract.</li>
     *   <li>Services are upserted (updated if duplicate code/name, created otherwise).</li>
     *   <li>Returns a per-provider breakdown of created / updated / failed counts.</li>
     * </ul>
     *
     * POST /api/v1/provider-contracts/bulk-import
     */
    @PostMapping(
        value = "/bulk-import",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(
        summary = "Bulk Import — Multi-Provider Classified Price List",
        description = "Accepts the full classified price-list Excel file " +
                      "(نتيجة_تصنيف_قوائم_الاسعار_بالقاموس_الموحد.xlsx). " +
                      "Rows are automatically distributed to the correct providers. " +
                      "Missing providers and contracts are created on the fly. " +
                      "Duplicate services are updated (not duplicated)."
    )
    public ResponseEntity<ApiResponse<BulkImportResultDto>> bulkImportPriceList(
            @Parameter(description = "Classified price-list Excel file", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        log.info("[BulkImport] Request received, file={}, size={} bytes",
                file.getOriginalFilename(), file.getSize());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("الملف فارغ"));
        }

        BulkImportResultDto result = bulkImportService.importFromClassifiedExcel(file);

        boolean hasData = result.getTotalCreated() + result.getTotalUpdated() > 0;
        String msg = hasData ? result.getSummaryAr() : "لم يتم استيراد أي بيانات";

        return ResponseEntity.ok(ApiResponse.success(msg, result));
    }
}

