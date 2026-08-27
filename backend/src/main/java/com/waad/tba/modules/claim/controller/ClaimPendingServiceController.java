package com.waad.tba.modules.claim.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.claim.dto.*;
import com.waad.tba.modules.claim.service.ClaimPendingServiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/claims/{claimId}/pending-services")
@RequiredArgsConstructor
public class ClaimPendingServiceController {
    private final ClaimPendingServiceService service;

    @GetMapping
    @PreAuthorize("@claimAccessGuard.canReview(#claimId)")
    public ApiResponse<List<PendingServiceResponse>> list(@PathVariable Long claimId) {
        return ApiResponse.success(service.list(claimId));
    }

    @PostMapping
    @PreAuthorize("@claimAccessGuard.canReview(#claimId)")
    public ApiResponse<PendingServiceResponse> create(@PathVariable Long claimId,
                                                       @Valid @RequestBody PendingServiceCreateRequest request) {
        return ApiResponse.success(service.create(claimId, request));
    }

    @PostMapping("/{pendingId}/decision")
    @PreAuthorize("@claimAccessGuard.canApprove(#claimId)")
    public ApiResponse<PendingServiceResponse> decide(@PathVariable Long claimId,
                                                       @PathVariable Long pendingId,
                                                       @Valid @RequestBody PendingServiceDecisionRequest request) {
        return ApiResponse.success(service.decide(claimId, pendingId, request));
    }
}
