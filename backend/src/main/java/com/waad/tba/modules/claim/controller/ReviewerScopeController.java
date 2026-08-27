package com.waad.tba.modules.claim.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.claim.dto.ReviewerProviderOptionDto;
import com.waad.tba.modules.claim.service.ReviewerProviderIsolationService;
import com.waad.tba.modules.rbac.entity.User;
import com.waad.tba.security.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reviewers")
@PreAuthorize("@permissionGuard.has('CLAIM_REVIEW')")
public class ReviewerScopeController {

    private final AuthorizationService authorizationService;
    private final ReviewerProviderIsolationService reviewerIsolationService;

    @GetMapping("/my-providers")
    @PreAuthorize("@permissionGuard.has('CLAIM_REVIEW')")
    public ResponseEntity<ApiResponse<List<ReviewerProviderOptionDto>>> getMyProviders() {
        User currentUser = authorizationService.getCurrentUser();

        if (!reviewerIsolationService.isSubjectToIsolation(currentUser)) {
            return ResponseEntity.ok(ApiResponse.success("Current user is not medical reviewer", List.of()));
        }

        List<ReviewerProviderOptionDto> providers = reviewerIsolationService.getAssignedProviders(currentUser);
        return ResponseEntity.ok(ApiResponse.success("Reviewer providers retrieved successfully", providers));
    }
}
