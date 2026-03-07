package com.waad.tba.modules.medicaltaxonomy.controller;

import com.waad.tba.modules.medicaltaxonomy.service.MedicalServiceBulkImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * Controller for Medical Services Excel Bulk Import
 * 
 * ARCHITECTURE:
 * - System-generated templates only
 * - Upsert mode (create new or update existing by code)
 * - Optimized for large imports (12,500+ rows)
 * - Category lookup is mandatory
 * 
 * PERMISSIONS:
 * - SUPER_ADMIN: Full access
 * - INSURANCE_ADMIN: Full access
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/medical-services/import")
@RequiredArgsConstructor
@Tag(name = "Medical Services Excel Import", description = "Bulk Excel import optimized for large datasets")
public class MedicalServiceExcelController {

    private final MedicalServiceBulkImportService bulkImportService;

    /**
     * Download Excel template for medical services import
     * 
     * GET /api/medical-services/import/template
     */
    @GetMapping("/template")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
        summary = "Download Medical Services Import Template",
        description = "Downloads a system-generated Excel template for importing medical services. " +
                     "Optimized for large imports (12,500+ rows). " +
                     "Requires SUPER_ADMIN, INSURANCE_ADMIN, ADMIN role or medical_services.create/import permission."
    )
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        log.info("[MedicalServiceImport] Template download requested");
        
        byte[] excelData = bulkImportService.generateTemplate();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "Medical_Services_Import_Template.xlsx");
        headers.setContentLength(excelData.length);
        
        log.info("[MedicalServiceImport] Template generated: {} bytes", excelData.length);
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(excelData);
    }

    /**
     * Import medical services from Excel file
     * 
     * POST /api/medical-services/import
     * 
     * DEPRECATED: Replaced by Safe Import in MedicalServiceController
     */
    /*
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
        summary = "Bulk Import Medical Services from Excel",
        description = "Imports medical services from Excel template. " +
                     "Creates new services or updates existing ones by code. " +
                     "Optimized for 12,500+ rows with batch processing. " +
                     "Requires SUPER_ADMIN, INSURANCE_ADMIN, ADMIN role or medical_services.create/import permission."
    )
    public ResponseEntity<ApiResponse<ExcelImportResultDto>> importMedicalServices(
            @RequestParam("file") MultipartFile file
    ) {
        log.info("[MedicalServiceImport] Bulk import request: {}", file.getOriginalFilename());
        
        ExcelImportResultDto result = bulkImportService.importFromExcel(file);
        
        log.info("[MedicalServiceImport] Import completed: inserted={}, updated={}, failed={}", 
                result.getSummary().getInserted(),
                result.getSummary().getUpdated(),
                result.getSummary().getFailed());
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    */
}
