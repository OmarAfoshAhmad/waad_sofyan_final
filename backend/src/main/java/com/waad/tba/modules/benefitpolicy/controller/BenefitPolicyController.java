package com.waad.tba.modules.benefitpolicy.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyCreateDto;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyResponseDto;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicySelectorDto;
import com.waad.tba.modules.benefitpolicy.dto.BenefitPolicyUpdateDto;
import com.waad.tba.modules.benefitpolicy.entity.BenefitPolicy.BenefitPolicyStatus;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller for Benefit Policy management.
 * 
 * SECURITY (2026-01-16):
 * - EMPLOYER_ADMIN: Automatically filtered to their employer's policies only
 * - SUPER_ADMIN/INSURANCE_ADMIN: No automatic filter (can see all)
 * 
 * Endpoints:
 * - GET /api/benefit-policies - List all (paginated)
 * - GET /api/benefit-policies/{id} - Get by ID
 * - GET /api/benefit-policies/code/{code} - Get by policy code
 * - GET /api/benefit-policies/employer/{id} - List by employer
 * - GET /api/benefit-policies/status/{status} - List by status
 * - GET /api/benefit-policies/effective - Get effective for employer on date
 * - GET /api/benefit-policies/selector - Selector list for dropdowns
 * - GET /api/benefit-policies/expiring - Get policies expiring soon
 * - POST /api/benefit-policies - Create new
 * - PUT /api/benefit-policies/{id} - Update
 * - POST /api/benefit-policies/{id}/activate - Activate policy
 * - POST /api/benefit-policies/{id}/deactivate - Deactivate policy
 * - POST /api/benefit-policies/{id}/suspend - Suspend policy
 * - POST /api/benefit-policies/{id}/cancel - Cancel policy
 * - DELETE /api/benefit-policies/{id} - Soft delete
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/benefit-policies")
@RequiredArgsConstructor
@Tag(name = "Benefit Policy", description = "Manage medical benefit policies")
public class BenefitPolicyController {

