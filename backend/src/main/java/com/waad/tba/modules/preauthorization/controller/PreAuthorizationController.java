package com.waad.tba.modules.preauthorization.controller;

import com.waad.tba.modules.preauthorization.api.request.*;
import com.waad.tba.modules.preauthorization.api.response.*;
import com.waad.tba.modules.preauthorization.api.PreAuthorizationApiMapper;
import com.waad.tba.modules.preauthorization.dto.*;
import com.waad.tba.modules.preauthorization.entity.PreAuthorization.PreAuthStatus;
import com.waad.tba.modules.preauthorization.entity.PreAuthorizationAttachment;
import com.waad.tba.modules.preauthorization.service.PreAuthorizationService;
import com.waad.tba.modules.preauthorization.service.PreAuthorizationAttachmentService;
import com.waad.tba.common.file.FileResourceUtils;
import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.common.dto.PaginationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Hidden;

import java.util.List;
import java.util.Set;

/**
 * REST Controller for PreAuthorization management
 * 
 * ✅ API v1: Uses contract-first approach with PreAuthorizationApiMapper
 * - All request DTOs validated via API contracts
 * - All response DTOs converted to API responses
 * - Critical: ApprovePreAuthorizationRequest FORBIDS decision fields
 */
@RestController
@RequestMapping("/api/v1/pre-authorizations")
@RequiredArgsConstructor
@Slf4j
public class PreAuthorizationController {

    private final PreAuthorizationService preAuthorizationService;
    private final PreAuthorizationAttachmentService attachmentService;
    private final PreAuthorizationApiMapper apiMapper;

