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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER')")
    public ResponseEntity<ApiResponse<StructureResponse>> get(@PathVariable Long policyId) {
        return ResponseEntity.ok(ApiResponse.success(service.getStructure(policyId)));
    }

    @GetMapping("/import-template")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','MEDICAL_REVIEWER')")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable Long policyId) {
        service.getStructure(policyId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=benefits-groups-template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(importService.createSimplifiedTemplate());
    }

    @PostMapping("/cleanup")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> cleanupOrphanedData(@PathVariable Long policyId) {
        policyService.assertDraftConfiguration(policyId);
        service.cleanupOrphanedData(policyId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/reset")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Integer>> resetStructure(@PathVariable Long policyId) {
        policyService.assertDraftConfiguration(policyId);
        int deleted = service.resetPolicyStructure(policyId);
        return ResponseEntity.ok(ApiResponse.success("تم إعادة تهيئة هيكل المنافع. القواعد المُعطَّلة: " + deleted, deleted));
    }

    @PostMapping("/groups")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(@PathVariable Long policyId,
            @Valid @RequestBody GroupRequest request) {
        policyService.assertDraftConfiguration(policyId);
        return ResponseEntity.ok(ApiResponse.success(service.createGroup(policyId, request)));
    }

    @PutMapping("/groups/{groupId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<GroupResponse>> updateGroup(@PathVariable Long policyId,
            @PathVariable Long groupId, @Valid @RequestBody GroupRequest request) {
        policyService.assertDraftConfiguration(policyId);
        return ResponseEntity.ok(ApiResponse.success(service.updateGroup(policyId, groupId, request)));
    }

    @PutMapping("/groups/{groupId}/active")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> toggleGroupActive(@PathVariable Long policyId, @PathVariable Long groupId) {
        policyService.assertDraftConfiguration(policyId);
        service.toggleGroupActive(policyId, groupId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/buckets")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<BucketResponse>> createBucket(@PathVariable Long policyId,
            @Valid @RequestBody BucketRequest request) {
        policyService.assertDraftConfiguration(policyId);
        return ResponseEntity.ok(ApiResponse.success(service.createBucket(policyId, request)));
    }

    @PostMapping("/rules/{ruleId}/buckets")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<RuleBucketResponse>> link(@PathVariable Long policyId,
            @PathVariable Long ruleId, @Valid @RequestBody RuleBucketRequest request) {
        policyService.assertDraftConfiguration(policyId);
        return ResponseEntity.ok(ApiResponse.success(service.linkRuleBucket(policyId, ruleId, request)));
    }

    @PutMapping("/rules/{ruleId}/individual-limit")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<BucketResponse>> upsertIndividualLimit(@PathVariable Long policyId,
            @PathVariable Long ruleId, @Valid @RequestBody IndividualLimitRequest request) {
        policyService.assertDraftConfiguration(policyId);
        return ResponseEntity.ok(ApiResponse.success(service.upsertIndividualLimit(policyId, ruleId, request)));
    }

    @DeleteMapping("/buckets/{bucketId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBucket(@PathVariable Long policyId, @PathVariable Long bucketId) {
        policyService.assertDraftConfiguration(policyId);
        service.deleteBucket(policyId, bucketId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/groups/{groupId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable Long policyId, @PathVariable Long groupId) {
        policyService.assertDraftConfiguration(policyId);
        service.deleteGroup(policyId, groupId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/links/{linkId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteLink(@PathVariable Long policyId, @PathVariable Long linkId) {
        policyService.assertDraftConfiguration(policyId);
        service.deleteLink(policyId, linkId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<BenefitStructureImportResult>> importWorkbook(
            @PathVariable Long policyId,
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(defaultValue = "MERGE") BenefitStructureImportService.ImportMode mode) {
        return ResponseEntity.ok(ApiResponse.success(importService.importWorkbook(policyId, file, dryRun, mode)));
    }
}
