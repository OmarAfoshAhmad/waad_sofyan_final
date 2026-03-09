package com.waad.tba.modules.provider.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.provider.dto.EffectivePriceResponseDto;
import com.waad.tba.modules.provider.dto.ProviderClaimRequest;
import com.waad.tba.modules.provider.dto.ProviderClaimResponse;
import com.waad.tba.modules.provider.dto.ProviderEligibilityRequest;
import com.waad.tba.modules.provider.dto.ProviderEligibilityResponse;
import com.waad.tba.modules.provider.dto.ProviderVisitRegisterRequest;
import com.waad.tba.modules.provider.dto.ProviderVisitResponse;
import com.waad.tba.modules.provider.dto.ProviderServiceDto;
import com.waad.tba.modules.provider.service.ProviderClaimsService;
import com.waad.tba.modules.provider.service.ProviderContractService;
import com.waad.tba.modules.provider.service.ProviderPortalService;
import com.waad.tba.modules.provider.service.ProviderServiceService;
import com.waad.tba.modules.provider.service.ProviderVisitService;
import com.waad.tba.modules.providercontract.service.ProviderContractPricingItemService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.modules.visit.entity.VisitType;
import com.waad.tba.security.AuthorizationService;
import com.waad.tba.security.ProviderContextGuard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Provider Portal Controller.
 * 
 * Healthcare provider interface for:
 * - Real-time eligibility verification
 * - Member card scanning (QR Code / Manual entry)
 * - Coverage verification
 * - Claims submission (future)
 * - Pre-authorization requests (future)
 * 
 * @since Phase 1 - Provider Portal
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/provider")
@RequiredArgsConstructor
@Tag(name = "Provider Portal", description = "Healthcare provider interface for eligibility checks and service verification")
public class ProviderPortalController {
    
    private final ProviderPortalService providerPortalService;
    private final ProviderClaimsService providerClaimsService;
    private final ProviderVisitService providerVisitService;
    private final ProviderServiceService providerServiceService;
    private final ProviderContractService providerContractService;
    
    // NEW: Modern provider contract module service for my-contract endpoints
    @Qualifier("providerContractModuleService")
    private final com.waad.tba.modules.providercontract.service.ProviderContractService modernContractService;
    private final ProviderContractPricingItemService pricingItemService;
    
    // For medical categories (used in claims/pre-approval forms)
    private final com.waad.tba.modules.medicaltaxonomy.service.MedicalCategoryService medicalCategoryService;
    
    // For pre-approval services lookup
    private final com.waad.tba.modules.member.repository.MemberRepository memberRepository;
    private final com.waad.tba.modules.benefitpolicy.service.BenefitPolicyRuleService benefitPolicyRuleService;
    
    private final AuthorizationService authorizationService;
    private final ProviderContextGuard providerContextGuard;
    
    // Provider-Partner Isolation (Phase 5.5)
    private final com.waad.tba.modules.provider.service.ProviderService providerService;
    
