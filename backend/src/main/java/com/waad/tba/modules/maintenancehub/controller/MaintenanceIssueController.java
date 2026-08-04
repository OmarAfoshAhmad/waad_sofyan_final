package com.waad.tba.modules.maintenancehub.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.maintenancehub.dto.MaintenanceHubDtos.AssignRequest;
import com.waad.tba.modules.maintenancehub.dto.MaintenanceHubDtos.IgnoreRequest;
import com.waad.tba.modules.maintenancehub.dto.MaintenanceHubDtos.IssueDetailDto;
import com.waad.tba.modules.maintenancehub.dto.MaintenanceHubDtos.IssueRowDto;
import com.waad.tba.modules.maintenancehub.dto.MaintenanceHubDtos.IssueSummaryDto;
import com.waad.tba.modules.maintenancehub.dto.MaintenanceHubDtos.ResolveRequest;
import com.waad.tba.modules.maintenancehub.entity.IssueSeverity;
import com.waad.tba.modules.maintenancehub.entity.IssueStatus;
import com.waad.tba.modules.maintenancehub.service.IssueManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin API for the unified maintenance/problem ledger. Read/assign/resolve/ignore
 * only — issues are created exclusively by detectors through {@code IssueRegistry},
 * never directly via this API, so the ledger can never drift from what was actually
 * detected.
 */
@RestController
@RequestMapping("/api/v1/admin/maintenance/issues")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class MaintenanceIssueController {

    private final IssueManagementService service;

    @GetMapping
    public ApiResponse<Page<IssueRowDto>> list(
            @RequestParam(required = false) String issueType,
            @RequestParam(required = false) IssueStatus status,
            @RequestParam(required = false) IssueSeverity severity,
            @RequestParam(required = false) Long employerId,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "lastSeenAt"));
        return ApiResponse.success(service.list(issueType, status, severity, employerId, assignedTo, pageable));
    }

    @GetMapping("/summary")
    public ApiResponse<IssueSummaryDto> summary() {
        return ApiResponse.success(service.summary());
    }

    @GetMapping("/{id}")
    public ApiResponse<IssueDetailDto> get(@PathVariable Long id) {
        return ApiResponse.success(service.get(id));
    }

    @PatchMapping("/{id}/assign")
    public ApiResponse<IssueDetailDto> assign(@PathVariable Long id, @RequestBody AssignRequest request,
                                              Authentication authentication) {
        return ApiResponse.success(service.assign(id, request == null ? null : request.assignee(), usernameOf(authentication)),
                "Issue assigned", "تم تعيين المشكلة");
    }

    @PatchMapping("/{id}/resolve")
    public ApiResponse<IssueDetailDto> resolve(@PathVariable Long id, @RequestBody(required = false) ResolveRequest request,
                                               Authentication authentication) {
        return ApiResponse.success(service.resolve(id, request == null ? null : request.note(), usernameOf(authentication)),
                "Issue resolved", "تم حل المشكلة");
    }

    @PatchMapping("/{id}/ignore")
    public ApiResponse<IssueDetailDto> ignore(@PathVariable Long id, @RequestBody(required = false) IgnoreRequest request,
                                              Authentication authentication) {
        return ApiResponse.success(service.ignore(id, request == null ? null : request.note(), usernameOf(authentication)),
                "Issue ignored", "تم تجاهل المشكلة");
    }

    private static String usernameOf(Authentication authentication) {
        return authentication == null ? "SYSTEM" : authentication.getName();
    }
}
