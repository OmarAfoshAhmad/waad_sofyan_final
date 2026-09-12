package com.waad.tba.modules.systemadmin.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.common.exception.SystemConfigurationException;
import com.waad.tba.modules.employer.dto.EmployerResponseDto;
import com.waad.tba.modules.employer.service.EmployerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * System Controller - Employer-Centric
 * 
 * Provides system-level endpoints for employer context.
 * This reflects the employer-only architecture where employers
 * are the sole top-level business entity.
 * 
 * Design Philosophy:
 * - Employer is the only top-level entity
 * - No company selection needed from frontend
 * - Employer context is implicit for operations
 * - Simplifies architecture
 * 
 * @created 2025-01-02
 * @updated 2026-02-14 - Migrated from Company to Employer
 */
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "System", description = "System-level configuration and context APIs")
@PreAuthorize("isAuthenticated()")
public class SystemController {

    private final EmployerService employerService;

    /**
     * Get the system's default employer.
     * 
     * This endpoint returns the default employer or the first available employer.
     * Frontend should call this once at startup and cache the result.
     * 
     * @return EmployerResponseDto - The default employer
     */
    @GetMapping("/employer")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Get system default employer",
        description = "Returns the default employer configured in the system. " +
                     "This endpoint reflects the employer-centric operational context. " +
                     "Always returns 200 OK unless there is a configuration error."
    )
    public ResponseEntity<ApiResponse<EmployerResponseDto>> getSystemEmployer() {
        log.info("REST request to get system default employer");

        List<EmployerResponseDto> employers = employerService.getActiveEmployers();

        if (employers.isEmpty()) {
            // Mapped by GlobalExceptionHandler to a safe 500 with a tracking id.
            throw new SystemConfigurationException("No active employers configured");
        }

        // Return the first employer (or the one marked as default)
        EmployerResponseDto defaultEmployer = employers.stream()
            .filter(e -> e.isDefault())
            .findFirst()
            .orElse(employers.get(0));

        log.debug("System employer retrieved: {} (ID: {})",
            defaultEmployer.getName(), defaultEmployer.getId());

        return ResponseEntity.ok(
            ApiResponse.success("System employer retrieved successfully", defaultEmployer));
    }
}
