package com.waad.tba.modules.claim.controller;

import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
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
import com.waad.tba.modules.claim.api.ClaimApiMapper;
import com.waad.tba.modules.claim.api.request.ApproveClaimRequest;
import com.waad.tba.modules.claim.api.request.CreateClaimRequest;
import com.waad.tba.modules.claim.api.request.RejectClaimRequest;
import com.waad.tba.modules.claim.api.request.ReturnForInfoClaimRequest;
import com.waad.tba.modules.claim.api.request.ReviewClaimRequest;
import com.waad.tba.modules.claim.api.request.UpdateClaimDataRequest;
import com.waad.tba.modules.claim.api.request.UpdateClaimRequest;
import com.waad.tba.modules.claim.api.response.ClaimListResponse;
import com.waad.tba.modules.claim.api.response.ClaimResponse;
import com.waad.tba.modules.claim.dto.ClaimViewDto;
import com.waad.tba.modules.claim.dto.CostBreakdownDto;
import com.waad.tba.modules.claim.dto.FinancialSummaryDto;
import com.waad.tba.modules.claim.entity.ClaimStatus;
import com.waad.tba.modules.claim.service.ClaimService;
import com.waad.tba.common.guard.FeatureGuard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ════════════════════════════════════════════════════════════════════════════
 * Claims Controller - API v1 Contract-Protected
 * ════════════════════════════════════════════════════════════════════════════
 * 
 * FINANCIAL SAFETY ARCHITECTURE:
 * This controller enforces BACKEND-AUTHORITATIVE financial calculations.
 * 
 * API v1 Request Contracts FORBID:
 * - approvedAmount fields (calculated by backend)
 * - requestedAmount fields (calculated from contract pricing)
 * - All other monetary values
 * 
 * API v1 Response Contracts are READ-ONLY:
 * - All financial fields calculated by backend
 * - Frontend can display but not modify
 * 
 * @since API v1.0
 * @version 2026-02-01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/claims")
@RequiredArgsConstructor
@Tag(name = "Claims API v1", description = "Claims Management APIs - Full Lifecycle (Contract-Protected)")
public class ClaimController {

    private final ClaimService claimService;
    private final ClaimApiMapper apiMapper;
    private final FeatureGuard featureGuard;

