package com.waad.tba.modules.member.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.member.dto.EligibilityResultDto;
import com.waad.tba.modules.member.dto.MemberFinancialSummaryDto;
import com.waad.tba.modules.member.exception.InvalidEligibilityInputException;
import com.waad.tba.modules.member.exception.MemberNotFoundException;
import com.waad.tba.modules.member.service.MemberFinancialSummaryService;
import com.waad.tba.modules.member.service.UnifiedEligibilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Unified Eligibility Check Controller
 * Single endpoint for deterministic eligibility verification
 * 
 * Supported Methods:
 * - Card Number (digits only)
 * - Barcode (WAD-YYYY-NNNNNNNN format)
 * 
 * NOT Supported:
 * - Name search (removed by architectural decision)
 * - Fuzzy search
 * - Multiple results
 * 
 * @version 2.0 - Refactored for deterministic behavior
 * @since 2026-01-10
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
@Tag(name = "Eligibility Check", description = "Unified eligibility verification endpoints")
public class UnifiedEligibilityController {

    private final UnifiedEligibilityService eligibilityService;
    private final MemberFinancialSummaryService financialSummaryService;

    // ==================== REMAINING LIMIT ====================
    
    /**
     * Get Member Remaining Limit
     * 
     * GET /api/members/{memberId}/remaining-limit
     * 
     * Simple endpoint for Provider Portal to show remaining limit during claim creation.
     */
    @GetMapping("/{memberId}/remaining-limit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(
        summary = "Get Member Remaining Limit",
        description = "Returns the remaining coverage limit for a member. Used in Provider Portal during claim creation."
    )
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getRemainingLimit(
            @PathVariable Long memberId) {
        
        log.info("📊 Retrieving remaining limit for member: memberId={}", memberId);
        
        try {
            MemberFinancialSummaryDto summary = financialSummaryService.getFinancialSummary(memberId);
            
            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("memberId", memberId);
            result.put("memberName", summary.getFullName());
            result.put("annualLimit", summary.getAnnualLimit());
            result.put("usedAmount", summary.getTotalApproved());
            result.put("remainingLimit", summary.getRemainingCoverage());
            result.put("usagePercentage", summary.getUtilizationPercent());
            result.put("policyName", summary.getPolicyName());
            result.put("policyActive", summary.getPolicyActive());
            
            log.info("✅ Remaining limit retrieved: memberId={}, remaining={}", 
                     memberId, summary.getRemainingCoverage());
            
            return ResponseEntity.ok(ApiResponse.success(result));
        } catch (Exception e) {
            log.warn("⚠️ Failed to get remaining limit for member {}: {}", memberId, e.getMessage());
            java.util.Map<String, Object> fallback = new java.util.HashMap<>();
            fallback.put("memberId", memberId);
            fallback.put("annualLimit", 0);
            fallback.put("usedAmount", 0);
            fallback.put("remainingLimit", 0);
            return ResponseEntity.ok(ApiResponse.success(fallback));
        }
    }

    /**
     * Get Member Service Coverage Limits
     * 
     * GET /api/v1/members/{memberId}/service-coverage?serviceCode=...
     * 
     * Returns detailed coverage percentages and limits (amount and times) for a specific service.
     */
    @GetMapping("/{memberId}/service-coverage")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF')")
    @Operation(
        summary = "Get Member Service Coverage",
        description = "Returns coverage details and calculated limits for a specific service against a member's active policy."
    )
    public ResponseEntity<ApiResponse<com.waad.tba.modules.member.dto.CoverageLimitsDto>> getServiceCoverage(
            @PathVariable Long memberId,
            @RequestParam(name = "serviceCode") String serviceCode) {
        
        log.info("📊 Retrieving service coverage limits: memberId={}, serviceCode={}", memberId, serviceCode);
        
        try {
            com.waad.tba.modules.member.dto.CoverageLimitsDto limits = financialSummaryService.getServiceCoverageLimits(memberId, serviceCode);
            return ResponseEntity.ok(ApiResponse.success(limits));
        } catch (com.waad.tba.common.exception.BusinessRuleException e) {
            log.warn("⚠️ Failed to get service coverage limits: memberId={}, serviceCode={}, msg={}", memberId, serviceCode, e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("💥 Error retrieving service coverage limits", e);
            return ResponseEntity.internalServerError().body(ApiResponse.error("Internal Server Error"));
        }
    }

    // ==================== ELIGIBILITY CHECK ====================

    /**
     * Check member eligibility with automatic input detection
     * 
     * Endpoint: GET /api/members/eligibility?query={value}
     * 
     * Auto-Detection Rules:
     * 1. WAD-YYYY-NNNNNNNN → Barcode search
     * 2. Digits only → Card Number search
     * 3. Other → 400 Bad Request
     * 
     * @param query Search query (card number or barcode)
     * @return EligibilityResultDto with complete eligibility information
     */
    @Operation(
        summary = "Check member eligibility (Unified)",
        description = "Deterministic eligibility check using Card Number or Barcode. " +
            "Auto-detects input type: WAD-YYYY-NNNNNNNN for barcode, digits for card number. " +
            "Returns single result with complete eligibility status."
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Eligibility check completed successfully (member found)",
            content = @Content(schema = @Schema(implementation = EligibilityResultDto.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid input format (INVALID_ELIGIBILITY_INPUT)"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Member not found (MEMBER_NOT_FOUND)"
        )
    })
    @GetMapping("/eligibility")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'EMPLOYER_ADMIN', 'PROVIDER_STAFF')")
    public ResponseEntity<ApiResponse<EligibilityResultDto>> checkEligibility(
        @Parameter(
            description = "Search query - Card Number (digits) or Barcode (WAD-YYYY-NNNNNNNN)",
            required = true,
            example = "1234567890"
        )
        @RequestParam(name = "query") String query
    ) {
        // Security: Don't log query content (may contain sensitive data)
        log.info("📥 [ELIGIBILITY-REQUEST] Received");

        try {
            // Perform eligibility check with auto-detection
            EligibilityResultDto result = eligibilityService.checkEligibility(query);

            // Strategic logging: Result already logged in service layer
            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (InvalidEligibilityInputException e) {
            log.warn("⚠️ [INVALID-INPUT] {}", e.getErrorCode());
            return ResponseEntity
                .badRequest()
                .body(ApiResponse.error(e.getMessage()));

        } catch (MemberNotFoundException e) {
            log.warn("⚠️ [NOT-FOUND] {}", e.getErrorCode());
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("💥 [UNEXPECTED-ERROR] Eligibility check failed", e);
            return ResponseEntity
                .internalServerError()
                .body(ApiResponse.error("Internal server error during eligibility check"));
        }
    }

    /**
     * Global exception handler for this controller
     */
    @ExceptionHandler(InvalidEligibilityInputException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidInput(InvalidEligibilityInputException e) {
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(MemberNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleMemberNotFound(MemberNotFoundException e) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(e.getMessage()));
    }
}
