package com.waad.tba.modules.provider.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.provider.dto.ProviderClaimReportDto;
import com.waad.tba.modules.provider.dto.ProviderPreAuthReportDto;
import com.waad.tba.modules.provider.dto.ProviderVisitReportDto;
import com.waad.tba.modules.provider.service.ProviderReportExcelService;
import com.waad.tba.modules.provider.service.ProviderReportsService;
import com.waad.tba.security.ProviderContextGuard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Provider Reports Controller
 * 
 * Provides reporting endpoints for provider portal:
 * - Claims report (submitted claims history)
 * - Pre-authorizations report (pre-auth requests)
 * - Visits report (visit history)
 * 
 * All reports are scoped to the authenticated provider only.
 * 
 * @since Phase 5.5
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/provider/reports")
@RequiredArgsConstructor
@Tag(name = "Provider Reports", description = "Provider Portal Reports — Claims, Pre-Auth, Visits")
public class ProviderReportsController {

    /** Maximum records returned per export — prevents OOM on large datasets */
    private static final int MAX_EXPORT_SIZE = 500;

    private final ProviderReportsService reportsService;
    private final ProviderReportExcelService providerReportExcelService;
    private final ProviderContextGuard providerContextGuard;

    private static final DateTimeFormatter FILE_TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Get claims report for current provider
     * 
     * Shows all claims submitted by this provider with filtering options.
     */
    @GetMapping("/claims")
    @PreAuthorize("hasRole('PROVIDER_STAFF')")
    @Operation(summary = "Get claims report", description = "Retrieve claims submitted by current provider")
    public ResponseEntity<ApiResponse<Page<ProviderClaimReportDto>>> getClaimsReport(
            @Parameter(description = "From date (YYYY-MM-DD)") @RequestParam(name = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

            @Parameter(description = "To date (YYYY-MM-DD)") @RequestParam(name = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

            @Parameter(description = "Claim status filter") @RequestParam(name = "status", required = false) String status,

            @Parameter(description = "Member barcode filter") @RequestParam(name = "memberBarcode", required = false) String memberBarcode,

            @Parameter(description = "Page number (0-based)") @RequestParam(name = "page", defaultValue = "0") int page,

            @Parameter(description = "Page size") @RequestParam(name = "size", defaultValue = "20") int size,

            @Parameter(description = "Sort field") @RequestParam(name = "sortBy", defaultValue = "claimDate") String sortBy,

            @Parameter(description = "Sort direction") @RequestParam(name = "sortDir", defaultValue = "DESC") String sortDir) {

        Long providerId = providerContextGuard.getRequiredProviderIdStrict();

        log.info("📊 [PROVIDER-REPORTS] Claims report requested: provider={}, fromDate={}, toDate={}, status={}",
                providerId, fromDate, toDate, status);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), sort);

        ClaimStatus claimStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                claimStatus = ClaimStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                log.warn("Ignoring invalid claim status filter '{}' for provider {}", status, providerId);
            }
        }

        Page<ProviderClaimReportDto> report = reportsService.getClaimsReport(
                providerId, fromDate, toDate, claimStatus, memberBarcode, pageRequest);

        return ResponseEntity.ok(ApiResponse.success(report));
    }

    /**
     * Get pre-authorizations report for current provider
     */
    @GetMapping("/pre-auth")
    @PreAuthorize("hasRole('PROVIDER_STAFF')")
    @Operation(summary = "Get pre-auth report", description = "Retrieve pre-auth requests by current provider")
    public ResponseEntity<ApiResponse<Page<ProviderPreAuthReportDto>>> getPreAuthReport(
            @Parameter(description = "From date (YYYY-MM-DD)") @RequestParam(name = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

            @Parameter(description = "To date (YYYY-MM-DD)") @RequestParam(name = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

            @Parameter(description = "Pre-auth status filter") @RequestParam(name = "status", required = false) String status,

            @Parameter(description = "Member barcode filter") @RequestParam(name = "memberBarcode", required = false) String memberBarcode,

            @Parameter(description = "Page number (0-based)") @RequestParam(name = "page", defaultValue = "0") int page,

            @Parameter(description = "Page size") @RequestParam(name = "size", defaultValue = "20") int size,

            @Parameter(description = "Sort field") @RequestParam(name = "sortBy", defaultValue = "requestDate") String sortBy,

            @Parameter(description = "Sort direction") @RequestParam(name = "sortDir", defaultValue = "DESC") String sortDir) {

        Long providerId = providerContextGuard.getRequiredProviderIdStrict();

        log.info("📊 [PROVIDER-REPORTS] Pre-auth report requested: provider={}, fromDate={}, toDate={}",
                providerId, fromDate, toDate);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), sort);

        Page<ProviderPreAuthReportDto> report = reportsService.getPreAuthReport(
                providerId, fromDate, toDate, status, memberBarcode, pageRequest);

        return ResponseEntity.ok(ApiResponse.success(report));
    }

    /**
     * Get visits report for current provider
     */
    @GetMapping("/visits")
    @PreAuthorize("hasRole('PROVIDER_STAFF')")
    @Operation(summary = "Get visits report", description = "Retrieve visit history for current provider")
    public ResponseEntity<ApiResponse<Page<ProviderVisitReportDto>>> getVisitsReport(
            @Parameter(description = "From date (YYYY-MM-DD)") @RequestParam(name = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,

            @Parameter(description = "To date (YYYY-MM-DD)") @RequestParam(name = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,

            @Parameter(description = "Visit status filter") @RequestParam(name = "status", required = false) String status,

            @Parameter(description = "Member barcode filter") @RequestParam(name = "memberBarcode", required = false) String memberBarcode,

            @Parameter(description = "Page number (0-based)") @RequestParam(name = "page", defaultValue = "0") int page,

            @Parameter(description = "Page size") @RequestParam(name = "size", defaultValue = "20") int size,

            @Parameter(description = "Sort field") @RequestParam(name = "sortBy", defaultValue = "visitDate") String sortBy,

            @Parameter(description = "Sort direction") @RequestParam(name = "sortDir", defaultValue = "DESC") String sortDir) {

        Long providerId = providerContextGuard.getRequiredProviderIdStrict();

        log.info("📊 [PROVIDER-REPORTS] Visits report requested: provider={}, fromDate={}, toDate={}",
                providerId, fromDate, toDate);

        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, 100), sort);

        Page<ProviderVisitReportDto> report = reportsService.getVisitsReport(
                providerId, fromDate, toDate, status, memberBarcode, pageRequest);

        return ResponseEntity.ok(ApiResponse.success(report));
    }

    @GetMapping("/claims/export")
    @PreAuthorize("hasRole('PROVIDER_STAFF')")
    @Operation(summary = "Export claims report", description = "Export filtered provider claims report to Excel")
    public ResponseEntity<byte[]> exportClaimsReport(
            @RequestParam(name = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(name = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "memberBarcode", required = false) String memberBarcode) {

        Long providerId = providerContextGuard.getRequiredProviderIdStrict();

        ClaimStatus claimStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                claimStatus = ClaimStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                log.warn("Ignoring invalid claim status filter '{}' for provider {}", status, providerId);
            }
        }

        List<ProviderClaimReportDto> reportRows = reportsService.getClaimsReport(
                providerId,
                fromDate,
                toDate,
                claimStatus,
                memberBarcode,
                PageRequest.of(0, MAX_EXPORT_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();

        byte[] excel = providerReportExcelService.exportClaimsReport(reportRows);
        String fileName = "provider_claims_report_" + LocalDateTime.now().format(FILE_TS_FORMATTER) + ".xlsx";

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(excel);
    }

    @GetMapping("/pre-auth/export")
    @PreAuthorize("hasRole('PROVIDER_STAFF')")
    @Operation(summary = "Export pre-auth report", description = "Export filtered provider pre-authorization report to Excel")
    public ResponseEntity<byte[]> exportPreAuthReport(
            @RequestParam(name = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(name = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "memberBarcode", required = false) String memberBarcode) {

        Long providerId = providerContextGuard.getRequiredProviderIdStrict();

        List<ProviderPreAuthReportDto> reportRows = reportsService.getPreAuthReport(
                providerId,
                fromDate,
                toDate,
                status,
                memberBarcode,
                PageRequest.of(0, MAX_EXPORT_SIZE, Sort.by(Sort.Direction.DESC, "requestDate"))).getContent();

        byte[] excel = providerReportExcelService.exportPreAuthReport(reportRows);
        String fileName = "provider_preauth_report_" + LocalDateTime.now().format(FILE_TS_FORMATTER) + ".xlsx";

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(excel);
    }

    @GetMapping("/visits/export")
    @PreAuthorize("hasRole('PROVIDER_STAFF')")
    @Operation(summary = "Export visits report", description = "Export filtered provider visits report to Excel")
    public ResponseEntity<byte[]> exportVisitsReport(
            @RequestParam(name = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(name = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "memberBarcode", required = false) String memberBarcode) {

        Long providerId = providerContextGuard.getRequiredProviderIdStrict();

        List<ProviderVisitReportDto> reportRows = reportsService.getVisitsReport(
                providerId,
                fromDate,
                toDate,
                status,
                memberBarcode,
                PageRequest.of(0, MAX_EXPORT_SIZE, Sort.by(Sort.Direction.DESC, "visitDate"))).getContent();

        byte[] excel = providerReportExcelService.exportVisitsReport(reportRows);
        String fileName = "provider_visits_report_" + LocalDateTime.now().format(FILE_TS_FORMATTER) + ".xlsx";

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(excel);
    }
}
