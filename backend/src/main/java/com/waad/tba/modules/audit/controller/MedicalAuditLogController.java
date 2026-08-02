package com.waad.tba.modules.audit.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.audit.dto.AuditLogViewDto;
import com.waad.tba.modules.audit.entity.AuditLog;
import com.waad.tba.modules.audit.enums.AuditAction;
import com.waad.tba.modules.audit.enums.AuditSource;
import com.waad.tba.modules.audit.enums.EntityType;
import com.waad.tba.modules.audit.service.AuditLogEnrichmentService;
import com.waad.tba.modules.audit.service.MedicalAuditLogExcelExportService;
import com.waad.tba.modules.audit.service.MedicalAuditLogService;
import com.waad.tba.modules.claim.repository.ClaimRepository;
import com.waad.tba.modules.member.entity.Member;
import com.waad.tba.modules.member.repository.MemberRepository;
import com.waad.tba.modules.preauthorization.repository.PreAuthorizationRepository;
import com.waad.tba.modules.visit.repository.VisitRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/medical-audit-logs")
@RequiredArgsConstructor
@Tag(name = "Medical Audit Logs", description = "Administrative APIs for immutable medical audit logs")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER')")
public class MedicalAuditLogController {

        private final MedicalAuditLogService medicalAuditLogService;
        private final MedicalAuditLogExcelExportService medicalAuditLogExcelExportService;
        private final AuditLogEnrichmentService auditLogEnrichmentService;
        private final ClaimRepository claimRepository;
        private final VisitRepository visitRepository;
        private final PreAuthorizationRepository preAuthorizationRepository;
        private final MemberRepository memberRepository;

        @GetMapping
        @Operation(summary = "Search claim audit logs", description = "Filter by claimId, providerId, employerId and/or correlationId with pagination")
        public ResponseEntity<ApiResponse<Page<AuditLogViewDto>>> search(
                        @RequestParam(name = "claimId", required = false) Long claimId,
                        @RequestParam(name = "entityType", required = false) EntityType entityType,
                        @RequestParam(name = "entityId", required = false) String entityId,
                        @RequestParam(name = "providerId", required = false) Long providerId,
                        @RequestParam(name = "employerId", required = false) Long employerId,
                        @RequestParam(name = "action", required = false) AuditAction action,
                        @RequestParam(name = "source", required = false) AuditSource source,
                        @RequestParam(name = "correlationId", required = false) String correlationId,
                        @RequestParam(name = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                        @RequestParam(name = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
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

                EntityType effectiveEntityType = claimId != null ? EntityType.CLAIM : entityType;
                String effectiveEntityId = claimId != null ? String.valueOf(claimId) : entityId;
                LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
                LocalDateTime to = toDate != null ? toDate.plusDays(1).atStartOfDay() : null;

                Specification<AuditLog> facilityFilter = buildFacilityFilter(providerId, employerId);

                Page<AuditLog> result = medicalAuditLogService.searchAuditLogs(
                                effectiveEntityType,
                                effectiveEntityId,
                                null,
                                facilityFilter,
                                action,
                                source,
                                correlationId,
                                from != null ? from.toInstant(ZoneOffset.UTC) : null,
                                to != null ? to.toInstant(ZoneOffset.UTC) : null,
                                pageable);
                return ResponseEntity.ok(ApiResponse.success(auditLogEnrichmentService.enrich(result)));
        }

        /**
         * Builds a facility/company OR-filter without any schema migration: resolves which entity
         * IDs belong to the given provider (across CLAIM/VISIT/PREAUTHORIZATION/PROVIDER) or
         * employer (across MEMBER/EMPLOYER), then restricts the audit log query to
         * (entityType = X AND entityId IN resolvedIds) OR (entityType = Y AND entityId IN ...).
         */
        private Specification<AuditLog> buildFacilityFilter(Long providerId, Long employerId) {
                if (providerId == null && employerId == null) {
                        return null;
                }

                List<Specification<AuditLog>> branches = new ArrayList<>();

                if (providerId != null) {
                        List<String> claimIds = claimRepository.findByProviderId(providerId).stream()
                                        .map(c -> String.valueOf(c.getId())).collect(Collectors.toList());
                        List<String> visitIds = visitRepository.findByProviderId(providerId).stream()
                                        .map(v -> String.valueOf(v.getId())).collect(Collectors.toList());
                        List<String> preAuthIds = preAuthorizationRepository.findByProviderIdAndActiveTrue(providerId).stream()
                                        .map(p -> String.valueOf(p.getId())).collect(Collectors.toList());

                        branches.add(entityTypeAndIdsIn(EntityType.CLAIM, claimIds));
                        branches.add(entityTypeAndIdsIn(EntityType.VISIT, visitIds));
                        branches.add(entityTypeAndIdsIn(EntityType.PREAUTHORIZATION, preAuthIds));
                        branches.add(entityTypeAndIdsIn(EntityType.PROVIDER, List.of(String.valueOf(providerId))));
                }

                if (employerId != null) {
                        List<String> memberIds = memberRepository.findByEmployerId(employerId).stream()
                                        .map(m -> String.valueOf(m.getId())).collect(Collectors.toList());

                        branches.add(entityTypeAndIdsIn(EntityType.MEMBER, memberIds));
                        branches.add(entityTypeAndIdsIn(EntityType.EMPLOYER, List.of(String.valueOf(employerId))));
                }

                Specification<AuditLog> combined = Specification.where(null);
                for (Specification<AuditLog> branch : branches) {
                        combined = combined == null ? Specification.where(branch) : combined.or(branch);
                }
                return combined;
        }

        private Specification<AuditLog> entityTypeAndIdsIn(EntityType entityType, List<String> ids) {
                return (root, query, cb) -> {
                        Predicate typeMatch = cb.equal(root.get("entityType"), entityType);
                        if (ids.isEmpty()) {
                                return cb.and(typeMatch, cb.disjunction());
                        }
                        return cb.and(typeMatch, root.get("entityId").in(ids));
                };
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
                                                MediaType.parseMediaType(
                                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
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
                                                MediaType.parseMediaType(
                                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                                .body(file);
        }

}
