package com.waad.tba.modules.systemadmin.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.systemadmin.entity.AuditLog;
import com.waad.tba.modules.systemadmin.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/audit")
@Tag(name = "Audit Log", description = "Audit log management (SUPER_ADMIN only)")
@Slf4j
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@CrossOrigin(origins = "*")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @Operation(summary = "Get all audit logs")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Success"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ApiResponse<Page<AuditLog>> getAllAuditLogs(Pageable pageable) {
        Page<AuditLog> logs = auditLogService.getAllAuditLogs(pageable);
        return ApiResponse.success("Audit logs retrieved", logs);
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Get audit log by ID")
    public ApiResponse<AuditLog> getAuditLogById(@PathVariable("id") Long id) {
        AuditLog log = auditLogService.getAuditLogById(id);
        return ApiResponse.success("Audit log retrieved", log);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get audit logs by user")
    public ApiResponse<Page<AuditLog>> getAuditLogsByUser(@PathVariable("userId") Long userId, Pageable pageable) {
        Page<AuditLog> logs = auditLogService.getAuditLogsByUser(userId, pageable);
        return ApiResponse.success("User audit logs retrieved", logs);
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    @Operation(summary = "Get audit logs by entity")
    public ApiResponse<Page<AuditLog>> getAuditLogsByEntity(
            @PathVariable("entityType") String entityType,
            @PathVariable("entityId") Long entityId,
            Pageable pageable) {
        Page<AuditLog> logs = auditLogService.getAuditLogsByEntity(entityType, entityId, pageable);
        return ApiResponse.success("Entity audit logs retrieved", logs);
    }

    @GetMapping("/actions")
    @Operation(summary = "Get all action types")
    public ApiResponse<List<String>> getAllActionTypes() {
        List<String> actions = auditLogService.getAllActionTypes();
        return ApiResponse.success("Action types retrieved", actions);
    }

    @GetMapping("/action/{action}")
    @Operation(summary = "Get audit logs by action")
    public ApiResponse<Page<AuditLog>> getAuditLogsByAction(@PathVariable("action") String action, Pageable pageable) {
        Page<AuditLog> logs = auditLogService.getAuditLogsByAction(action, pageable);
        return ApiResponse.success("Action audit logs retrieved", logs);
    }
}

