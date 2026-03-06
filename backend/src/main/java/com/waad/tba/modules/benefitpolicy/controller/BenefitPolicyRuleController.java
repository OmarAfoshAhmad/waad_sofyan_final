package com.waad.tba.modules.benefitpolicy.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.benefitpolicy.dto.*;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST Controller for Benefit Policy Rules management.
 * 
 * All endpoints are nested under a policy:
 * /api/benefit-policies/{policyId}/rules
 * 
 * Additionally provides coverage lookup endpoints:
 * /api/benefit-policies/{policyId}/coverage/service/{serviceId}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/benefit-policies/{policyId}")
@RequiredArgsConstructor
@Tag(name = "Benefit Policy Rules", description = "Manage coverage rules within benefit policies")
public class BenefitPolicyRuleController {

    private final BenefitPolicyRuleService ruleService;

    // ═══════════════════════════════════════════════════════════════════════════
    // READ ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/rules")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List all rules for a policy")
    public ResponseEntity<ApiResponse<List<BenefitPolicyRuleResponseDto>>> findAll(
            @PathVariable Long policyId) {
        List<BenefitPolicyRuleResponseDto> result = ruleService.findByPolicy(policyId);
        return ResponseEntity.ok(ApiResponse.success("Rules retrieved", result));
    }

