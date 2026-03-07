package com.waad.tba.modules.eligibility.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.eligibility.domain.EligibilityResult;
import com.waad.tba.modules.eligibility.dto.EligibilityCheckRequest;
import com.waad.tba.modules.eligibility.dto.EligibilityCheckResponse;
import com.waad.tba.modules.eligibility.dto.FamilyEligibilityResponse;
import com.waad.tba.modules.eligibility.entity.EligibilityCheck;
import com.waad.tba.modules.eligibility.repository.EligibilityCheckRepository;
import com.waad.tba.modules.eligibility.service.EligibilityEngineService;
import com.waad.tba.modules.eligibility.service.FamilyEligibilityService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Eligibility Controller
 * Phase E1 - Eligibility Engine
 * 
 * REST API for eligibility verification.
 * 
 * Endpoints:
 * - POST /api/eligibility/check - Check member eligibility
 * - GET /api/eligibility/logs - View audit logs
 * - GET /api/eligibility/logs/{requestId} - View specific check
 * - GET /api/eligibility/rules - List active rules
 * 
 * @author TBA WAAD System
 * @version 2025.1
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/eligibility")
@RequiredArgsConstructor
@Tag(name = "Eligibility", description = "Eligibility Engine API - Member eligibility verification")
public class EligibilityController {

        private final EligibilityEngineService eligibilityService;
        private final FamilyEligibilityService familyEligibilityService;
        private final EligibilityCheckRepository eligibilityCheckRepository;
        private final AuthorizationService authorizationService;

        // ============================================
        // Eligibility Check
        // ============================================

