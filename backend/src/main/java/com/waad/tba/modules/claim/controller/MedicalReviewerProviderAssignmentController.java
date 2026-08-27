package com.waad.tba.modules.claim.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.claim.dto.MedicalReviewerProviderAssignmentsRequest;
import com.waad.tba.modules.claim.dto.MedicalReviewerProviderAssignmentsResponse;
import com.waad.tba.modules.claim.service.MedicalReviewerProviderAssignmentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/medical-reviewers")
@PreAuthorize("@permissionGuard.has('USER_MANAGE')")
public class MedicalReviewerProviderAssignmentController {

    private final MedicalReviewerProviderAssignmentService assignmentService;

    @GetMapping("/{id}/providers")
    @PreAuthorize("@permissionGuard.has('USER_MANAGE')")
    public ResponseEntity<ApiResponse<MedicalReviewerProviderAssignmentsResponse>> getAssignments(@PathVariable("id") Long reviewerId) {
        MedicalReviewerProviderAssignmentsResponse response = assignmentService.getAssignments(reviewerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}/providers")
    @PreAuthorize("@permissionGuard.has('USER_MANAGE')")
    public ResponseEntity<ApiResponse<MedicalReviewerProviderAssignmentsResponse>> updateAssignments(
            @PathVariable("id") Long reviewerId,
            @Valid @RequestBody MedicalReviewerProviderAssignmentsRequest request,
            HttpServletRequest httpServletRequest
    ) {
        MedicalReviewerProviderAssignmentsResponse response = assignmentService.updateAssignments(
                reviewerId,
                request.providerIds(),
                httpServletRequest
        );
        return ResponseEntity.ok(ApiResponse.success("Reviewer provider assignments updated successfully", response));
    }
}
