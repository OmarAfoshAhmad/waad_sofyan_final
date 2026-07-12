package com.waad.tba.modules.medicaltaxonomy.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.common.excel.dto.ExcelImportResult;
import com.waad.tba.modules.medicaltaxonomy.service.MedicalCategoryExcelTemplateService;
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

/**
 * Controller for Medical Categories Excel Template
 * 
 * NEW ARCHITECTURE:
 * - System-generated templates only
 * - Upsert mode (create new or update existing by code)
 * - Strict validation
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/medical-categories/import")
@RequiredArgsConstructor
@Tag(name = "Medical Categories Excel Import", description = "System-generated Excel template download and import")
@PreAuthorize("isAuthenticated()")
public class MedicalCategoryExcelController {

    private final MedicalCategoryExcelTemplateService templateService;

    /**
     * Download Excel template for medical categories import
     * 
     * GET /api/medical-categories/import/template
     */
    @GetMapping("/template")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
        summary = "Download Medical Categories Import Template",
        description = "Downloads a system-generated Excel template for importing medical categories. " +
                     "Only files downloaded from this endpoint are accepted for import. " +
                     "Requires ADMIN or SUPER_ADMIN authority."
    )
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        log.info("[MedicalCategoryImport] Template download requested");
        
        byte[] excelData = templateService.generateTemplate();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "Medical_Categories_Import_Template.xlsx");
        headers.setContentLength(excelData.length);
        
        log.info("[MedicalCategoryImport] Template generated: {} bytes", excelData.length);
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(excelData);
    }

    /**
     * Import medical categories from Excel file
     * 
     * POST /api/medical-categories/import
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
        summary = "Import Medical Categories from Excel",
        description = "Imports medical categories from a system-generated Excel template. " +
                     "Creates new categories or updates existing ones by category code. " +
                     "Requires ADMIN or SUPER_ADMIN authority."
    )
    public ResponseEntity<ApiResponse<ExcelImportResult>> importMedicalCategories(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "clearOld", defaultValue = "false") boolean clearOld
    ) {
        log.info("[MedicalCategoryImport] Import request received: {}, clearOld={}", file.getOriginalFilename(), clearOld);
        
        ExcelImportResult result = templateService.importFromExcel(file, clearOld);
        
        log.info("[MedicalCategoryImport] Import completed: {}/{} successful", 
                result.getSummary().getCreated() + result.getSummary().getUpdated(),
                result.getSummary().getTotalRows());
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
