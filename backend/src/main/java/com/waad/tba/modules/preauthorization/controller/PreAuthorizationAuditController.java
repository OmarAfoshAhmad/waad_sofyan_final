package com.waad.tba.modules.preauthorization.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.preauthorization.dto.PreAuthorizationAuditDto;
import com.waad.tba.modules.preauthorization.service.PreAuthorizationAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for PreAuthorization Audit Trail
 */
@RestController
@RequestMapping("/api/v1/pre-authorizations")
@RequiredArgsConstructor
@Slf4j
public class PreAuthorizationAuditController {

    private final PreAuthorizationAuditService auditService;

    /**
     * Get audit history for a specific PreAuthorization
     * GET /api/pre-authorizations/{id}/history
     * 
     * OPEN ACCESS: Any authenticated user can view audit history
     */
    @GetMapping("/{id:\\d+}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<PreAuthorizationAuditDto>>> getAuditHistory(
            @PathVariable("id") Long id,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        log.info("[AUDIT-API] Fetching audit history for PreAuth ID: {}", id);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<PreAuthorizationAuditDto> history = auditService.getAuditHistory(id, pageable);
        
        return ResponseEntity.ok(ApiResponse.success("Audit history retrieved successfully", history));
    }

    /**
     * Get full audit history for a specific PreAuthorization (non-paginated)
     * GET /api/pre-authorizations/{id}/history/full
     */
    @GetMapping("/{id:\\d+}/history/full")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<List<PreAuthorizationAuditDto>>> getFullAuditHistory(
            @PathVariable("id") Long id
    ) {
        log.info("[AUDIT-API] Fetching full audit history for PreAuth ID: {}", id);
        
        List<PreAuthorizationAuditDto> history = auditService.getFullAuditHistory(id);
        
        return ResponseEntity.ok(ApiResponse.success("Full audit history retrieved successfully", history));
    }

    /**
     * Get audit records by user
     * GET /api/pre-authorizations/audits/user/{username}
     */
    @GetMapping("/audits/user/{username}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<Page<PreAuthorizationAuditDto>>> getAuditsByUser(
            @PathVariable("username") String username,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        log.info("[AUDIT-API] Fetching audits for user: {}", username);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<PreAuthorizationAuditDto> audits = auditService.getAuditsByUser(username, pageable);
        
        return ResponseEntity.ok(ApiResponse.success("User audits retrieved successfully", audits));
    }

    /**
     * Get audit records by action type
     * GET /api/pre-authorizations/audits/action/{action}
     */
    @GetMapping("/audits/action/{action}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<Page<PreAuthorizationAuditDto>>> getAuditsByAction(
            @PathVariable("action") String action,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        log.info("[AUDIT-API] Fetching audits for action: {}", action);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<PreAuthorizationAuditDto> audits = auditService.getAuditsByAction(action, pageable);
        
        return ResponseEntity.ok(ApiResponse.success("Action audits retrieved successfully", audits));
    }

    /**
     * Get recent audit records (last N days)
     * GET /api/pre-authorizations/audits/recent
     * 
     * OPEN ACCESS: Any authenticated user can view audit trail
     */
    @GetMapping("/audits/recent")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<PreAuthorizationAuditDto>>> getRecentAudits(
            @RequestParam(name = "days", defaultValue = "7") int days,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        log.info("[AUDIT-API] Fetching audits from last {} days", days);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<PreAuthorizationAuditDto> audits = auditService.getRecentAudits(days, pageable);
        
        return ResponseEntity.ok(ApiResponse.success("Recent audits retrieved successfully", audits));
    }

    /**
     * Search audit records
     * GET /api/pre-authorizations/audits/search
     * 
     * OPEN ACCESS: Any authenticated user can search audit trail
     */
    @GetMapping("/audits/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<PreAuthorizationAuditDto>>> searchAudits(
            @RequestParam(name = "query") String query,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        log.info("[AUDIT-API] Searching audits with query: {}", query);
        
        Pageable pageable = PageRequest.of(page, size);
        Page<PreAuthorizationAuditDto> audits = auditService.searchAudits(query, pageable);
        
        return ResponseEntity.ok(ApiResponse.success("Search results retrieved successfully", audits));
    }

    /**
     * Get audit statistics
     * GET /api/pre-authorizations/audits/statistics
     */
    @GetMapping("/audits/statistics")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<PreAuthorizationAuditService.AuditStatistics>> getAuditStatistics() {
        log.info("[AUDIT-API] Fetching audit statistics");
        
        PreAuthorizationAuditService.AuditStatistics stats = auditService.getStatistics();
        
        return ResponseEntity.ok(ApiResponse.success("Statistics retrieved successfully", stats));
    }
}