    /**
     * Check Member Eligibility.
     * 
     * الفحص يتم فقط بـ:
     * - الباركود (WAD-2026-00001234)
     * - رقم البطاقة (Card Number)
     * 
     * ملاحظة: الرقم الوطني لا يُستخدم للفحص - يظهر فقط كمعلومات أساسية
     * 
     * Returns:
     * - Member information
     * - Eligibility status
     * - Coverage details
     * - Available annual limit
     * - Family members (if applicable)
     * 
     * <p><b>Provider Flow:</b></p>
     * 1. Scan member card / QR code OR enter barcode/card number
     * 2. System verifies eligibility in real-time
     * 3. Display member + all family members
     * 4. Provider selects patient from family
     * 5. Proceed with service/claim
     * 
     * POST /api/provider/eligibility-check
     */
    @PostMapping("/eligibility-check")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(
        summary = "Check member eligibility (Provider Portal)",
        description = "Real-time eligibility verification for healthcare providers. " +
                      "Supports barcode scan, QR code, or card number entry. " +
                      "Returns member info, coverage details, and family members."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Eligibility check successful",
        content = @Content(schema = @Schema(implementation = ProviderEligibilityResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Member not found"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Invalid request - barcode or card number required"
    )
    public ResponseEntity<ProviderEligibilityResponse> checkEligibility(
            @Valid @RequestBody ProviderEligibilityRequest request) {
        
        // ═══════════════════════════════════════════════════════════════════════════
        // PROVIDER SECURITY HARDENING (2026-01-16): Validate provider binding
        // ═══════════════════════════════════════════════════════════════════════════
        User currentUser = authorizationService.getCurrentUser();
        if (currentUser != null && authorizationService.isProvider(currentUser)) {
            providerContextGuard.validateProviderBinding(currentUser);
        }
        
        String provider = currentUser != null 
            ? currentUser.getUsername() 
            : "UNKNOWN";
        
        log.info("🏥 Provider eligibility check: provider={}, barcode={}", 
                 provider, 
                 request.getBarcode());
        
        ProviderEligibilityResponse response = providerPortalService.checkEligibility(request, provider);
        
        log.info("✅ Eligibility check completed: eligible={}, familySize={}, principal={}", 
                 response.getEligible(), 
                 response.getFamilyMembers().size(),
                 response.getPrincipalMember() != null ? response.getPrincipalMember().getFullName() : "N/A");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Quick Eligibility Check by Barcode (Simplified).
     * 
     * GET /api/provider/eligibility/{barcode}
     * 
     * Example: GET /api/provider/eligibility/WAD-2026-00001234
     */
    @GetMapping("/eligibility/{barcode}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(
        summary = "Quick eligibility check by barcode (GET)",
        description = "Simplified eligibility check using barcode only. " +
                      "Useful for QR code scanners that trigger GET requests."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Eligibility check successful"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Member not found"
    )
    public ResponseEntity<ProviderEligibilityResponse> checkEligibilityByBarcode(
            @PathVariable("barcode") String barcode) {
        
        String provider = authorizationService.getCurrentUser() != null 
            ? authorizationService.getCurrentUser().getUsername() 
            : "UNKNOWN";
        
        log.info("🏥 Provider eligibility check (GET): provider={}, barcode={}", provider, barcode);
        
        ProviderEligibilityRequest request = ProviderEligibilityRequest.builder()
            .barcode(barcode)
            .build();
        
        ProviderEligibilityResponse response = providerPortalService.checkEligibility(request, provider);
        
        return ResponseEntity.ok(response);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CLAIMS SUBMISSION
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Submit Claim (Provider Portal).
     * 
     * Healthcare provider submits claim for a member after service delivery.
     * 
     * Flow:
     * 1. Select member (from eligibility check results)
     * 2. Enter service details (type, date, amount)
     * 3. Upload attachments (invoices, medical reports)
     * 4. System validates annual limit & service limits
     * 5. Submit claim for review
     * 
     * <p><b>Validation:</b></p>
     * - Member must be active
     * - Claimed amount must not exceed annual limit
     * - Service-level limits checked (if applicable)
     * - Warnings shown if approaching 80% limit
     * 
     * POST /api/provider/claims/submit
     */
    @PostMapping("/claims/submit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(
        summary = "Submit claim (Provider Portal)",
        description = "Submit claim for member with automatic limit validation. " +
                      "Supports Cash Claims and Direct Billing. " +
                      "Returns detailed response with warnings if approaching limit."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Claim submitted successfully (may include warnings)",
        content = @Content(schema = @Schema(implementation = ProviderClaimResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Invalid request or exceeded limits"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Member not found"
    )
    public ResponseEntity<ProviderClaimResponse> submitClaim(
            @Valid @RequestBody ProviderClaimRequest request) {
        
        String provider = authorizationService.getCurrentUser() != null 
            ? authorizationService.getCurrentUser().getUsername() 
            : "UNKNOWN";
        
        log.info("🏥 Provider claim submission: provider={}, memberId={}, amount={}, type={}", 
                 provider, 
                 request.getMemberId(), 
                 request.getClaimedAmount(),
                 request.getClaimType());
        
        ProviderClaimResponse response = providerClaimsService.submitClaim(request, provider);
        
        if (response.getSuccess()) {
            log.info("✅ Claim submitted: claimId={}, ref={}, status={}", 
                     response.getClaimId(), 
                     response.getClaimReferenceNumber(),
                     response.getStatusCode());
        } else {
            log.warn("❌ Claim submission failed: member={}, reason={}", 
                     request.getMemberId(), 
                     response.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * ✅ PHASE 1: Submit Claim with File Attachments.
     * 
     * Accepts:
     * - Claim data (JSON) via @RequestPart
     * - Multiple file attachments (PDF, JPEG, PNG) via @RequestPart
     * 
     * File upload specifications:
     * - Max file size: 5 MB per file
     * - Max total size: 20 MB (4 files × 5 MB)
     * - Allowed types: PDF, JPEG, PNG
     * - Max files: 10
     * 
     * Transaction Handling:
     * - All operations are atomic (claim + files)
     * - If any file upload fails, entire claim is rolled back
     * - Ensures data consistency
     * 
     * POST /api/provider/submit-claim-with-attachments
     * Content-Type: multipart/form-data
     * 
     * Example Request:
     * --boundary
     * Content-Disposition: form-data; name="claim"
     * Content-Type: application/json
     * 
     * {
     *   "memberId": 123,
     *   "claimedAmount": 500.00,
     *   "claimType": "OUTPATIENT",
     *   ...
     * }
     * --boundary
     * Content-Disposition: form-data; name="files"; filename="invoice.pdf"
     * Content-Type: application/pdf
     * 
     * [binary data]
     * --boundary--
     * 
     * @param claimJson JSON string of ProviderClaimRequest
     * @param files Array of multipart files (can be empty for claims without attachments)
     * @return ProviderClaimResponse with claim ID and upload status
     */
    @PostMapping(value = "/submit-claim-with-attachments", 
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(
        summary = "Submit claim with file attachments (Provider Portal - Phase 1)",
        description = "Submit a claim with multiple file attachments (invoices, prescriptions, medical reports). " +
                      "Supports PDF, JPEG, PNG. Max 5 MB per file, 20 MB total. " +
                      "Transaction is atomic - if any file fails, claim is rolled back."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Claim and files uploaded successfully",
        content = @Content(schema = @Schema(implementation = ProviderClaimResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Invalid file type, size exceeded, or claim validation failed"
    )
    public ResponseEntity<ProviderClaimResponse> submitClaimWithAttachments(
            @RequestPart("claim") String claimJson,
            @RequestPart(value = "files", required = false) MultipartFile[] files) {
        
        String provider = authorizationService.getCurrentUser() != null 
            ? authorizationService.getCurrentUser().getUsername() 
            : "UNKNOWN";
        
        int fileCount = files != null ? files.length : 0;
        log.info("🏥 Provider claim submission with {} attachment(s): provider={}", 
                 fileCount, provider);
        
        try {
            // Delegate to service layer (handles JSON parsing, validation, file upload, and transaction)
            ProviderClaimResponse response = providerClaimsService.submitClaimWithAttachments(
                claimJson, files, provider);
            
            if (response.getSuccess()) {
                log.info("✅ Claim with attachments submitted: claimId={}, ref={}, files={}", 
                         response.getClaimId(), 
                         response.getClaimReferenceNumber(),
                         fileCount);
            } else {
                log.warn("❌ Claim with attachments failed: reason={}", 
                         response.getMessage());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("❌ Error submitting claim with attachments", e);
            
            ProviderClaimResponse errorResponse = ProviderClaimResponse.builder()
                .success(false)
                .message("خطأ في رفع المرفقات: " + e.getMessage())
                .build();
            
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // VISIT REGISTRATION & VISIT LOG (NEW FLOW 2026-01-13)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Register Visit (Provider Portal).
     * 
     * NEW FLOW:
     * 1. Provider performs eligibility check
     * 2. Selects eligible member from results
     * 3. Clicks "Register Visit" → this endpoint is called
     * 4. Visit is created and linked to member
     * 5. Provider can then create Claim or Pre-Authorization from Visit Log
     * 
     * POST /api/provider/visits/register
     */
    @PostMapping("/visits/register")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(
        summary = "Register visit (Provider Portal)",
        description = "Register a new visit for a member after eligibility check. " +
                      "Creates visit linked to member. Use visitId for subsequent " +
                      "claim or pre-authorization creation."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Visit registered successfully",
        content = @Content(schema = @Schema(implementation = ProviderVisitResponse.class))
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Invalid request or member not eligible"
    )
    public ResponseEntity<ProviderVisitResponse> registerVisit(
            @Valid @RequestBody ProviderVisitRegisterRequest request) {
        
        String provider = authorizationService.getCurrentUser() != null 
            ? authorizationService.getCurrentUser().getUsername() 
            : "UNKNOWN";
        
        log.info("📋 Provider visit registration: provider={}, memberId={}", 
                 provider, request.getMemberId());
        
        ProviderVisitResponse response = providerVisitService.registerVisit(request, provider);
        
        if (response.getSuccess()) {
            log.info("✅ Visit registered: visitId={}, member={}", 
                     response.getVisitId(), response.getMemberName());
        } else {
            log.warn("❌ Visit registration failed: member={}, reason={}", 
                     request.getMemberId(), response.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get Visit Log (Provider Portal).
     * 
     * Returns paginated list of visits with optional filters.
     * Each visit shows available actions (Create Claim / Create Pre-Auth).
     * 
     * GET /api/provider/visits
     */
    @GetMapping("/visits")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF', 'MEDICAL_REVIEWER')")
    @Operation(
        summary = "Get visit log (Provider Portal)",
        description = "Returns paginated list of visits with filters. " +
                      "Each visit shows canCreateClaim and canCreatePreAuth flags."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Visit log retrieved successfully"
    )
    public ResponseEntity<Page<ProviderVisitResponse>> getVisitLog(
            @Parameter(description = "Member ID filter") 
            @RequestParam(name = "memberId", required = false) Long memberId,
            @Parameter(description = "Member name/card number/civil ID search") 
            @RequestParam(name = "memberName", required = false) String memberName,
            @Parameter(description = "Status filter (REGISTERED, IN_PROGRESS, CLAIM_SUBMITTED, etc.)") 
            @RequestParam(name = "status", required = false) String status,
            @Parameter(description = "Visit type filter (OUTPATIENT, INPATIENT, ...)")
            @RequestParam(name = "visitType", required = false) String visitType,
            @Parameter(description = "From date filter (YYYY-MM-DD)") 
            @RequestParam(name = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "To date filter (YYYY-MM-DD)") 
            @RequestParam(name = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "Page number (0-based)") 
            @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Page size") 
            @RequestParam(name = "size", defaultValue = "10") int size,
            @Parameter(description = "Sort field") 
            @RequestParam(name = "sortBy", defaultValue = "visitDate") String sortBy,
            @Parameter(description = "Sort direction (asc/desc)") 
            @RequestParam(name = "sortDir", defaultValue = "desc") String sortDir) {
        
        String providerUsername = authorizationService.getCurrentUser() != null 
            ? authorizationService.getCurrentUser().getUsername() 
            : "UNKNOWN";
        
        // ════════════════════════════════════════════════════════════════════════
        // PROVIDER ISOLATION: Use ProviderContextGuard to get provider filter
        // - PROVIDER role: Only sees their own visits
        // - ADMIN roles: See all visits (filter = null)
        // ════════════════════════════════════════════════════════════════════════
        Long providerId = providerContextGuard.getProviderFilter();
        
        VisitType visitTypeEnum = null;
        if (visitType != null && !visitType.trim().isEmpty()) {
            try {
                visitTypeEnum = VisitType.valueOf(visitType.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                log.warn("Invalid visitType filter '{}' ignored for provider visit log", visitType);
            }
        }

        log.debug("📋 Provider visit log request: user={}, providerId={}, member={}, memberName={}, status={}, visitType={}, fromDate={}, toDate={}", 
                  providerUsername, providerId, memberId, memberName, status, visitTypeEnum, fromDate, toDate);
        
        Sort sort = sortDir.equalsIgnoreCase("desc") 
            ? Sort.by(sortBy).descending() 
            : Sort.by(sortBy).ascending();
        
        PageRequest pageable = PageRequest.of(page, size, sort);
        
        Page<ProviderVisitResponse> visits = providerVisitService.getVisitLog(
            providerId, memberId, memberName, status, visitTypeEnum, fromDate, toDate, pageable);
        
        return ResponseEntity.ok(visits);
    }
    
    /**
     * Get Visit Details (Provider Portal).
     * 
     * GET /api/provider/visits/{id}
     */
    @GetMapping("/visits/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF', 'MEDICAL_REVIEWER')")
    @Operation(
        summary = "Get visit details (Provider Portal)",
        description = "Returns detailed visit information including member, " +
                      "provider, and available actions."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Visit details retrieved successfully"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Visit not found"
    )
    public ResponseEntity<ProviderVisitResponse> getVisitById(@PathVariable("id") Long id) {
        
        log.debug("📋 Provider visit details request: visitId={}", id);
        
        ProviderVisitResponse visit = providerVisitService.getVisitById(id);
        
        if (!visit.getSuccess()) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(visit);
    }
    
    /**
     * ═══════════════════════════════════════════════════════════════════════════
     * GET VISIT CONTEXT (Decision Payload)
     * ═══════════════════════════════════════════════════════════════════════════
     * 
     * This is the CANONICAL way to determine what to show for a visit.
     * Backend decides - Frontend just follows.
     * 
     * GET /api/provider/visits/{id}/context
     * 
     * Returns:
     * - hasClaim: boolean
     * - claimId: Long (if hasClaim)
     * - claimStatus: String (if hasClaim)
     * - hasPreAuthorization: boolean
     * - preAuthorizationId: Long (if hasPreAuthorization)
     * - preAuthorizationStatus: String (if hasPreAuthorization)
     * - eligibilityOnly: boolean (if neither claim nor preAuth exists)
     */
    @GetMapping("/visits/{id}/context")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF', 'MEDICAL_REVIEWER')")
    @Operation(
        summary = "Get visit context for navigation (Provider Portal)",
        description = "Returns the decision payload to determine where to navigate. " +
                      "Backend decides based on what exists (claim, pre-auth, or just eligibility)."
    )
    public ResponseEntity<ApiResponse<VisitContextDto>> getVisitContext(@PathVariable("id") Long id) {
        
        log.info("📋 [VISIT-CONTEXT] Request for visitId={}", id);
        
        ProviderVisitResponse visit = providerVisitService.getVisitById(id);
        
        if (!visit.getSuccess()) {
            return ResponseEntity.notFound().build();
        }
        
        // Build context response based on what exists
        VisitContextDto context = VisitContextDto.builder()
            .visitId(id)
            .visitDate(visit.getVisitDate())
            .visitType(visit.getVisitType())
            .memberId(visit.getMemberId())
            .memberName(visit.getMemberName())
            .providerId(visit.getProviderId())
            .providerName(visit.getProviderName())
            // Claim info
            .hasClaim(visit.getLatestClaimId() != null)
            .claimId(visit.getLatestClaimId())
            .claimStatus(visit.getLatestClaimStatus())
            .claimStatusLabel(visit.getLatestClaimStatusLabel())
            .claimCount(visit.getClaimCount() != null ? visit.getClaimCount() : 0)
            // PreAuth info
            .hasPreAuthorization(visit.getLatestPreAuthId() != null)
            .preAuthorizationId(visit.getLatestPreAuthId())
            .preAuthorizationStatus(visit.getLatestPreAuthStatus())
            .preAuthorizationStatusLabel(visit.getLatestPreAuthStatusLabel())
            .preAuthCount(visit.getPreAuthCount() != null ? visit.getPreAuthCount() : 0)
            // Eligibility only if neither exists
            .eligibilityOnly(visit.getLatestClaimId() == null && visit.getLatestPreAuthId() == null)
            // Actions available
            .canCreateClaim(visit.getCanCreateClaim() != null ? visit.getCanCreateClaim() : true)
            .canCreatePreAuth(visit.getCanCreatePreAuth() != null ? visit.getCanCreatePreAuth() : true)
            .build();
        
        log.info("📋 [VISIT-CONTEXT] Response: hasClaim={}, hasPreAuth={}, eligibilityOnly={}", 
                 context.isHasClaim(), context.isHasPreAuthorization(), context.isEligibilityOnly());
        
        return ResponseEntity.ok(ApiResponse.success("Visit context retrieved", context));
    }
    
    /**
     * DTO for Visit Context (Decision Payload)
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VisitContextDto {
        private Long visitId;
        private LocalDate visitDate;
        private String visitType;
        
        // Member info
        private Long memberId;
        private String memberName;
        
        // Provider info
        private Long providerId;
        private String providerName;
        
        // Claim context
        private boolean hasClaim;
        private Long claimId;
        private String claimStatus;
        private String claimStatusLabel;
        private int claimCount;
        
        // Pre-Authorization context
        private boolean hasPreAuthorization;
        private Long preAuthorizationId;
        private String preAuthorizationStatus;
        private String preAuthorizationStatusLabel;
        private int preAuthCount;
        
        // Navigation decision
        private boolean eligibilityOnly;
        
        // Available actions
        private boolean canCreateClaim;
        private boolean canCreatePreAuth;
    }
    
    // ════════════════════════════════════════════════════════════════════════════
    // PROVIDER MEDICAL SERVICES (For Claims & Pre-Auth)
    // ════════════════════════════════════════════════════════════════════════════
    
    /**
     * Get Medical Services Available to Provider.
     * 
     * Returns the list of medical services that this provider is contracted to offer.
     * Used by Claims and Pre-Authorization forms to populate service dropdowns.
     * 
     * SECURITY: Provider gets their own services, Admin can specify providerId.
     * 
     * GET /api/provider/my-services
     */
    @GetMapping("/my-services")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(
        summary = "Get provider's contracted services (Provider Portal)",
        description = "Returns medical services available for this provider to use in claims and pre-authorizations."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Services retrieved successfully"
    )
    public ResponseEntity<ApiResponse<List<ProviderServiceDto>>> getMyServices() {
        
        // ════════════════════════════════════════════════════════════════════════
        // PROVIDER ISOLATION: Get services for the current provider only
        // ════════════════════════════════════════════════════════════════════════
        Long providerId = providerContextGuard.getProviderFilter();
        
        if (providerId == null) {
            // Admin without provider binding - return empty list
            return ResponseEntity.ok(ApiResponse.success("No provider bound", List.of()));
        }
        
        log.debug("📋 Provider services request: providerId={}", providerId);
        
        List<com.waad.tba.modules.provider.dto.ProviderServiceResponseDto> services = 
            providerServiceService.getProviderServices(providerId);
        
        // Map to simplified DTO for frontend
        List<ProviderServiceDto> result = services.stream()
            .map(s -> ProviderServiceDto.builder()
                .serviceId(s.getId())
                .serviceCode(s.getServiceCode())
                .serviceName(s.getServiceName())
                .categoryCode(s.getCategoryCode())
                .categoryName(s.getCategoryName())
                .requiresPA(false) // PA requirement comes from BenefitPolicyRule, not MedicalService
                .build())
            .collect(java.util.stream.Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    /**
     * Get effective price for a service (Provider Portal).
     * 
     * This endpoint allows PROVIDER role users to fetch the contract price
     * for a specific service. SECURITY: Provider can only access their own prices.
     * 
     * GET /api/provider/my-services/{serviceCode}/price
     * 
     * @param serviceCode The medical service code
     * @param date Optional effective date (defaults to today)
     * @return Contract price information
     */
    @GetMapping("/my-services/{serviceCode}/price")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(
        summary = "Get service price for provider (Provider Portal)",
        description = "Returns the effective contract price for a specific service. " +
                      "Provider users can only access their own contract prices."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Price retrieved successfully"
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "404",
        description = "Service or contract not found"
    )
    public ResponseEntity<ApiResponse<EffectivePriceResponseDto>> getServicePrice(
            @PathVariable("serviceCode") String serviceCode,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        
        // ════════════════════════════════════════════════════════════════════════
        // PROVIDER ISOLATION: Get provider ID from security context
        // ════════════════════════════════════════════════════════════════════════
        Long providerId = providerContextGuard.getProviderFilter();
        
        if (providerId == null) {
            // Admin without provider binding - return error
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("No provider bound to current user"));
        }
        
        log.info("[PROVIDER-PORTAL] GET /api/provider/my-services/{}/price, providerId={}, date={}", 
                serviceCode, providerId, date);
        
        try {
            EffectivePriceResponseDto priceResponse = providerContractService.getEffectivePrice(
                providerId, serviceCode, date);
            
            return ResponseEntity.ok(ApiResponse.success(priceResponse));
        } catch (Exception e) {
            log.warn("[PROVIDER-PORTAL] Price lookup failed: {}", e.getMessage());
            // Return graceful fallback instead of 500
            EffectivePriceResponseDto fallback = EffectivePriceResponseDto.builder()
                .providerId(providerId)
                .serviceCode(serviceCode)
                .hasContract(false)
                .message("Unable to retrieve price: " + e.getMessage())
                .build();
            return ResponseEntity.ok(ApiResponse.success(fallback));
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PROVIDER-PARTNER ISOLATION (Phase 5.5)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Get list of allowed employers (partners) for the current provider.
     * 
     * PROVIDER ISOLATION RULE:
     * - PROVIDER users can ONLY see their assigned employers/partners
     * - NO global employer selector access
     * - Used by Provider Portal to filter members, claims, visits by partner
     * 
     * SECURITY:
     * - providerId extracted from auth context (cannot be tampered)
     * - Backend validates Provider-Partner relationships
     * - Returns empty list if provider has NO partnerships
     * 
     * GET /api/v1/provider/allowed-employers
     * 
     * @return List of allowed employers/partners
     */
    @GetMapping("/allowed-employers")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(
        summary = "Get allowed employers for current provider",
        description = "Returns list of employers/partners that this provider can access. " +
                      "Used for Provider Portal scope filtering."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Allowed employers retrieved successfully"
    )
    public ResponseEntity<ApiResponse<List<com.waad.tba.modules.provider.dto.AllowedEmployerDto>>> getAllowedEmployers() {
        Long providerId = providerContextGuard.getProviderFilter();
        
        if (providerId == null) {
            // Admin without provider binding - return empty list
            return ResponseEntity.ok(ApiResponse.success("No provider bound to current user", List.of()));
        }
        
        log.info("[PROVIDER-PORTAL] GET /api/v1/provider/allowed-employers, providerId={}", providerId);
        
        try {
            List<com.waad.tba.modules.provider.dto.AllowedEmployerDto> allowedEmployers = 
                providerService.getAllowedEmployers(providerId);
            
            log.debug("Provider {} has {} allowed employers", providerId, allowedEmployers.size());
            
            return ResponseEntity.ok(ApiResponse.success(
                "Allowed employers retrieved successfully",
                allowedEmployers
            ));
        } catch (Exception e) {
            log.error("[PROVIDER-PORTAL] Error fetching allowed employers: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.error("Unable to fetch allowed employers: " + e.getMessage()));
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MY CONTRACT ENDPOINTS (PROVIDER SELF-ACCESS)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Get the active contract for the current PROVIDER user.
     * 
     * SECURITY: Provider can only access their own contract (via ProviderContextGuard).
     * 
     * GET /api/provider/my-contract
     * 
     * @return Active contract details or null if no active contract
     */
    @GetMapping("/my-contract")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(
        summary = "Get my active contract (Provider Portal)",
        description = "Returns the active contract for the current provider user. " +
                      "Used by pre-approval and claims forms to fetch contract-based pricing."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Contract retrieved successfully (may be null if no active contract)"
    )
    public ResponseEntity<ApiResponse<MyContractResponseDto>> getMyActiveContract() {
        Long providerId = providerContextGuard.getProviderFilter();
        
        if (providerId == null) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("No provider bound to current user"));
        }
        
        log.info("[PROVIDER-PORTAL] GET /api/provider/my-contract, providerId={}", providerId);
        
        try {
            // Use the MODERN ProviderContractService (from providercontract module)
            com.waad.tba.modules.providercontract.dto.ProviderContractResponseDto activeContract = 
                modernContractService.findActiveByProvider(providerId);
            
            if (activeContract == null) {
                return ResponseEntity.ok(ApiResponse.success(
                    "No active contract found",
                    MyContractResponseDto.builder()
                        .providerId(providerId)
                        .hasActiveContract(false)
                        .build()
                ));
            }
            
            // Get pricing items count from the DTO (already calculated)
            long totalServices = activeContract.getPricingItemsCount() != null ? 
                activeContract.getPricingItemsCount() : 0L;
            
            // Get provider name from embedded provider summary
            String providerName = activeContract.getProvider() != null ? 
                activeContract.getProvider().getName() : null;
            
            MyContractResponseDto response = MyContractResponseDto.builder()
                .id(activeContract.getId())
                .providerId(providerId)
                .providerName(providerName)
                .effectiveFrom(activeContract.getStartDate())
                .effectiveTo(activeContract.getEndDate())
                .hasActiveContract(true)
                .totalServices(totalServices)
                .build();
            
            return ResponseEntity.ok(ApiResponse.success("Active contract found", response));
        } catch (Exception e) {
            log.error("[PROVIDER-PORTAL] Error fetching my contract: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.success(
                "Unable to fetch contract",
                MyContractResponseDto.builder()
                    .providerId(providerId)
                    .hasActiveContract(false)
                    .errorMessage(e.getMessage())
                    .build()
            ));
        }
    }
    
    /**
     * Get all pricing items (services with prices) for the current PROVIDER's active contract.
     * 
     * SECURITY: Provider can only access their own contract pricing (via ProviderContextGuard).
     * 
     * GET /api/provider/my-contract/services
     * 
     * @param page Page number (0-based)
     * @param size Page size (default 200)
     * @return Paginated list of services with contract prices
     */
    @GetMapping("/my-contract/services")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF', 'PROVIDER_ADMIN')")
    @Operation(
        summary = "Get my contract services with pricing (Provider Portal)",
        description = "Returns all services available in the provider's active contract with prices. " +
                      "Used by pre-approval form to populate service dropdown."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Services retrieved successfully"
    )
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<MyContractServiceDto>>> getMyContractServices(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "200") int size) {
        
        Long providerId = providerContextGuard.getProviderFilter();
        
        if (providerId == null) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("No provider bound to current user"));
        }
        
        log.info("[PROVIDER-PORTAL] GET /api/provider/my-contract/services, providerId={}, page={}, size={}", 
                providerId, page, size);
        
        try {
            // Use MODERN ProviderContractService to get active contract
            com.waad.tba.modules.providercontract.dto.ProviderContractResponseDto activeContract = 
                modernContractService.findActiveByProvider(providerId);
            
            if (activeContract == null) {
                log.warn("[PROVIDER-PORTAL] No active contract found for provider {}", providerId);
                return ResponseEntity.ok(ApiResponse.success(
                    "No active contract found",
                    org.springframework.data.domain.Page.empty()
                ));
            }
            
            log.info("[PROVIDER-PORTAL] Found active contract {} for provider {}", activeContract.getId(), providerId);
            
            // Use pricingItemService to get paginated services from MODERN tables
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
            org.springframework.data.domain.Page<com.waad.tba.modules.providercontract.dto.ProviderContractPricingItemResponseDto> pricingItems = 
                pricingItemService.findByContract(activeContract.getId(), pageable);
            
            log.info("[PROVIDER-PORTAL] Found {} pricing items for contract {}", pricingItems.getTotalElements(), activeContract.getId());
            
            // Map pricing items to our simplified DTO using stream and PageImpl
            java.util.List<MyContractServiceDto> mappedItems = pricingItems.getContent().stream()
                .map(item -> {
                    // Get service info from pricing item
                    String serviceCode = item.getServiceCode();
                    String serviceName = item.getServiceName();
                    String categoryName = item.getCategoryName();
                    Long medicalServiceId = null;  // Initialize to null
                    Long medicalCategoryId = null;
                    String categoryCode = null;
                    
                    // Try to get from medicalService if available
                    if (item.getMedicalService() != null) {
                        medicalServiceId = item.getMedicalService().getId();  // CRITICAL: Use MedicalService ID
                        serviceCode = item.getMedicalService().getCode();
                        serviceName = item.getMedicalService().getName();
                    }
                    
                    // Get category info
                    if (item.getEffectiveCategory() != null) {
                        medicalCategoryId = item.getEffectiveCategory().getId();
                        categoryCode = item.getEffectiveCategory().getCode();
                        categoryName = item.getEffectiveCategory().getName();
                    } else if (item.getMedicalCategory() != null) {
                        medicalCategoryId = item.getMedicalCategory().getId();
                        categoryCode = item.getMedicalCategory().getCode();
                        categoryName = item.getMedicalCategory().getName();
                    }
                    
                    return MyContractServiceDto.builder()
                        .id(item.getId())
                        .medicalServiceId(medicalServiceId)  // CRITICAL: Include MedicalService ID
                        .serviceCode(serviceCode)
                        .serviceName(serviceName)
                        .medicalCategoryId(medicalCategoryId)
                        .categoryCode(categoryCode)
                        .categoryName(categoryName)
                        .contractPrice(item.getContractPrice())
                        .currency(item.getCurrency())
                        .effectiveFrom(item.getEffectiveFrom())
                        .effectiveTo(item.getEffectiveTo())
                        .hasContract(true)
                        .requiresPreApproval(null)  // Will be set per-member in frontend if needed
                        .build();
                })
                .collect(java.util.stream.Collectors.toList());
            
            org.springframework.data.domain.Page<MyContractServiceDto> resultPage = 
                new org.springframework.data.domain.PageImpl<>(
                    mappedItems,
                    pageable,
                    pricingItems.getTotalElements()
                );
            
            return ResponseEntity.ok(ApiResponse.success("Contract services retrieved", resultPage));
        } catch (Exception e) {
            log.error("[PROVIDER-PORTAL] Error fetching my contract services: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.success(
                "Unable to fetch services",
                org.springframework.data.domain.Page.empty()
            ));
        }
    }
    
    /**
     * Get services requiring pre-approval from the provider's active contract.
     * 
     * This endpoint returns ONLY services that:
     * 1. Are in the provider's active contract (with contract pricing)
     * 2. Require pre-approval based on the MEMBER's benefit policy rules
     * 
     * SECURITY: Provider can only access their own contract services.
     * 
     * GET /api/provider/my-contract/services/requiring-preauth
     * 
     * @param memberId The member ID to check benefit policy rules for
     * @param page Page number (0-based)
     * @param size Page size
     * @return Services requiring pre-approval with contract prices
     */
    @GetMapping("/my-contract/services/requiring-preauth")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(
        summary = "Get services requiring pre-approval (Provider Portal)",
        description = "Returns contract services that require pre-approval based on member's benefit policy"
    )
    public ResponseEntity<ApiResponse<java.util.List<MyContractServiceDto>>> getServicesRequiringPreAuth(
            @RequestParam(name = "memberId") Long memberId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "500") int size) {
        
        Long providerId = providerContextGuard.getProviderFilter();
        
        if (providerId == null) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("No provider bound to current user"));
        }
        
        log.info("[PROVIDER-PORTAL] GET /api/provider/my-contract/services/requiring-preauth, providerId={}, memberId={}", 
                providerId, memberId);
        
        try {
            // 1. Get active contract
            com.waad.tba.modules.providercontract.dto.ProviderContractResponseDto activeContract = 
                modernContractService.findActiveByProvider(providerId);
            
            if (activeContract == null) {
                log.warn("[PROVIDER-PORTAL] No active contract found for provider {}", providerId);
                return ResponseEntity.ok(ApiResponse.success(
                    "No active contract found",
                    java.util.Collections.emptyList()
                ));
            }
            
            // 2. Get member's benefit policy
            com.waad.tba.modules.member.entity.Member member = memberRepository.findById(memberId)
                .orElse(null);
            
            if (member == null || member.getBenefitPolicy() == null) {
                log.warn("[PROVIDER-PORTAL] Member {} not found or has no benefit policy", memberId);
                return ResponseEntity.ok(ApiResponse.success(
                    "Member has no benefit policy",
                    java.util.Collections.emptyList()
                ));
            }
            
            Long policyId = member.getBenefitPolicy().getId();
            
            // 3. Get all pricing items from contract
            java.util.List<com.waad.tba.modules.providercontract.dto.ProviderContractPricingItemResponseDto> allPricingItems = 
                pricingItemService.findByContract(activeContract.getId());
            
            // 4. Filter only services that require pre-approval from benefit policy
            java.util.List<MyContractServiceDto> servicesRequiringPA = allPricingItems.stream()
                .filter(item -> {
                    Long serviceId = null;
                    if (item.getMedicalService() != null) {
                        serviceId = item.getMedicalService().getId();
                    }
                    if (serviceId == null) return false;
                    
                    // Check if this service requires pre-approval in the member's policy
                    return benefitPolicyRuleService.requiresPreApproval(policyId, serviceId, null);
                })
                .map(item -> {
                    String serviceCode = item.getServiceCode();
                    String serviceName = item.getServiceName();
                    String categoryName = item.getCategoryName();
                    Long medicalServiceId = null;
                    
                    if (item.getMedicalService() != null) {
                        medicalServiceId = item.getMedicalService().getId();
                        serviceCode = item.getMedicalService().getCode();
                        serviceName = item.getMedicalService().getName();
                    }
                    
                    if (item.getEffectiveCategory() != null) {
                        categoryName = item.getEffectiveCategory().getName();
                    } else if (item.getMedicalCategory() != null) {
                        categoryName = item.getMedicalCategory().getName();
                    }
                    
                    return MyContractServiceDto.builder()
                        .id(item.getId())
                        .medicalServiceId(medicalServiceId)
                        .serviceCode(serviceCode)
                        .serviceName(serviceName)
                        .categoryName(categoryName)
                        .contractPrice(item.getContractPrice())
                        .currency(item.getCurrency())
                        .effectiveFrom(item.getEffectiveFrom())
                        .effectiveTo(item.getEffectiveTo())
                        .hasContract(true)
                        .requiresPreAuth(true)  // All items here require pre-auth
                        .build();
                })
                .collect(java.util.stream.Collectors.toList());
            
            log.info("[PROVIDER-PORTAL] Found {} services requiring pre-approval for member {} in contract {}", 
                    servicesRequiringPA.size(), memberId, activeContract.getId());
            
            return ResponseEntity.ok(ApiResponse.success(
                "Services requiring pre-approval retrieved", 
                servicesRequiringPA
            ));
        } catch (Exception e) {
            log.error("[PROVIDER-PORTAL] Error fetching services requiring pre-auth: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponse.success(
                "Unable to fetch services",
                java.util.Collections.emptyList()
            ));
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // DTO CLASSES FOR MY CONTRACT ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Response DTO for my-contract endpoint
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MyContractResponseDto {
        private Long id;
        private Long providerId;
        private String providerName;
        private LocalDate effectiveFrom;
        private LocalDate effectiveTo;
        private Boolean hasActiveContract;
        private Long totalServices;
        private String errorMessage;
    }
    
    /**
     * DTO for services in my contract
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MyContractServiceDto {
        private Long id;  // Pricing Item ID
        private Long medicalServiceId;  // Medical Service ID - IMPORTANT for claim creation
        private String serviceCode;
        private String serviceName;
        private String serviceNameAr;
        private Long medicalCategoryId;
        private String categoryCode;
        private String categoryName;
        private java.math.BigDecimal contractPrice;
        private String currency;
        private LocalDate effectiveFrom;
        private LocalDate effectiveTo;
        private Boolean hasContract;
        
        /**
         * @deprecated Use requiresPreApproval instead
         */
        @Deprecated
        private Boolean requiresPreAuth;  // Legacy name
        
        /**
         * CANONICAL: Does this service require pre-approval based on Member's BenefitPolicy?
         * Set to null in general listings, computed per-member in specific endpoints
         */
        private Boolean requiresPreApproval;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // MEDICAL CATEGORIES (FOR PROVIDER CLAIMS/PRE-APPROVAL FORMS)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Get all active medical categories for provider forms.
     * 
     * SECURITY: Available to authenticated PROVIDER users for form dropdowns.
     * 
     * GET /api/provider/medical-categories
     * 
     * @return List of active medical categories
     */
    @GetMapping("/medical-categories")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF', 'PROVIDER_ADMIN')")
    @Operation(
        summary = "Get medical categories for provider forms",
        description = "Returns all active medical categories for use in claims and pre-approval forms."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "Categories retrieved successfully"
    )
    public ResponseEntity<ApiResponse<List<com.waad.tba.modules.medicaltaxonomy.dto.MedicalCategoryResponseDto>>> getProviderMedicalCategories() {
        log.info("[PROVIDER-PORTAL] GET /api/provider/medical-categories");
        
        List<com.waad.tba.modules.medicaltaxonomy.dto.MedicalCategoryResponseDto> categories = 
            medicalCategoryService.findAllList();
        
        return ResponseEntity.ok(ApiResponse.success(categories));
    }

    /**
     * Generate PDF for a specific visit.
     * 
     * SECURITY: Provider can only access their own visits.
     * 
     * GET /api/v1/provider/visits/{visitId}/pdf
     * 
     * @param visitId Visit ID
     * @return PDF file (application/pdf) with Content-Disposition: inline
     */
    @GetMapping("/visits/{visitId}/pdf")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(
        summary = "Generate visit PDF",
        description = "Generate a PDF report for a specific visit. Provider can only access their own visits."
    )
    public ResponseEntity<byte[]> getVisitPdf(@PathVariable("visitId") Long visitId) {
        log.info("[PROVIDER-PORTAL] GET /api/provider/visits/{}/pdf", visitId);
        
        Long providerId = providerContextGuard.getRequiredProviderId();
        
        byte[] pdfBytes = providerVisitService.generateVisitPdf(visitId, providerId);
        
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", "visit-" + visitId + ".pdf");
        headers.setContentLength(pdfBytes.length);
        
        return ResponseEntity.ok()
            .headers(headers)
            .body(pdfBytes);
    }
}

