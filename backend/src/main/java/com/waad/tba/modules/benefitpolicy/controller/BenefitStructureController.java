package com.waad.tba.modules.benefitpolicy.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.benefitpolicy.dto.BenefitStructureDtos.*;
import com.waad.tba.modules.benefitpolicy.service.BenefitStructureService;
import com.waad.tba.modules.benefitpolicy.service.BenefitStructureImportService;
import com.waad.tba.modules.benefitpolicy.service.BenefitPolicyService;
import com.waad.tba.modules.benefitpolicy.dto.BenefitStructureImportResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/benefit-policies/{policyId}/structure")
@RequiredArgsConstructor
public class BenefitStructureController {
    private final BenefitStructureService service;
    private final BenefitStructureImportService importService;
    private final BenefitPolicyService policyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','INSURANCE_ADMIN','MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<StructureResponse>> get(@PathVariable Long policyId) {
        return ResponseEntity.ok(ApiResponse.success(service.getStructure(policyId)));
    }

    @PostMapping("/groups")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','INSURANCE_ADMIN')")
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(@PathVariable Long policyId,
            @Valid @RequestBody GroupRequest request) {
        policyService.assertDraftConfiguration(policyId);
        return ResponseEntity.ok(ApiResponse.success(service.createGroup(policyId, request)));
    }

    @PostMapping("/buckets")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','INSURANCE_ADMIN')")
    public ResponseEntity<ApiResponse<BucketResponse>> createBucket(@PathVariable Long policyId,
            @Valid @RequestBody BucketRequest request) {
        policyService.assertDraftConfiguration(policyId);
        return ResponseEntity.ok(ApiResponse.success(service.createBucket(policyId, request)));
    }

    @PostMapping("/rules/{ruleId}/buckets")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','INSURANCE_ADMIN')")
    public ResponseEntity<ApiResponse<RuleBucketResponse>> link(@PathVariable Long policyId,
            @PathVariable Long ruleId, @Valid @RequestBody RuleBucketRequest request) {
        policyService.assertDraftConfiguration(policyId);
        return ResponseEntity.ok(ApiResponse.success(service.linkRuleBucket(policyId, ruleId, request)));
    }

    @DeleteMapping("/buckets/{bucketId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','INSURANCE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBucket(@PathVariable Long policyId, @PathVariable Long bucketId) {
        policyService.assertDraftConfiguration(policyId);
        service.deleteBucket(policyId, bucketId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/groups/{groupId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','INSURANCE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable Long policyId, @PathVariable Long groupId) {
        policyService.assertDraftConfiguration(policyId);
        service.deleteGroup(policyId, groupId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/links/{linkId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','INSURANCE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteLink(@PathVariable Long policyId, @PathVariable Long linkId) {
        policyService.assertDraftConfiguration(policyId);
        service.deleteLink(policyId, linkId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','INSURANCE_ADMIN')")
    public ResponseEntity<ApiResponse<BenefitStructureImportResult>> importWorkbook(
            @PathVariable Long policyId,
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return ResponseEntity.ok(ApiResponse.success(importService.importWorkbook(policyId, file, dryRun)));
    }
}
