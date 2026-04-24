package com.waad.tba.modules.audit.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.audit.entity.AuditLog;
import com.waad.tba.modules.audit.service.MedicalAuditLogExcelExportService;
import com.waad.tba.modules.audit.service.MedicalAuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1/admin/medical-audit-logs")
@RequiredArgsConstructor
@Tag(name = "Medical Audit Logs", description = "Administrative APIs for immutable medical audit logs")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER')")
public class MedicalAuditLogController {

    private final MedicalAuditLogService medicalAuditLogService;
    private final MedicalAuditLogExcelExportService medicalAuditLogExcelExportService;

    @GetMapping
    @Operation(summary = "Search claim audit logs", description = "Filter by claimId and/or correlationId with pagination")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> search(
            @RequestParam(name = "claimId", required = false) Long claimId,
            @RequestParam(name = "correlationId", required = false) String correlationId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "timestamp") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "desc") String sortDir) {

        String safeSortBy = "timestamp";
        if ("id".equals(sortBy) || "timestamp".equals(sortBy)) {
            safeSortBy = sortBy;
        }

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        int safePage = Math.max(0, page - 1);
        int safeSize = Math.min(Math.max(1, size), 100);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(direction, safeSortBy));

        Page<AuditLog> result = medicalAuditLogService.searchClaimAuditLogs(claimId, correlationId, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/export.xlsx")
    @Operation(summary = "Export claim audit logs to XLSX", description = "Export filtered claim audit logs in XLSX format")
    public ResponseEntity<byte[]> exportXlsx(
            @RequestParam(name = "claimId", required = false) Long claimId,
            @RequestParam(name = "correlationId", required = false) String correlationId,
            @RequestParam(name = "maxRows", defaultValue = "5000") int maxRows) throws IOException {

        byte[] file = medicalAuditLogExcelExportService.exportClaimAuditLogs(claimId, correlationId, maxRows);
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "medical_audit_logs_" + ts + ".xlsx";

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(file);
    }

    @GetMapping("/export-by-date.xlsx")
    @Operation(summary = "Export claim audit logs by date to XLSX", description = "Export filtered claim audit logs by date range (UTC) in XLSX format")
    public ResponseEntity<byte[]> exportByDateXlsx(
            @RequestParam(name = "fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(name = "toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(name = "claimId", required = false) Long claimId,
            @RequestParam(name = "correlationId", required = false) String correlationId,
            @RequestParam(name = "maxRows", defaultValue = "5000") int maxRows) throws IOException {

        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must be before or equal to toDate");
        }

        byte[] file = medicalAuditLogExcelExportService.exportClaimAuditLogsByDate(
                claimId,
                correlationId,
                fromDate.atStartOfDay().toInstant(ZoneOffset.UTC),
                toDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC),
                maxRows);

        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = "medical_audit_logs_" + fromDate + "_to_" + toDate + "_" + ts + ".xlsx";

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(file);
    }
}