        @Operation(summary = "Check Member Eligibility", description = "Verify if a member is eligible for a medical service on a specific date. "
                        +
                        "Returns eligibility status with detailed reasons and snapshot.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Eligibility check completed", content = @Content(schema = @Schema(implementation = EligibilityCheckResponse.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request parameters"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
        })
        @PostMapping("/check")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
        public ResponseEntity<ApiResponse<EligibilityCheckResponse>> checkEligibility(
                        @Valid @RequestBody EligibilityCheckRequest request) {

                log.info("[EligibilityAPI] Check request - MemberID: {}, ServiceDate: {}",
                                request.getMemberId(), request.getServiceDate());

                // Execute eligibility check
                EligibilityResult result = eligibilityService.checkEligibility(request);

                // Convert to DTO
                EligibilityCheckResponse response = EligibilityCheckResponse.from(result);

                // Return appropriate response based on eligibility
                if (result.isEligible()) {
                        return ResponseEntity.ok(ApiResponse.success(
                                        "تم التحقق من الأهلية بنجاح - مؤهل", response));
                } else {
                        return ResponseEntity.ok(ApiResponse.success(
                                        "تم التحقق من الأهلية - غير مؤهل", response));
                }
        }

        // ============================================
        // Family Eligibility Check
        // ============================================

        @Operation(summary = "Check Family Eligibility", description = "Verify eligibility for a member and all their family members (dependents or principal + siblings). "
                        +
                        "Returns eligibility status for each family member.")
        @ApiResponses({
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Family eligibility check completed", content = @Content(schema = @Schema(implementation = FamilyEligibilityResponse.class))),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request parameters"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Member not found")
        })
        @GetMapping("/family/{memberId}")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
        public ResponseEntity<ApiResponse<FamilyEligibilityResponse>> checkFamilyEligibility(
                        @PathVariable Long memberId,
                        @RequestParam(required = false) @Parameter(description = "Service date (defaults to today)") java.time.LocalDate serviceDate) {

                log.info("[EligibilityAPI] Family check request - MemberID: {}, ServiceDate: {}", memberId,
                                serviceDate);

                FamilyEligibilityResponse response = familyEligibilityService.checkFamilyEligibility(memberId,
                                serviceDate);

                String message = response.getSummary().getFamilyStatus().equals("ALL_ELIGIBLE")
                                ? "تم التحقق من أهلية العائلة - جميع الأفراد مؤهلون"
                                : response.getSummary().getFamilyStatus().equals("PARTIAL")
                                                ? "تم التحقق من أهلية العائلة - بعض الأفراد مؤهلون"
                                                : "تم التحقق من أهلية العائلة - لا يوجد أفراد مؤهلون";

                return ResponseEntity.ok(ApiResponse.success(message, response));
        }

        // ============================================
        // Audit Logs
        // ============================================

        @Operation(summary = "Get Eligibility Check Logs", description = "View audit trail of eligibility checks. Filterable by member, policy, and date range.")
        @GetMapping("/logs")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
        public ResponseEntity<ApiResponse<Page<EligibilityCheck>>> getLogs(
                        @Parameter(description = "Filter by member ID") @RequestParam(name = "memberId", required = false) Long memberId,

                        @Parameter(description = "Filter by policy ID") @RequestParam(name = "policyId", required = false) Long policyId,

                        @Parameter(description = "Search by name, civil ID, or request ID") @RequestParam(name = "search", required = false) String search,

                        @PageableDefault(size = 20, sort = "checkTimestamp") Pageable pageable) {

                User currentUser = authorizationService.getCurrentUser();
                boolean isSuperAdmin = currentUser != null && authorizationService.isSuperAdmin(currentUser);
                // companyScopeId is null only for SUPER_ADMIN — all other users are scoped to
                // their employer
                Long companyScopeId = isSuperAdmin ? null : (currentUser != null ? currentUser.getEmployerId() : null);

                Page<EligibilityCheck> logs;

                if (memberId != null) {
                        // Always apply company scope — non-admins cannot query members from other
                        // employers
                        if (companyScopeId != null) {
                                logs = eligibilityCheckRepository.findByMemberIdAndCompanyScopeId(memberId,
                                                companyScopeId, pageable);
                        } else {
                                logs = eligibilityCheckRepository.findByMemberId(memberId, pageable);
                        }
                } else if (policyId != null) {
                        // Always apply company scope — non-admins cannot query policies from other
                        // employers
                        if (companyScopeId != null) {
                                logs = eligibilityCheckRepository.findByPolicyIdAndCompanyScopeId(policyId,
                                                companyScopeId, pageable);
                        } else {
                                logs = eligibilityCheckRepository.findByPolicyId(policyId, pageable);
                        }
                } else if (search != null && !search.isEmpty()) {
                        logs = eligibilityCheckRepository.search(companyScopeId, search, pageable);
                } else if (companyScopeId != null) {
                        logs = eligibilityCheckRepository.findByCompanyScopeId(companyScopeId, pageable);
                } else if (isSuperAdmin) {
                        // Only SUPER_ADMIN may retrieve all records
                        logs = eligibilityCheckRepository.findAll(pageable);
                } else {
                        // Non-admin users with no employer scope cannot list all records
                        return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                                        .<ApiResponse<Page<EligibilityCheck>>>body(ApiResponse
                                                        .error("غير مصرح لك بعرض سجلات الأهلية دون تحديد نطاق"));
                }

                return ResponseEntity.ok(ApiResponse.success("تم جلب سجلات التحقق من الأهلية", logs));
        }

        @Operation(summary = "Get Eligibility Check by Request ID", description = "Retrieve a specific eligibility check record by its unique request ID.")
        @GetMapping("/logs/{requestId}")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
        public ResponseEntity<ApiResponse<EligibilityCheck>> getLogByRequestId(
                        @PathVariable String requestId) {

                return eligibilityCheckRepository.findByRequestId(requestId)
                                .map(check -> ResponseEntity.ok(ApiResponse.success("تم جلب سجل التحقق", check)))
                                .orElse(ResponseEntity.notFound().build());
        }

        // ============================================
        // Rules Info
        // ============================================

        @Operation(summary = "Get Active Eligibility Rules", description = "List all active eligibility rules in evaluation order.")
        @GetMapping("/rules")
        @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'PROVIDER_STAFF')")
        public ResponseEntity<ApiResponse<List<String>>> getActiveRules() {
                List<String> rules = eligibilityService.getActiveRules();
                return ResponseEntity.ok(ApiResponse.success("قواعد التحقق من الأهلية النشطة", rules));
        }

        // ============================================
        // Health Check (Now requires authentication per security policy)
        // ============================================

        @Operation(summary = "Eligibility Engine Health Check", description = "Check if the eligibility engine is operational.")
        @GetMapping("/health")
        @PreAuthorize("isAuthenticated()")
        public ResponseEntity<ApiResponse<String>> healthCheck() {
                int ruleCount = eligibilityService.getActiveRules().size();
                return ResponseEntity.ok(ApiResponse.success(
                                "محرك الأهلية يعمل بشكل صحيح",
                                "OK - " + ruleCount + " rules active"));
        }
}