    @GetMapping("/rules/paged")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List all rules for a policy (paginated)")
    public ResponseEntity<ApiResponse<Page<BenefitPolicyRuleResponseDto>>> findAllPaged(
            @PathVariable Long policyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<BenefitPolicyRuleResponseDto> result = ruleService.findByPolicy(policyId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Rules retrieved", result));
    }

    @GetMapping("/rules/active")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List only active rules for a policy")
    public ResponseEntity<ApiResponse<List<BenefitPolicyRuleResponseDto>>> findActive(
            @PathVariable Long policyId) {
        List<BenefitPolicyRuleResponseDto> result = ruleService.findActiveByPolicy(policyId);
        return ResponseEntity.ok(ApiResponse.success("Active rules retrieved", result));
    }

    @GetMapping("/rules/{ruleId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get a specific rule by ID")
    public ResponseEntity<ApiResponse<BenefitPolicyRuleResponseDto>> findById(
            @PathVariable Long policyId,
            @PathVariable Long ruleId) {
        BenefitPolicyRuleResponseDto result = ruleService.findById(ruleId);
        return ResponseEntity.ok(ApiResponse.success("Rule retrieved", result));
    }

    @GetMapping("/rules/category")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List category-level rules for a policy")
    public ResponseEntity<ApiResponse<List<BenefitPolicyRuleResponseDto>>> findCategoryRules(
            @PathVariable Long policyId) {
        List<BenefitPolicyRuleResponseDto> result = ruleService.findCategoryRules(policyId);
        return ResponseEntity.ok(ApiResponse.success("Category rules retrieved", result));
    }

    @GetMapping("/rules/service")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List service-level rules for a policy")
    public ResponseEntity<ApiResponse<List<BenefitPolicyRuleResponseDto>>> findServiceRules(
            @PathVariable Long policyId) {
        List<BenefitPolicyRuleResponseDto> result = ruleService.findServiceRules(policyId);
        return ResponseEntity.ok(ApiResponse.success("Service rules retrieved", result));
    }

    @GetMapping("/rules/pre-approval")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List rules that require pre-approval")
    public ResponseEntity<ApiResponse<List<BenefitPolicyRuleResponseDto>>> findPreApprovalRules(
            @PathVariable Long policyId) {
        List<BenefitPolicyRuleResponseDto> result = ruleService.findPreApprovalRules(policyId);
        return ResponseEntity.ok(ApiResponse.success("Pre-approval rules retrieved", result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COVERAGE LOOKUP ENDPOINTS (For Claims & Eligibility)
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/coverage/service/{serviceId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get coverage rule for a specific service",
               description = "Returns the applicable coverage rule for a service. " +
                           "Service-specific rules take priority over category rules.")
    public ResponseEntity<ApiResponse<BenefitPolicyRuleResponseDto>> getCoverageForService(
            @PathVariable Long policyId,
            @PathVariable Long serviceId) {
        
        Optional<BenefitPolicyRuleResponseDto> result = ruleService.findCoverageForService(policyId, serviceId);
        
        if (result.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success("Service not covered under this policy", null));
        }
        
        return ResponseEntity.ok(ApiResponse.success("Coverage found", result.get()));
    }

    @GetMapping("/coverage/service/{serviceId}/check")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Quick check if a service is covered")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkServiceCoverage(
            @PathVariable Long policyId,
            @PathVariable Long serviceId) {
        
        boolean isCovered = ruleService.isServiceCovered(policyId, serviceId);
        int coveragePercent = ruleService.getCoveragePercent(policyId, serviceId);
        boolean requiresPreApproval = ruleService.requiresPreApproval(policyId, serviceId);
        
        Map<String, Object> result = Map.of(
            "covered", isCovered,
            "coveragePercent", coveragePercent,
            "requiresPreApproval", requiresPreApproval
        );
        
        return ResponseEntity.ok(ApiResponse.success("Coverage check complete", result));
    }

    @GetMapping("/coverage/service/{serviceId}/usage")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'DATA_ENTRY')")
    @Operation(summary = "Check service usage against policy limits for a member")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkServiceUsage(
            @PathVariable Long policyId,
            @PathVariable Long serviceId,
            @RequestParam Long memberId,
            @RequestParam(required = false) Integer year) {
        
        Map<String, Object> result = ruleService.checkUsageLimit(policyId, serviceId, memberId, year);
        return ResponseEntity.ok(ApiResponse.success("Usage check complete", result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CREATE ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    @PostMapping("/rules")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Create a new rule for the policy",
               description = "Either medicalCategoryId OR medicalServiceId must be provided, not both.")
    public ResponseEntity<ApiResponse<BenefitPolicyRuleResponseDto>> create(
            @PathVariable Long policyId,
            @Valid @RequestBody BenefitPolicyRuleCreateDto dto) {
        
        log.info("Creating rule for policy {} - category: {}, service: {}", 
                policyId, dto.getMedicalCategoryId(), dto.getMedicalServiceId());
        
        BenefitPolicyRuleResponseDto result = ruleService.create(policyId, dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Rule created successfully", result));
    }

    @PostMapping("/rules/bulk")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Bulk create rules for the policy")
    public ResponseEntity<ApiResponse<List<BenefitPolicyRuleResponseDto>>> createBulk(
            @PathVariable Long policyId,
            @Valid @RequestBody List<BenefitPolicyRuleCreateDto> dtos) {
        
        log.info("Bulk creating {} rules for policy {}", dtos.size(), policyId);
        
        List<BenefitPolicyRuleResponseDto> result = ruleService.createBulk(policyId, dtos);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Rules created successfully", result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UPDATE ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    @PutMapping("/rules/{ruleId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update an existing rule",
               description = "Note: Cannot change the target (category/service) after creation.")
    public ResponseEntity<ApiResponse<BenefitPolicyRuleResponseDto>> update(
            @PathVariable Long policyId,
            @PathVariable Long ruleId,
            @Valid @RequestBody BenefitPolicyRuleUpdateDto dto) {
        
        log.info("Updating rule {} for policy {}", ruleId, policyId);
        
        BenefitPolicyRuleResponseDto result = ruleService.update(ruleId, dto);
        return ResponseEntity.ok(ApiResponse.success("Rule updated successfully", result));
    }

    @PostMapping("/rules/{ruleId}/toggle")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Toggle rule active status")
    public ResponseEntity<ApiResponse<BenefitPolicyRuleResponseDto>> toggleActive(
            @PathVariable Long policyId,
            @PathVariable Long ruleId) {
        
        BenefitPolicyRuleResponseDto result = ruleService.toggleActive(ruleId);
        return ResponseEntity.ok(ApiResponse.success("Rule toggled", result));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DELETE ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    @DeleteMapping("/rules/{ruleId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Soft delete a rule (deactivate)")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long policyId,
            @PathVariable Long ruleId) {
        
        log.info("Deleting rule {} from policy {}", ruleId, policyId);
        ruleService.delete(ruleId);
        return ResponseEntity.ok(ApiResponse.success("Rule deleted", null));
    }

    @DeleteMapping("/rules/{ruleId}/hard")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Permanently delete a rule")
    public ResponseEntity<ApiResponse<Void>> hardDelete(
            @PathVariable Long policyId,
            @PathVariable Long ruleId) {
        
        log.info("Hard deleting rule {} from policy {}", ruleId, policyId);
        ruleService.hardDelete(ruleId);
        return ResponseEntity.ok(ApiResponse.success("Rule permanently deleted", null));
    }

    @DeleteMapping("/rules")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Delete all rules for this policy")
    public ResponseEntity<ApiResponse<Void>> deleteAll(
            @PathVariable Long policyId) {
        
        log.info("Deleting all rules for policy {}", policyId);
        ruleService.deleteAllForPolicy(policyId);
        return ResponseEntity.ok(ApiResponse.success("All rules deleted", null));
    }

    @PostMapping("/rules/deactivate-all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Deactivate all rules for this policy")
    public ResponseEntity<ApiResponse<Integer>> deactivateAll(
            @PathVariable Long policyId) {
        
        log.info("Deactivating all rules for policy {}", policyId);
        int count = ruleService.deactivateAllForPolicy(policyId);
        return ResponseEntity.ok(ApiResponse.success("Deactivated " + count + " rules", count));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATISTICS ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════════

    @GetMapping("/rules/count")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Get rule count for the policy")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getRuleCount(
            @PathVariable Long policyId) {
        
        long total = ruleService.countByPolicy(policyId);
        long active = ruleService.countActiveByPolicy(policyId);
        
        Map<String, Long> counts = Map.of(
            "total", total,
            "active", active,
            "inactive", total - active
        );
        
        return ResponseEntity.ok(ApiResponse.success("Rule counts", counts));
    }
}
