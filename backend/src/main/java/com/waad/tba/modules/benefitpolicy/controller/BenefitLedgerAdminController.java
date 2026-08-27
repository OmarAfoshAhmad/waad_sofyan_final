package com.waad.tba.modules.benefitpolicy.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.benefitpolicy.dto.OpeningConsumptionImportExecuteResultDto;
import com.waad.tba.modules.benefitpolicy.dto.OpeningConsumptionImportPreviewDto;
import com.waad.tba.modules.benefitpolicy.service.BenefitBucketLedgerService;
import com.waad.tba.modules.benefitpolicy.service.OpeningConsumptionImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/benefit-ledger")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class BenefitLedgerAdminController {
    private final BenefitBucketLedgerService ledgerService;
    private final OpeningConsumptionImportService openingConsumptionImportService;

    @PostMapping("/claims/{claimId}/reconcile")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reconcileApprovedClaim(
            @PathVariable Long claimId) {
        int createdEntries = ledgerService.reconcileApprovedClaim(claimId);
        return ResponseEntity.ok(ApiResponse.success(
                "Benefit ledger reconciliation completed",
                Map.of("claimId", claimId, "createdEntries", createdEntries)));
    }

    /**
     * A dry run: parses and validates every row against the policy in force
     * ON {@code referenceDate}, and mints the ticket {@code execute} requires.
     * Nothing is written to the ledger here.
     */
    @PostMapping(value = "/opening-consumption/import/preview", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<OpeningConsumptionImportPreviewDto>> previewOpeningConsumptionImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam("referenceDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate) {
        var preview = openingConsumptionImportService.parseAndPreview(file, referenceDate);
        return ResponseEntity.ok(ApiResponse.success("تمت معاينة ملف الاستيراد", preview));
    }

    /**
     * Re-validates the same file against the same preview ticket, then posts
     * one opening-balance batch for it. Aborts entirely (no partial batch) if
     * anything invalidated since the preview.
     */
    @PostMapping(value = "/opening-consumption/import/execute", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<OpeningConsumptionImportExecuteResultDto>> executeOpeningConsumptionImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam("referenceDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate,
            @RequestParam("previewToken") String previewToken,
            @RequestParam("batchReference") String batchReference,
            @RequestParam("reason") String reason,
            @RequestParam("sourceReference") String sourceReference) {
        var result = openingConsumptionImportService.executeConfirmedImport(
                file, referenceDate, previewToken, batchReference, reason, sourceReference);
        return ResponseEntity.ok(ApiResponse.success("تم تنفيذ استيراد الرصيد الافتتاحي", result));
    }
}
