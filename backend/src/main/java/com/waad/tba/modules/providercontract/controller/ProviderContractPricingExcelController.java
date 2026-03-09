package com.waad.tba.modules.providercontract.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.common.excel.dto.ExcelImportResult;
import com.waad.tba.modules.providercontract.service.PriceListExcelTemplateService;
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
 * 
 * @version 3.0
 * @since 2026-01-06
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/provider-contracts")
@RequiredArgsConstructor
@Tag(name = "Price List Excel Import", description = "System-generated Excel template download and import for contract pricing")
@SecurityRequirement(name = "bearer-jwt")
public class ProviderContractPricingExcelController {

    private final PriceListExcelTemplateService templateService;

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
     * Import pricing items from system-generated template
     * 
     * POST /api/provider-contracts/{contractId}/pricing/import
     */
    @PostMapping(
        value = "/{contractId}/pricing/import",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ACCOUNTANT')")
    @Operation(
        summary = "Import Price List from Template",
        description = "Imports pricing items from a system-generated Excel template. " +
                     "Supports upsert mode (create or update by contract + service). " +
                     "Auto-calculates discount percentage."
    )
    public ResponseEntity<ApiResponse<ExcelImportResult>> importPriceList(
            @Parameter(description = "Provider contract ID", required = true)
            @PathVariable("contractId") Long contractId,
            
            @Parameter(description = "Excel template file", required = true)
            @RequestParam("file") MultipartFile file
    ) {
        log.info("[PriceListImport] Import request for contract: {}", contractId, file.getOriginalFilename());
        
        ExcelImportResult result = templateService.importFromExcel(contractId, file);
        
        log.info("[PriceListImport] Import completed: {}/{} successful", 
                result.getSummary().getCreated() + result.getSummary().getUpdated(),
                result.getSummary().getTotalRows());
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}

