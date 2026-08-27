package com.waad.tba.modules.provider.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.common.excel.dto.ExcelImportResult;
import com.waad.tba.modules.provider.service.ProviderExcelTemplateService;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/providers/import")
@RequiredArgsConstructor
@Tag(name = "Provider Excel Import", description = "System-generated Excel template download and import")
@PreAuthorize("@permissionGuard.has('PROVIDER_MANAGE')")
public class ProviderExcelTemplateController {
    
    private final ProviderExcelTemplateService templateService;
    
    @GetMapping("/template")
    @PreAuthorize("@permissionGuard.has('PROVIDER_MANAGE')")
    @Operation(
        summary = "Download Providers Import Template",
        description = "Downloads a system-generated Excel template for importing medical providers"
    )
    public ResponseEntity<byte[]> downloadTemplate() throws IOException {
        log.info("[ProviderImport] Template download requested");
        
        byte[] excelData = templateService.generateTemplate();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "Providers_Import_Template.xlsx");
        headers.setContentLength(excelData.length);
        
        return ResponseEntity.ok().headers(headers).body(excelData);
    }
    
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionGuard.has('PROVIDER_MANAGE')")
    @Operation(
        summary = "Import Providers from Excel",
        description = "Imports medical providers from system-generated template. License numbers auto-generated."
    )
    public ResponseEntity<ApiResponse<ExcelImportResult>> importProviders(
            @RequestParam("file") MultipartFile file
    ) {
        log.info("[ProviderImport] Import requested: {}", file.getOriginalFilename());
        
        ExcelImportResult result = templateService.importFromExcel(file);
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.success(result.getMessageEn(), result));
        } else {
            return ResponseEntity.badRequest()
                .body(ApiResponse.<ExcelImportResult>builder()
                    .status("error")
                    .message(result.getMessageEn())
                    .data(result)
                    .timestamp(java.time.LocalDateTime.now())
                    .build());
        }
    }
}
