package com.waad.tba.modules.benefitpolicy.controller;

import static com.waad.tba.modules.benefitpolicy.dto.BenefitOpeningBalanceImportDto.*;

import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.benefitpolicy.service.BenefitOpeningBalanceImportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/benefit-opening-balances")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY')")
public class BenefitOpeningBalanceImportController {
    private final BenefitOpeningBalanceImportService service;

    @GetMapping("/template")
    public ResponseEntity<byte[]> template(@RequestParam("policyId") Long policyId) throws Exception {
        byte[] content = service.generateTemplate(policyId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Benefit_Opening_Balances.xlsx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(content.length)
                .body(content);
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Preview>> preview(@RequestParam("file") MultipartFile file,
                                                        @RequestParam("policyId") Long policyId,
                                                        @RequestParam(value = "batchId", required = false) String batchId)
            throws Exception {
        return ResponseEntity.ok(ApiResponse.success("تم التحقق من الأرصدة الافتتاحية",
                service.preview(file, policyId, batchId)));
    }

    @PostMapping(value = "/execute", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Result>> execute(@RequestParam("file") MultipartFile file,
                                                        @RequestParam("policyId") Long policyId,
                                                        @RequestParam("batchId") String batchId)
            throws Exception {
        return ResponseEntity.ok(ApiResponse.success("تم استيراد الأرصدة الافتتاحية",
                service.execute(file, policyId, batchId)));
    }
}