    /** Allowlist of valid sort field names to prevent entity field enumeration. */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "name", "policyCode", "status", "startDate", "endDate", "createdAt", "updatedAt");

    private final BenefitPolicyService benefitPolicyService;
    private final AuthorizationService authorizationService;

    // ═══════════════════════════════════════════════════════════════════════════
    // READ ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','EMPLOYER_ADMIN','ACCOUNTANT','MEDICAL_REVIEWER')")
    @Operation(summary = "List all benefit policies", description = "Get paginated list of all benefit policies (filtered by employer if provided). EMPLOYER_ADMIN users are automatically filtered to their employer.")
    public ResponseEntity<ApiResponse<Page<BenefitPolicyResponseDto>>> findAll(
            @Parameter(description = "Employer ID for filtering (null = show all for admin)") @RequestParam(name = "employerId", required = false) Long employerId,
            @Parameter(description = "Page number (0-based)") @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(name = "size", defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(name = "sortDir", defaultValue = "DESC") String sortDir) {

        log.info(
                "[BENEFIT-POLICIES] GET /api/benefit-policies - employerId={}, page={}, size={}, sortBy={}, sortDir={}",
                employerId, page, size, sortBy, sortDir);

        // ═══════════════════════════════════════════════════════════════════════════
        // EMPLOYER_ADMIN SECURITY FILTER (2026-01-16)
        // ═══════════════════════════════════════════════════════════════════════════
        User currentUser = authorizationService.getCurrentUser();
        Long effectiveEmployerId = employerId;

        if (currentUser != null && authorizationService.isEmployerAdmin(currentUser)) {
            // Check feature toggle
            if (!authorizationService.canEmployerViewBenefitPolicies(currentUser)) {
                log.warn("❌ EMPLOYER_ADMIN user {} attempted to view benefit policies but feature is disabled",
                        currentUser.getUsername());
                return ResponseEntity.ok(ApiResponse.success("Benefit policies retrieved", Page.empty()));
            }

            // EMPLOYER_ADMIN is LOCKED to their employer - override any provided filter
            Long employerFilter = authorizationService.getEmployerFilterForUser(currentUser);
            if (employerFilter == null) {
                log.warn("⚠️ EMPLOYER_ADMIN user {} has no employerId assigned", currentUser.getUsername());
                return ResponseEntity.ok(ApiResponse.success("Benefit policies retrieved", Page.empty()));
            }

            effectiveEmployerId = employerFilter;
            log.info("🔒 EMPLOYER_ADMIN filter applied: user={}, locked to employerId={}",
                    currentUser.getUsername(), effectiveEmployerId);
        }

        // Validate sortBy against allowlist to prevent entity field enumeration
        String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        // Validate sortDir with safe fallback instead of exposing internal exception
        // messages
        Sort.Direction safeDirection;
        try {
            safeDirection = Sort.Direction.fromString(sortDir);
        } catch (IllegalArgumentException e) {
            log.warn("[BENEFIT-POLICIES] Invalid sortDir value, defaulting to DESC");
            safeDirection = Sort.Direction.DESC;
        }
        Sort sort = Sort.by(safeDirection, safeSortBy);
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), sort);

        Page<BenefitPolicyResponseDto> result;
        if (effectiveEmployerId != null) {
            result = benefitPolicyService.findByEmployer(effectiveEmployerId, pageable);
        } else {
            result = benefitPolicyService.findAll(pageable);
        }

        log.info("[BENEFIT-POLICIES] Returning {} records (totalElements: {}, totalPages: {})",
                result.getContent().size(), result.getTotalElements(), result.getTotalPages());

        return ResponseEntity.ok(ApiResponse.success("Benefit policies retrieved", result));
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','EMPLOYER_ADMIN','ACCOUNTANT','MEDICAL_REVIEWER')")
    @Operation(summary = "Get benefit policy by ID")
    public ResponseEntity<ApiResponse<BenefitPolicyResponseDto>> findById(@PathVariable Long id) {
        BenefitPolicyResponseDto result = benefitPolicyService.findById(id);
        return ResponseEntity.ok(ApiResponse.success("Benefit policy retrieved", result));
    }

    @GetMapping("/code/{policyCode}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','EMPLOYER_ADMIN','ACCOUNTANT','MEDICAL_REVIEWER')")
    @Operation(summary = "Get benefit policy by policy code")
    public ResponseEntity<ApiResponse<BenefitPolicyResponseDto>> findByCode(@PathVariable String policyCode) {
        BenefitPolicyResponseDto result = benefitPolicyService.findByPolicyCode(policyCode);
        return ResponseEntity.ok(ApiResponse.success("Benefit policy retrieved", result));
    }

    @GetMapping("/employer/{employerOrgId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','EMPLOYER_ADMIN','ACCOUNTANT','MEDICAL_REVIEWER')")
    @Operation(summary = "List benefit policies for an employer")
    public ResponseEntity<ApiResponse<List<BenefitPolicyResponseDto>>> findByEmployer(
            @PathVariable Long employerOrgId) {
        List<BenefitPolicyResponseDto> result = benefitPolicyService.findByEmployer(employerOrgId);
        return ResponseEntity.ok(ApiResponse.success("Benefit policies for employer retrieved", result));
    }

    @GetMapping("/employer/{employerOrgId}/paged")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','EMPLOYER_ADMIN','ACCOUNTANT','MEDICAL_REVIEWER')")
    @Operation(summary = "List benefit policies for an employer (paginated)")
    public ResponseEntity<ApiResponse<Page<BenefitPolicyResponseDto>>> findByEmployerPaged(
            @PathVariable Long employerOrgId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<BenefitPolicyResponseDto> result = benefitPolicyService.findByEmployer(employerOrgId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Benefit policies for employer retrieved", result));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','EMPLOYER_ADMIN','ACCOUNTANT','MEDICAL_REVIEWER')")
    @Operation(summary = "List benefit policies by status")
    public ResponseEntity<ApiResponse<List<BenefitPolicyResponseDto>>> findByStatus(
            @PathVariable String status) {

        BenefitPolicyStatus policyStatus;
        try {
            policyStatus = BenefitPolicyStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid status: " + status));
        }

        List<BenefitPolicyResponseDto> result = benefitPolicyService.findByStatus(policyStatus);
        return ResponseEntity.ok(ApiResponse.success("Benefit policies retrieved", result));
    }

    @GetMapping("/effective")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','EMPLOYER_ADMIN','ACCOUNTANT','MEDICAL_REVIEWER')")
    @Operation(summary = "Get effective policy for employer on a date")
    public ResponseEntity<ApiResponse<BenefitPolicyResponseDto>> findEffective(
            @Parameter(description = "Employer organization ID") @RequestParam(name = "employerOrgId") Long employerOrgId,
            @Parameter(description = "Date to check (defaults to today)") @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        if (date == null) {
            date = LocalDate.now();
        }

        BenefitPolicyResponseDto result = benefitPolicyService.findEffectiveForEmployer(employerOrgId, date);
        if (result == null) {
            return ResponseEntity.ok(ApiResponse.success("No effective policy found", null));
        }
        return ResponseEntity.ok(ApiResponse.success("Effective policy retrieved", result));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','EMPLOYER_ADMIN','ACCOUNTANT','MEDICAL_REVIEWER')")
    @Operation(summary = "Search benefit policies by name or code")
    public ResponseEntity<ApiResponse<Page<BenefitPolicyResponseDto>>> search(
            @RequestParam(name = "q") String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<BenefitPolicyResponseDto> result = benefitPolicyService.search(q, pageable);
        return ResponseEntity.ok(ApiResponse.success("Search results", result));
    }

    @GetMapping("/selector")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','EMPLOYER_ADMIN','ACCOUNTANT','MEDICAL_REVIEWER')")
    @Operation(summary = "Get selector list for dropdowns")
    public ResponseEntity<ApiResponse<List<BenefitPolicySelectorDto>>> getSelectors() {
        List<BenefitPolicySelectorDto> result = benefitPolicyService.getSelectors();
        return ResponseEntity.ok(ApiResponse.success("Selectors retrieved", result));
    }

    @GetMapping("/selector/employer/{employerOrgId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','EMPLOYER_ADMIN','ACCOUNTANT','MEDICAL_REVIEWER')")
    @Operation(summary = "Get selector list for an employer")
    public ResponseEntity<ApiResponse<List<BenefitPolicySelectorDto>>> getSelectorsForEmployer(
            @PathVariable Long employerOrgId) {
        List<BenefitPolicySelectorDto> result = benefitPolicyService.getSelectorsForEmployer(employerOrgId);
        return ResponseEntity.ok(ApiResponse.success("Selectors retrieved", result));
    }

    @GetMapping("/expiring")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','EMPLOYER_ADMIN','ACCOUNTANT','MEDICAL_REVIEWER')")
    @Operation(summary = "Get policies expiring soon")
    public ResponseEntity<ApiResponse<List<BenefitPolicyResponseDto>>> getExpiringSoon(
            @Parameter(description = "Number of days to check (default 30, max 365)") @RequestParam(name = "days", defaultValue = "30") int days) {

        int safeDays = Math.min(Math.max(1, days), 365);
        List<BenefitPolicyResponseDto> result = benefitPolicyService.getPoliciesExpiringSoon(safeDays);
        return ResponseEntity.ok(ApiResponse.success("Expiring policies retrieved", result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CREATE ENDPOINT
    // ═══════════════════════════════════════════════════════════════════════════

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Create a new benefit policy")
    public ResponseEntity<ApiResponse<BenefitPolicyResponseDto>> create(
            @Valid @RequestBody BenefitPolicyCreateDto dto) {

        log.info("Creating benefit policy: {}", dto.getName());
        BenefitPolicyResponseDto result = benefitPolicyService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Benefit policy created successfully", result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE ENDPOINT
    // ═══════════════════════════════════════════════════════════════════════════

    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update an existing benefit policy")
    public ResponseEntity<ApiResponse<BenefitPolicyResponseDto>> update(
            @PathVariable Long id,
            @Valid @RequestBody BenefitPolicyUpdateDto dto) {

        log.info("Updating benefit policy: {}", id);
        BenefitPolicyResponseDto result = benefitPolicyService.update(id, dto);
        return ResponseEntity.ok(ApiResponse.success("Benefit policy updated successfully", result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATUS ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    @PostMapping("/{id:\\d+}/activate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Activate a benefit policy", description = "Only one active policy is allowed per employer per period")
    public ResponseEntity<ApiResponse<BenefitPolicyResponseDto>> activate(@PathVariable Long id) {
        log.info("Activating benefit policy: {}", id);
        BenefitPolicyResponseDto result = benefitPolicyService.activate(id);
        return ResponseEntity.ok(ApiResponse.success("Benefit policy activated", result));
    }

    @PostMapping("/{id:\\d+}/deactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Deactivate (expire) a benefit policy")
    public ResponseEntity<ApiResponse<BenefitPolicyResponseDto>> deactivate(@PathVariable Long id) {
        log.info("Deactivating benefit policy: {}", id);
        BenefitPolicyResponseDto result = benefitPolicyService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.success("Benefit policy deactivated", result));
    }

    @PostMapping("/{id:\\d+}/suspend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Suspend a benefit policy temporarily")
    public ResponseEntity<ApiResponse<BenefitPolicyResponseDto>> suspend(@PathVariable Long id) {
        log.info("Suspending benefit policy: {}", id);
        BenefitPolicyResponseDto result = benefitPolicyService.suspend(id);
        return ResponseEntity.ok(ApiResponse.success("Benefit policy suspended", result));
    }

    @PostMapping("/{id:\\d+}/cancel")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Cancel a benefit policy")
    public ResponseEntity<ApiResponse<BenefitPolicyResponseDto>> cancel(@PathVariable Long id) {
        log.info("Cancelling benefit policy: {}", id);
        BenefitPolicyResponseDto result = benefitPolicyService.cancel(id);
        return ResponseEntity.ok(ApiResponse.success("Benefit policy cancelled", result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DELETE ENDPOINT
    // ═══════════════════════════════════════════════════════════════════════════

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Soft delete a benefit policy")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting benefit policy: {}", id);
        benefitPolicyService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MAINTENANCE ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    @PostMapping("/maintenance/expire-old")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Auto-expire policies past their end date")
    public ResponseEntity<ApiResponse<Integer>> expireOldPolicies() {
        log.info("Running auto-expiration of old policies");
        int count = benefitPolicyService.expireOldPolicies();
        return ResponseEntity.ok(ApiResponse.success("Expired " + count + " policies", count));
    }
}