    /**
     * Allowed sort fields for pre-authorization list endpoints
     * Prevents PropertyReferenceException from invalid sort fields
     */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "id", "createdAt", "updatedAt", "status", "requestDate",
        "expectedServiceDate", "contractPrice", "approvedAmount",
        "referenceNumber", "preAuthNumber"
    );

    // ==================== CREATE ====================

    /**
     * Create a new pre-authorization
     * POST /api/v1/pre-authorizations
     * 
     * ✅ API v1: Uses CreatePreAuthorizationRequest (forbids approvedAmount, copayPercentage, contractPrice)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF', 'DATA_ENTRY')")
    public ResponseEntity<ApiResponse<PreAuthorizationResponse>> createPreAuthorization(
            @Valid @RequestBody CreatePreAuthorizationRequest request,
            Authentication authentication) {
        
        log.info("📥 [PRE-AUTH-API] Incoming create request: visitId={}, memberId={}, providerId={}, serviceId={}", 
                 request.getVisitId(), request.getMemberId(), 
                 request.getProviderId(), request.getMedicalServiceId());
        
        try {
            // Convert API contract to internal DTO
            PreAuthorizationCreateDto createDto = apiMapper.toCreateDto(request);
            
            String createdBy = authentication != null ? authentication.getName() : "system";
            PreAuthorizationResponseDto internalResponse = preAuthorizationService.createPreAuthorization(createDto, createdBy);
            
            log.info("✅ [PRE-AUTH-API] Pre-authorization created successfully: id={}, refNumber={}, status={}", 
                     internalResponse.getId(), internalResponse.getReferenceNumber(), internalResponse.getStatus());
            
            // Convert internal DTO to API response
            PreAuthorizationResponse response = apiMapper.toResponse(internalResponse);
            
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Pre-authorization created successfully", response));
        } catch (Exception e) {
            log.error("❌ [PRE-AUTH-API] Failed to create pre-authorization: visitId={}, error={}", 
                      request.getVisitId(), e.getMessage(), e);
            throw e;
        }
    }

    // ==================== UPDATE ====================

    /**
     * @deprecated Use /pre-authorizations/{id}/data or /pre-authorizations/{id}/review instead
     * This endpoint is DISABLED for security reasons.
     */
    @Deprecated
    @Hidden // Hide from Swagger/OpenAPI documentation
    @PutMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF', 'DATA_ENTRY')")
    public ResponseEntity<ApiResponse<PreAuthorizationResponse>> updatePreAuthorization(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePreAuthorizationRequest request,
            Authentication authentication) {
        
        // SECURITY: This endpoint is disabled to prevent bypassing role-based field restrictions
        throw new UnsupportedOperationException(
            "This endpoint is deprecated and disabled. " +
            "Use PUT /pre-authorizations/{id}/data for data edits or PUT /pre-authorizations/{id}/review for review actions."
        );
    }

    /**
     * Update pre-authorization DATA (for PROVIDER and EMPLOYER_ADMIN).
     * SECURITY: Only allowed in PENDING and NEEDS_CORRECTION statuses.
     * 
     * @since Provider Portal Security Fix (Phase 3)
     */
    @PutMapping("/{id:\\d+}/data")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF', 'DATA_ENTRY')")
    public ResponseEntity<ApiResponse<PreAuthorizationResponse>> updatePreAuthData(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePreAuthDataRequest request,
            Authentication authentication) {
        
        String updatedBy = authentication != null ? authentication.getName() : "system";
        PreAuthorizationResponseDto internalResponse = preAuthorizationService.updatePreAuthData(
                id, apiMapper.toDataUpdateDto(request), updatedBy);
        
        PreAuthorizationResponse response = apiMapper.toResponse(internalResponse);
        
        return ResponseEntity.ok(ApiResponse.success("Pre-authorization data updated successfully", response));
    }

    /**
     * Review pre-authorization (for REVIEWER and INSURANCE_ADMIN).
     * SECURITY: Reviewers can ONLY change status/comment/approvedAmount.
     * 
     * @since Provider Portal Security Fix (Phase 3)
     */
    @PutMapping("/{id:\\d+}/review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<PreAuthorizationResponse>> reviewPreAuth(
            @PathVariable Long id,
            @Valid @RequestBody ReviewPreAuthRequest request,
            Authentication authentication) {
        
        String reviewedBy = authentication != null ? authentication.getName() : "system";
        PreAuthorizationResponseDto internalResponse = preAuthorizationService.reviewPreAuth(
                id, apiMapper.toReviewDto(request), reviewedBy);
        
        PreAuthorizationResponse response = apiMapper.toResponse(internalResponse);
        
        return ResponseEntity.ok(ApiResponse.success("Pre-authorization reviewed successfully", response));
    }

    /**
     * Submit pre-authorization for review.
     * Transitions from PENDING or NEEDS_CORRECTION to UNDER_REVIEW.
     * 
     * @since Provider Portal Draft-First Model (Phase 3)
     */
    @PostMapping("/{id:\\d+}/submit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF', 'DATA_ENTRY')")
    public ResponseEntity<ApiResponse<PreAuthorizationResponse>> submitPreAuth(
            @PathVariable Long id,
            Authentication authentication) {
        
        String submittedBy = authentication != null ? authentication.getName() : "system";
        PreAuthorizationResponseDto internalResponse = preAuthorizationService.submitPreAuth(id, submittedBy);
        
        PreAuthorizationResponse response = apiMapper.toResponse(internalResponse);
        
        return ResponseEntity.ok(ApiResponse.success("Pre-authorization submitted successfully", response));
    }

    // ==================== APPROVE ====================

    /**
     * ═══════════════════════════════════════════════════════════════════════════════
     * SPLIT-PHASE APPROVAL: Approve pre-authorization asynchronously (non-blocking)
     * ═══════════════════════════════════════════════════════════════════════════════
     * 
     * POST /api/v1/pre-authorizations/{id}/approve
     * 
     * ⚠️ CRITICAL SECURITY FIX - API v1:
     * - Uses ApprovePreAuthorizationRequest (ONLY contains approvalNotes)
     * - FORBIDS: approvedAmount, copayPercentage, copayAmount, insuranceCoveredAmount, coverageLimits, expiryDate
     * - Backend calculates ALL decision values from: ProviderContract + BenefitPolicy + member eligibility
     * 
     * Returns immediately with status APPROVAL_IN_PROGRESS.
     * Heavy calculations execute in background.
     * 
     * Client should poll GET /api/v1/pre-authorizations/{id} to check for final status.
     */
    @PostMapping("/{id:\\d+}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<PreAuthorizationResponse>> approvePreAuthorization(
            @PathVariable Long id,
            @Valid @RequestBody ApprovePreAuthorizationRequest request,
            Authentication authentication) {
        
        log.info("[API v1] Requesting approval for pre-authorization {} (decision fields calculated by backend)", id);
        
        // Convert API contract to internal DTO
        // ✅ CRITICAL: apiMapper.toApproveDto() does NOT set approvedAmount or copayPercentage
        // Backend service will calculate these from contract pricing and benefit policy
        PreAuthorizationApproveDto approveDto = apiMapper.toApproveDto(request);
        
        String approvedBy = authentication != null ? authentication.getName() : "system";
        PreAuthorizationResponseDto internalResponse = preAuthorizationService.requestApproval(id, approveDto, approvedBy);
        
        // Convert internal DTO to API response
        PreAuthorizationResponse response = apiMapper.toResponse(internalResponse);
        
        return ResponseEntity.ok(ApiResponse.success("جاري معالجة الموافقة...", response));
    }

    // ==================== REJECT ====================

    /**
     * Reject pre-authorization
     * POST /api/v1/pre-authorizations/{id}/reject
     * 
     * ✅ API v1: Uses RejectPreAuthorizationRequest (mandatory rejection reason)
     */
    @PostMapping("/{id:\\d+}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<PreAuthorizationResponse>> rejectPreAuthorization(
            @PathVariable Long id,
            @Valid @RequestBody RejectPreAuthorizationRequest request,
            Authentication authentication) {
        
        log.info("[API v1] Rejecting pre-authorization {}", id);
        
        // Convert API contract to internal DTO
        PreAuthorizationRejectDto rejectDto = apiMapper.toRejectDto(request);
        
        String rejectedBy = authentication != null ? authentication.getName() : "system";
        PreAuthorizationResponseDto internalResponse = preAuthorizationService.rejectPreAuthorization(id, rejectDto, rejectedBy);
        
        // Convert internal DTO to API response
        PreAuthorizationResponse response = apiMapper.toResponse(internalResponse);
        
        return ResponseEntity.ok(ApiResponse.success("Pre-authorization rejected", response));
    }

    // ==================== CANCEL ====================

    /**
     * Cancel pre-authorization
     * POST /api/pre-authorizations/{id}/cancel
     */
    @PostMapping("/{id:\\d+}/cancel")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PreAuthorizationResponseDto>> cancelPreAuthorization(
            @PathVariable Long id,
            @RequestParam(name = "reason", required = false) String reason,
            Authentication authentication) {
        
        log.info("[API] Cancelling pre-authorization {}", id);
        
        String cancelledBy = authentication != null ? authentication.getName() : "system";
        String cancelReason = reason != null ? reason : "Cancelled by user";
        PreAuthorizationResponseDto response = preAuthorizationService.cancelPreAuthorization(id, cancelReason, cancelledBy);
        
        return ResponseEntity.ok(ApiResponse.success("Pre-authorization cancelled", response));
    }

    // ==================== ACKNOWLEDGE ====================

    /**
     * Acknowledge pre-authorization (Provider viewed approval)
     * POST /api/v1/pre-authorizations/{id}/acknowledge
     * 
     * Lifecycle: APPROVED → ACKNOWLEDGED
     * Permission: Providers can acknowledge their own pre-authorizations
     */
    @PostMapping("/{id:\\d+}/acknowledge")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF', 'DATA_ENTRY')")
    public ResponseEntity<ApiResponse<PreAuthorizationResponse>> acknowledgePreAuthorization(
            @PathVariable Long id,
            Authentication authentication) {
        
        log.info("[API v1] Provider acknowledging pre-authorization {}", id);
        
        String acknowledgedBy = authentication != null ? authentication.getName() : "system";
        PreAuthorizationResponseDto internalResponse = preAuthorizationService.acknowledgePreAuthorization(id, acknowledgedBy);
        
        // Convert internal DTO to API response
        PreAuthorizationResponse response = apiMapper.toResponse(internalResponse);
        
        return ResponseEntity.ok(ApiResponse.success("Pre-authorization acknowledged", response));
    }

    // ==================== MARK AS USED ====================

    /**
     * Mark pre-authorization as USED (typically called by ClaimService)
     * POST /api/v1/pre-authorizations/{id}/mark-used
     * 
     * Lifecycle: APPROVED/ACKNOWLEDGED → USED
     * Note: This is usually automatic when a claim is created, but exposed for manual use if needed
     */
    @PostMapping("/{id:\\d+}/mark-used")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<PreAuthorizationResponse>> markPreAuthorizationAsUsed(
            @PathVariable Long id,
            @RequestParam(name = "claimNumber", required = false) String claimNumber,
            Authentication authentication) {
        
        log.info("[API v1] Marking pre-authorization {} as USED", id);
        
        String updatedBy = authentication != null ? authentication.getName() : "system";
        String claim = claimNumber != null ? claimNumber : "Manual";
        PreAuthorizationResponseDto internalResponse = preAuthorizationService.markAsUsed(id, claim, updatedBy);
        
        // Convert internal DTO to API response
        PreAuthorizationResponse response = apiMapper.toResponse(internalResponse);
        
        return ResponseEntity.ok(ApiResponse.success("Pre-authorization marked as used", response));
    }

    // ==================== DELETE ====================

    /**
     * Delete pre-authorization (soft delete)
     * DELETE /api/pre-authorizations/{id}
     */
    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePreAuthorization(
            @PathVariable Long id,
            Authentication authentication) {
        
        log.info("[API] Deleting pre-authorization {}", id);
        
        String deletedBy = authentication != null ? authentication.getName() : "system";
        preAuthorizationService.deletePreAuthorization(id, deletedBy);
        
        return ResponseEntity.ok(ApiResponse.<Void>success("Pre-authorization deleted successfully", null));
    }

    // ==================== ATTACHMENTS ====================

    /**
     * Upload attachment for pre-authorization
     * POST /api/pre-authorizations/{id}/attachments
     */
    @PostMapping(value = "/{id:\\d+}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF', 'DATA_ENTRY')")
    public ResponseEntity<ApiResponse<PreAuthorizationAttachment>> uploadAttachment(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "attachmentType", defaultValue = "OTHER") String attachmentType,
            Authentication authentication) {
        
        log.info("[API] Uploading attachment for pre-authorization {}, type: {}", id, attachmentType);
        
        String uploadedBy = authentication != null ? authentication.getName() : "system";
        PreAuthorizationAttachment attachment = attachmentService.uploadAttachment(id, file, attachmentType, uploadedBy);
        
        return ResponseEntity.ok(ApiResponse.success("Attachment uploaded successfully", attachment));
    }

    // ==================== GET ALL (PAGINATED) ====================

    /**
     * Get all pre-authorizations with pagination
     * GET /api/v1/pre-authorizations
     * 
     * ✅ API v1: Returns PreAuthorizationListResponse with decision fields READ-ONLY
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<PreAuthorizationListResponse>> getAllPreAuthorizations(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sortDirection", defaultValue = "DESC") String sortDirection) {
        
        log.info("[API v1] Fetching all pre-authorizations, page: {}, size: {}", page, size);
        
        // Validate and sanitize sortBy field
        if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
            log.warn("Invalid sort field requested: '{}', falling back to default 'createdAt'", sortBy);
            sortBy = "createdAt";
        }
        
        Sort.Direction direction = Sort.Direction.fromString(sortDirection);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        Page<PreAuthorizationResponseDto> internalPage = preAuthorizationService.getAllPreAuthorizations(pageable);
        
        // Convert to API response
        PreAuthorizationListResponse response = apiMapper.toListResponse(internalPage);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ==================== INBOX - PENDING PRE-AUTHORIZATIONS ====================

    /**
     * Get pending pre-authorizations for inbox (Operations Queue)
     * GET /api/pre-authorizations/inbox/pending
     * 
     * Returns pre-authorizations with PENDING status for review.
     * Ordered by createdAt ASC (FIFO - First In First Out) by default.
     */
    @GetMapping("/inbox/pending")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<Page<PreAuthorizationResponseDto>>> getPendingInbox(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "ASC") String sortDir) {
        
        log.info("[API] Fetching pending pre-authorizations for inbox, page: {}, size: {}", page, size);
        
        Sort.Direction direction = Sort.Direction.fromString(sortDir);
        // Convert 1-based page to 0-based for Spring Data (like ClaimController does)
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), size, Sort.by(direction, sortBy));
        
        Page<PreAuthorizationResponseDto> pageResult = preAuthorizationService.getPendingInbox(pageable);
        
        return ResponseEntity.ok(ApiResponse.success(pageResult));
    }

    // ==================== GET BY ID ====================

    /**
     * Get pre-authorization by ID
     * GET /api/v1/pre-authorizations/{id}
     * 
     * ✅ API v1: Returns PreAuthorizationResponse with all decision fields READ-ONLY
     */
    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<PreAuthorizationResponse>> getPreAuthorizationById(@PathVariable Long id) {
        log.info("[API v1] Fetching pre-authorization {}", id);
        
        PreAuthorizationResponseDto internalResponse = preAuthorizationService.getPreAuthorizationById(id);
        
        // Convert internal DTO to API response
        PreAuthorizationResponse response = apiMapper.toResponse(internalResponse);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ==================== GET BY REFERENCE ====================

    /**
     * Get pre-authorization by reference number
     * GET /api/pre-authorizations/reference/{referenceNumber}
     */
    @GetMapping("/reference/{referenceNumber}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<PreAuthorizationResponseDto>> getPreAuthorizationByReference(
            @PathVariable String referenceNumber) {
        
        log.info("[API] Fetching pre-authorization by reference {}", referenceNumber);
        
        PreAuthorizationResponseDto response = preAuthorizationService.getPreAuthorizationByReference(referenceNumber);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ==================== GET BY MEMBER ====================

    /**
     * Get pre-authorizations by member
     * GET /api/pre-authorizations/member/{memberId}
     */
    @GetMapping("/member/{memberId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<PaginationResponse<PreAuthorizationResponseDto>> getPreAuthorizationsByMember(
            @PathVariable Long memberId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sortDirection", defaultValue = "DESC") String sortDirection) {
        
        log.info("[API] Fetching pre-authorizations for member {}", memberId);
        
        Sort.Direction direction = Sort.Direction.fromString(sortDirection);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        Page<PreAuthorizationResponseDto> preAuthsPage = preAuthorizationService.getPreAuthorizationsByMember(memberId, pageable);
        
        return ResponseEntity.ok(PaginationResponse.of(preAuthsPage));
    }

    // ==================== GET BY PROVIDER ====================

    /**
     * Get pre-authorizations by provider
     * GET /api/pre-authorizations/provider/{providerId}
     */
    @GetMapping("/provider/{providerId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<PaginationResponse<PreAuthorizationResponseDto>> getPreAuthorizationsByProvider(
            @PathVariable Long providerId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sortDirection", defaultValue = "DESC") String sortDirection) {
        
        log.info("[API] Fetching pre-authorizations for provider {}", providerId);
        
        Sort.Direction direction = Sort.Direction.fromString(sortDirection);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        Page<PreAuthorizationResponseDto> preAuthsPage = preAuthorizationService.getPreAuthorizationsByProvider(providerId, pageable);
        
        return ResponseEntity.ok(PaginationResponse.of(preAuthsPage));
    }

    // ==================== GET BY STATUS ====================

    /**
     * Get pre-authorizations by status
     * GET /api/pre-authorizations/status/{status}
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<PaginationResponse<PreAuthorizationResponseDto>> getPreAuthorizationsByStatus(
            @PathVariable String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "sortDirection", defaultValue = "DESC") String sortDirection) {
        
        log.info("[API] Fetching pre-authorizations with status {}", status);
        
        PreAuthStatus preAuthStatus = PreAuthStatus.valueOf(status.toUpperCase());
        Sort.Direction direction = Sort.Direction.fromString(sortDirection);
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        Page<PreAuthorizationResponseDto> preAuthsPage = preAuthorizationService.getPreAuthorizationsByStatus(preAuthStatus, pageable);
        
        return ResponseEntity.ok(PaginationResponse.of(preAuthsPage));
    }

    // ==================== FIND VALID FOR CLAIM ====================

    /**
     * Find valid pre-authorization for claim submission
     * GET /api/pre-authorizations/valid
     */
    @GetMapping("/valid")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<PreAuthorizationResponseDto>> findValidPreAuthorization(
            @RequestParam(name = "memberId") Long memberId,
            @RequestParam(name = "providerId") Long providerId,
            @RequestParam(name = "serviceCode") String serviceCode) {
        
        log.info("[API] Finding valid pre-authorization for member {}, provider {}, service {}", 
                 memberId, providerId, serviceCode);
        
        PreAuthorizationResponseDto response = preAuthorizationService.findValidPreAuthorization(
                memberId, providerId, serviceCode
        );
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ==================== MAINTENANCE ====================

    /**
     * Mark expired pre-authorizations (admin/scheduled task)
     * POST /api/pre-authorizations/maintenance/mark-expired
     */
    @PostMapping("/maintenance/mark-expired")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> markExpiredPreAuthorizations() {
        log.info("[API] Marking expired pre-authorizations");
        
        int count = preAuthorizationService.markExpiredPreAuthorizations();
        
        return ResponseEntity.ok(ApiResponse.success(count + " pre-authorizations marked as expired", Integer.valueOf(count)));
    }

    // ==================== START REVIEW ====================

    /**
     * Start review of a pre-authorization (PENDING → UNDER_REVIEW)
     * POST /api/pre-authorizations/{id}/start-review
     */
    @PostMapping("/{id:\\d+}/start-review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<PreAuthorizationResponseDto>> startReview(
            @PathVariable Long id,
            Authentication authentication) {
        
        log.info("[API] Starting review for pre-authorization {}", id);
        
        String reviewedBy = authentication != null ? authentication.getName() : "system";
        PreAuthorizationResponseDto response = preAuthorizationService.startReview(id, reviewedBy);
        
        return ResponseEntity.ok(ApiResponse.success("تم استلام طلب الموافقة المسبقة للمراجعة", response));
    }

    // ==================== CHECK VALIDITY ====================

    /**
     * Check if member has valid pre-authorization for service
     * GET /api/pre-authorizations/check-validity
     */
    @GetMapping("/check-validity")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<PreAuthorizationResponseDto>> checkValidity(
            @RequestParam(name = "memberId") Long memberId,
            @RequestParam(name = "serviceCode") String serviceCode) {
        
        log.info("[API] Checking validity for member {}, service {}", memberId, serviceCode);
        
        PreAuthorizationResponseDto response = preAuthorizationService.checkValidity(memberId, serviceCode);
        
        if (response != null) {
            return ResponseEntity.ok(ApiResponse.success("يوجد موافقة مسبقة صالحة", response));
        } else {
            return ResponseEntity.ok(ApiResponse.success("لا توجد موافقة مسبقة صالحة", null));
        }
    }

    // ==================== ATTACHMENTS ====================

    /**
     * Upload attachment to pre-authorization
     * POST /api/pre-authorizations/{id}/attachments
     */


    /**
     * Get all attachments for a pre-authorization
     * GET /api/pre-authorizations/{id}/attachments
     */
    @GetMapping("/{id:\\d+}/attachments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<List<PreAuthorizationAttachment>>> getAttachments(@PathVariable Long id) {
        log.info("[API] Getting attachments for pre-authorization {}", id);
        
        List<PreAuthorizationAttachment> attachments = attachmentService.getAttachments(id);
        return ResponseEntity.ok(ApiResponse.success("تم استرجاع المرفقات", attachments));
    }

    /**
     * Download specific attachment
     * GET /api/pre-authorizations/{id}/attachments/{attachmentId}
     */
    @GetMapping("/{id:\\d+}/attachments/{attachmentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
    public ResponseEntity<Resource> downloadAttachment(
            @PathVariable Long id,
            @PathVariable Long attachmentId) {
        
        log.info("[API] Downloading attachment {} from pre-authorization {}", attachmentId, id);
        
        try {
            PreAuthorizationAttachment attachment = attachmentService.getAttachment(attachmentId);
            byte[] fileContent = attachmentService.downloadAttachment(attachmentId);
            
            ByteArrayResource resource = new ByteArrayResource(fileContent);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(attachment.getFileType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                        FileResourceUtils.buildAttachmentContentDisposition(attachment.getOriginalFileName()))
                    .contentLength(fileContent.length)
                    .body(resource);
        } catch (RuntimeException e) {
            log.error("Failed to download attachment {}: {}", attachmentId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Delete attachment
     * DELETE /api/pre-authorizations/{id}/attachments/{attachmentId}
     */
    @DeleteMapping("/{id:\\d+}/attachments/{attachmentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF', 'DATA_ENTRY')")
    public ResponseEntity<ApiResponse<Void>> deleteAttachment(
            @PathVariable Long id,
            @PathVariable Long attachmentId) {
        
        log.info("[API] Deleting attachment {} from pre-authorization {}", attachmentId, id);
        
        try {
            attachmentService.deleteAttachment(attachmentId);
            return ResponseEntity.ok(ApiResponse.<Void>success("تم حذف المرفق بنجاح", null));
        } catch (RuntimeException e) {
            log.error("Failed to delete attachment {}: {}", attachmentId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("فشل في حذف المرفق: " + e.getMessage()));
        }
    }
}