    /**
     * Allowed sort fields for claims list endpoint
     * Prevents PropertyReferenceException from invalid sort fields
     */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "id", "createdAt", "updatedAt", "status", "requestedAmount",
        "approvedAmount", "serviceDate", "providerName", "memberName"
    );

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'DATA_ENTRY', 'PROVIDER_STAFF')")
    @Operation(summary = "Create claim", description = "Create a new claim from visit. All amounts calculated by backend from provider contract.")
    public ResponseEntity<ApiResponse<ClaimResponse>> createClaim(@Valid @RequestBody CreateClaimRequest apiRequest) {
        log.info("📥 [CLAIM-API] Incoming create request: visitId={}, lines={}", 
                 apiRequest.getVisitId(), 
                 apiRequest.getLines() != null ? apiRequest.getLines().size() : 0);
        
        // 🔒 FEATURE-FLAG-GUARD (Phase 10)
        featureGuard.requireProviderPortal();

        try {
            // Convert API v1 request to internal DTO
            ClaimViewDto claim = claimService.createClaim(apiMapper.toCreateDto(apiRequest));
            
            log.info("✅ [CLAIM-API] Claim created successfully: id={}, status={}, amount={}", 
                     claim.getId(), claim.getStatus(), claim.getRequestedAmount());
            
            // Convert internal DTO to API v1 response
            ClaimResponse response = apiMapper.toResponse(claim);
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Claim created successfully", response));
        } catch (Exception e) {
            log.error("❌ [CLAIM-API] Failed to create claim: visitId={}, error={}", 
                      apiRequest.getVisitId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * @deprecated Use /claims/{id}/data or /claims/{id}/review instead
     * This endpoint is DISABLED for security reasons.
     */
    @Deprecated
    @Hidden // Hide from Swagger/OpenAPI documentation
    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'DATA_ENTRY', 'PROVIDER_STAFF')")
    @Operation(summary = "[DEPRECATED - DISABLED] Update claim", description = "DEPRECATED: Use /data or /review endpoints instead. This endpoint is disabled.")
    public ResponseEntity<ApiResponse<ClaimResponse>> updateClaim(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateClaimRequest apiRequest) {
        
        // SECURITY: This endpoint is disabled to prevent bypassing role-based field restrictions
        throw new UnsupportedOperationException(
            "This endpoint is deprecated and disabled. " +
            "Use PUT /claims/{id}/data for data edits or PUT /claims/{id}/review for review actions."
        );
    }

    /**
     * Update claim DATA (for PROVIDER and EMPLOYER_ADMIN).
     * SECURITY: Only allowed in DRAFT and NEEDS_CORRECTION statuses.
     * 
     * @since Provider Portal Security Fix (Phase 0)
     */
    @PutMapping("/{id:\\d+}/data")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'DATA_ENTRY', 'PROVIDER_STAFF')")
    @Operation(summary = "Update claim medical/financial data", description = "Updates pricing and medical codes for a DRAFT claim.")
    public ResponseEntity<ApiResponse<ClaimResponse>> updateClaimData(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateClaimDataRequest apiRequest) {
        log.info("📝 Updating claim data {}", id);
        
        // 🔒 FEATURE-FLAG-GUARD (Phase 10)
        featureGuard.requireProviderPortal();

        try {
            ClaimViewDto claim = claimService.updateClaimData(id, apiMapper.toDataUpdateDto(apiRequest));
            ClaimResponse response = apiMapper.toResponse(claim);
            
            return ResponseEntity.ok(ApiResponse.success("Claim data updated successfully", response));
        } catch (Exception e) {
            log.error("❌ [CLAIM-API] Failed to update claim data: id={}, error={}", id, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Review claim (for REVIEWER and INSURANCE_ADMIN).
     * SECURITY: Reviewers can ONLY change status/comment/approvedAmount.
     * Data fields cannot be modified.
     * 
     * @since Provider Portal Security Fix (Phase 0)
     */
    @PutMapping("/{id:\\d+}/review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    @Operation(summary = "Review claim", description = "Reviewer action: change status, add comment, set approved amount.")
    public ResponseEntity<ApiResponse<ClaimResponse>> reviewClaim(
            @PathVariable("id") Long id,
            @Valid @RequestBody ReviewClaimRequest apiRequest) {
        log.info("🧐 Reviewing claim {}", id);

        // NOTE: Review is an INTERNAL operation by MEDICAL_REVIEWER/SUPER_ADMIN.
        // It is NOT gated by feature flags (those only affect provider-facing submission channels).

        try {
            ClaimViewDto claim = claimService.reviewClaim(id, apiMapper.toReviewDto(apiRequest));
            ClaimResponse response = apiMapper.toResponse(claim);
            
            return ResponseEntity.ok(ApiResponse.success("Claim reviewed successfully", response));
        } catch (Exception e) {
            log.error("❌ [CLAIM-API] Failed to review claim: id={}, error={}", id, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Submit claim for review.
     * Transitions from DRAFT or NEEDS_CORRECTION to SUBMITTED.
     * 
     * @since Provider Portal Draft-First Model (Phase 2)
     */
    @PostMapping("/{id:\\d+}/submit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'DATA_ENTRY', 'PROVIDER_STAFF')")
    @Operation(summary = "Submit claim", description = "Submit a DRAFT claim for review. Transitions state to SUBMITTED.")
    public ResponseEntity<ApiResponse<ClaimResponse>> submitClaim(@PathVariable("id") Long id) {
        log.info("🛫 Submitting claim {}", id);
        
        // 🔒 FEATURE-FLAG-GUARD (Phase 10)
        featureGuard.requireDirectClaimSubmission();

        try {
            ClaimViewDto claim = claimService.submitClaim(id);
            ClaimResponse response = apiMapper.toResponse(claim);
            
            return ResponseEntity.ok(ApiResponse.success("Claim submitted successfully", response));
        } catch (Exception e) {
            log.error("❌ [CLAIM-API] Failed to submit claim: id={}, error={}", id, e.getMessage(), e);
            throw e;
        }
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get claim by ID", description = "Retrieve a single claim with complete financial snapshot")
    public ResponseEntity<ApiResponse<ClaimResponse>> getClaim(@PathVariable("id") Long id) {
        ClaimViewDto claim = claimService.getClaim(id);
        ClaimResponse response = apiMapper.toResponse(claim);
        return ResponseEntity.ok(ApiResponse.success("Claim retrieved successfully", response));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List claims", description = "List claims with pagination and optional filtering")
    public ResponseEntity<ApiResponse<ClaimListResponse>> listClaims(
            @RequestParam(name = "employerId", required = false) Long employerId,
            @RequestParam(name = "providerId", required = false) Long providerId,
            @RequestParam(name = "status", required = false) ClaimStatus status,
            @RequestParam(name = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(name = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "desc") String sortDir,
            @RequestParam(name = "search", required = false) String search) {
        
        // Validate and sanitize sortBy field to prevent PropertyReferenceException
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            log.warn("Invalid sort field requested: '{}', falling back to default 'createdAt'", sortBy);
            sortBy = "createdAt";
        }
        
        Page<ClaimViewDto> claimsPage = claimService.listClaims(
                employerId, providerId, status, dateFrom, dateTo, Math.max(0, page - 1), size, sortBy, sortDir, search);

        ClaimListResponse response = apiMapper.toListResponse(claimsPage);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'DATA_ENTRY', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<Void>> deleteClaim(@PathVariable("id") Long id) {
        claimService.deleteClaim(id);
        return ResponseEntity.ok(ApiResponse.success("Claim deleted successfully", null));
    }

    @GetMapping("/count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Long>> countClaims(
            @RequestParam(name = "employerId", required = false) Long employerId) {
        long count = claimService.countClaims(employerId);
        return ResponseEntity.ok(ApiResponse.success("Claims counted successfully", count));
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ClaimViewDto>>> search(
            @RequestParam(name = "employerId", required = false) Long employerId,
            @RequestParam(name = "query") String query) {
        List<ClaimViewDto> results = claimService.search(employerId, query);
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    @GetMapping("/member/{memberId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get claims by member", description = "Retrieve all claims for a specific member")
    public ResponseEntity<ApiResponse<List<ClaimResponse>>> getClaimsByMember(@PathVariable("memberId") Long memberId) {
        List<ClaimViewDto> claims = claimService.getClaimsByMember(memberId);
        List<ClaimResponse> responses = claims.stream()
                .map(apiMapper::toResponse)
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Member claims retrieved successfully", responses));
    }

    @GetMapping("/pre-authorization/{preAuthorizationId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get claims by pre-authorization", description = "Retrieve all claims linked to a pre-authorization")
    public ResponseEntity<ApiResponse<List<ClaimResponse>>> getClaimsByPreAuthorization(@PathVariable("preAuthorizationId") Long preAuthorizationId) {
        List<ClaimViewDto> claims = claimService.getClaimsByPreAuthorization(preAuthorizationId);
        List<ClaimResponse> responses = claims.stream()
                .map(apiMapper::toResponse)
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Pre-authorization claims retrieved successfully", responses));
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MVP PHASE: Claim Lifecycle Endpoints
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Take a submitted claim for review.
     * Transitions: SUBMITTED → UNDER_REVIEW
     */
    @PostMapping("/{id:\\d+}/start-review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    @Operation(summary = "Start review", description = "Take a submitted claim for review. Transitions to UNDER_REVIEW status.")
    public ResponseEntity<ApiResponse<ClaimResponse>> startReview(@PathVariable("id") Long id) {
        ClaimViewDto claim = claimService.startReview(id);
        ClaimResponse response = apiMapper.toResponse(claim);
        return ResponseEntity.ok(ApiResponse.success("تم استلام المطالبة للمراجعة", response));
    }

    /**
     * ═══════════════════════════════════════════════════════════════════════════════
     * ⚠️ CRITICAL FINANCIAL SAFETY ENDPOINT ⚠️
     * ═══════════════════════════════════════════════════════════════════════════════
     * 
     * POST /api/v1/claims/{id}/approve
     * 
     * SPLIT-PHASE APPROVAL: Approve claim asynchronously (non-blocking)
     * Returns immediately with status APPROVAL_IN_PROGRESS.
     * Heavy calculations execute in background.
     * 
     * Client should poll GET /api/v1/claims/{id} to check for final status.
     * 
     * ⚠️⚠️⚠️ FINANCIAL SAFETY GUARANTEE ⚠️⚠️⚠️
     * 
     * The ApproveClaimRequest contract FORBIDS approvedAmount field.
     * The backend calculates approved amount from:
     * 
     * 1. Provider Contract Pricing
     * 2. Benefit Policy Rules
     * 3. Coverage Limits
     * 4. Cost Breakdown Engine
     * 
     * Frontend CANNOT influence approval amount.
     * 
     * Validates:
     * - Coverage limits (via CoverageValidationService)
     * - Financial snapshot equation: RequestedAmount = PatientCoPay + NetProviderAmount
     */
    @PostMapping("/{id:\\d+}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    @Operation(
        summary = "Approve claim (async)", 
        description = "Request claim approval. Returns immediately with APPROVAL_IN_PROGRESS status. " +
                      "Poll /api/v1/claims/{id} for final result. " +
                      "⚠️ CRITICAL: Approved amount is CALCULATED BY BACKEND, not from request."
    )
    public ResponseEntity<ApiResponse<ClaimResponse>> approveClaim(
            @PathVariable("id") Long id,
            @Valid @RequestBody ApproveClaimRequest apiRequest) {
        
        // Convert API v1 request to internal DTO
        // ⚠️ CRITICAL: This conversion does NOT include approvedAmount
        // Backend service layer calculates the approved amount
        ClaimViewDto claim = claimService.requestApproval(id, apiMapper.toApproveDto(apiRequest));
        
        // Convert internal DTO to API v1 response
        ClaimResponse response = apiMapper.toResponse(claim);
        
        return ResponseEntity.ok(ApiResponse.success("جاري معالجة الموافقة...", response));
    }

    /**
     * Reject a claim with mandatory reason.
     * Transitions: SUBMITTED/UNDER_REVIEW → REJECTED (terminal)
     */
    @PostMapping("/{id:\\d+}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    @Operation(summary = "Reject claim", description = "Reject a claim. Rejection reason is mandatory.")
    public ResponseEntity<ApiResponse<ClaimResponse>> rejectClaim(
            @PathVariable("id") Long id,
            @Valid @RequestBody RejectClaimRequest apiRequest) {
        ClaimViewDto claim = claimService.rejectClaim(id, apiMapper.toRejectDto(apiRequest));
        ClaimResponse response = apiMapper.toResponse(claim);
        return ResponseEntity.ok(ApiResponse.success("تم رفض المطالبة", response));
    }


    /**
     * Return a claim for additional information.
     * Transitions: UNDER_REVIEW → RETURNED_FOR_INFO
     * 
     * Business Rules:
     * - Claim must be in UNDER_REVIEW status
     * - Reason is mandatory
     * - Member can then edit and resubmit
     */
    @PostMapping("/{id:\\d+}/return-for-info")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'INSURANCE_ADMIN')")
    @Operation(summary = "Return claim for info", description = "Transitions claim from UNDER_REVIEW back to NEEDS_CORRECTION.")
    public ResponseEntity<ApiResponse<ClaimResponse>> returnForInfo(
            @PathVariable("id") Long id,
            @Valid @RequestBody ReturnForInfoClaimRequest apiRequest) {
        log.info("↩️ Returning claim {} for info", id);
        
        // 🔒 FEATURE-FLAG-GUARD (Phase 10)
        featureGuard.requireProviderPortal();

        try {
            ClaimViewDto claim = claimService.returnForInfo(id, apiMapper.toReturnForInfoDto(apiRequest));
            ClaimResponse response = apiMapper.toResponse(claim);
            return ResponseEntity.ok(ApiResponse.success("تم إعادة المطالبة لطلب معلومات إضافية", response));
        } catch (Exception e) {
            log.error("❌ [CLAIM-API] Failed to return claim for info: id={}, error={}", id, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get cost breakdown for a claim (Financial Snapshot).
     * Shows: RequestedAmount | PatientCoPay | NetProviderAmount
     */
    @GetMapping("/{id:\\d+}/cost-breakdown")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get cost breakdown", description = "Get detailed cost breakdown including deductible, co-pay, and insurance amount.")
    public ResponseEntity<ApiResponse<CostBreakdownDto>> getCostBreakdown(@PathVariable("id") Long id) {
        CostBreakdownDto breakdown = claimService.getCostBreakdownDto(id);
        return ResponseEntity.ok(ApiResponse.success("تم استرجاع تفاصيل التكلفة", breakdown));
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MVP PHASE: Inbox Endpoints (for Operations Staff)
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Get claims pending review (Inbox for reviewers).
     * Returns claims in SUBMITTED or UNDER_REVIEW status.
     */
    @GetMapping("/inbox/pending")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Claims pending review", description = "Get claims awaiting review (SUBMITTED or UNDER_REVIEW status)")
    public ResponseEntity<ApiResponse<ClaimListResponse>> getPendingClaims(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "asc") String sortDir,
            @RequestParam(name = "providerId", required = false) Long providerId) {

        Page<ClaimViewDto> claimsPage = claimService.getPendingClaims(
                Math.max(0, page - 1), size, sortBy, sortDir, providerId);

        ClaimListResponse response = apiMapper.toListResponse(claimsPage);

        return ResponseEntity.ok(ApiResponse.success("المطالبات المعلقة", response));
    }

    /**
     * Get approved claims ready for settlement (Inbox for finance).
     */
    @GetMapping("/inbox/approved")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Claims ready for settlement", description = "Get approved claims awaiting settlement (APPROVED status)")
    public ResponseEntity<ApiResponse<ClaimListResponse>> getApprovedClaims(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "reviewedAt") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "asc") String sortDir,
            @RequestParam(name = "providerId", required = false) Long providerId) {

        Page<ClaimViewDto> claimsPage = claimService.getApprovedClaims(
                Math.max(0, page - 1), size, sortBy, sortDir, providerId);

        ClaimListResponse response = apiMapper.toListResponse(claimsPage);

        return ResponseEntity.ok(ApiResponse.success("المطالبات الموافق عليها", response));
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // ADDITIONAL QUERY ENDPOINTS (Added 2026-01-14)
    // For Contract-First compliance
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Get claims by Visit ID.
     * Returns all claims associated with a specific visit.
     */
    @GetMapping("/visit/{visitId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get claims by visit", description = "Retrieve all claims for a specific visit")
    public ResponseEntity<ApiResponse<List<ClaimResponse>>> getClaimsByVisit(@PathVariable("visitId") Long visitId) {
        List<ClaimViewDto> claims = claimService.getClaimsByVisit(visitId);
        List<ClaimResponse> responses = claims.stream()
                .map(apiMapper::toResponse)
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Claims for visit retrieved successfully", responses));
    }

    /**
     * Get claim by claim number.
     * Returns a single claim by its unique identifier.
     */
    @GetMapping("/number/{claimNumber}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get claim by number", description = "Retrieve a claim by its unique claim number")
    public ResponseEntity<ApiResponse<ClaimResponse>> getClaimByNumber(@PathVariable("claimNumber") Long claimNumber) {
        ClaimViewDto claim = claimService.getClaimByNumber(claimNumber);
        ClaimResponse response = apiMapper.toResponse(claim);
        return ResponseEntity.ok(ApiResponse.success("Claim retrieved successfully", response));
    }

    /**
     * Get claims by status with pagination.
     * Returns claims filtered by their status.
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get claims by status", description = "Retrieve claims filtered by status with pagination")
    public ResponseEntity<ApiResponse<ClaimListResponse>> getClaimsByStatus(
            @PathVariable("status") ClaimStatus status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "desc") String sortDir) {

        Page<ClaimViewDto> claimsPage = claimService.getClaimsByStatus(
                status, Math.max(0, page - 1), size, sortBy, sortDir);

        ClaimListResponse response = apiMapper.toListResponse(claimsPage);

        return ResponseEntity.ok(ApiResponse.success("Claims by status retrieved", response));
    }

    /**
     * Get financial summary statistics for reports.
     * Calculates KPIs server-side for accuracy and performance.
     */
    @GetMapping("/financial-summary")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get financial summary", description = "Get aggregated financial KPIs for reports with filtering support.")
    public ResponseEntity<ApiResponse<FinancialSummaryDto>> getFinancialSummary(
            @RequestParam(name = "employerId", required = false) Long employerId,
            @RequestParam(name = "providerId", required = false) Long providerId,
            @RequestParam(name = "status", required = false) ClaimStatus status,
            @RequestParam(name = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(name = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        FinancialSummaryDto summary = claimService.getFinancialSummary(employerId, providerId, status, dateFrom, dateTo);
        return ResponseEntity.ok(ApiResponse.success("Financial summary retrieved successfully", summary));
    }
}

