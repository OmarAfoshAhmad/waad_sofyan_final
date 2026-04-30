package com.waad.tba.modules.claim.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.claim.draft.dto.ClaimDraftResponse;
import com.waad.tba.modules.claim.draft.dto.ClaimDraftUpsertRequest;
import com.waad.tba.modules.claim.draft.service.ClaimDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/claims/draft")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Claim Draft API", description = "Autosave draft APIs")
@PreAuthorize("isAuthenticated()")
public class ClaimDraftController {

    private final ClaimDraftService claimDraftService;

    @GetMapping
    @Operation(summary = "Get current user's draft for batch")
    public ResponseEntity<ApiResponse<ClaimDraftResponse>> getDraft(@RequestParam("batchId") Long batchId) {
        Optional<ClaimDraftResponse> draft = claimDraftService.getDraft(batchId);
        if (draft.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(null));
        }
        return ResponseEntity.ok(ApiResponse.success(draft.get()));
    }

    @PostMapping
    @Operation(summary = "Create or update autosave draft")
    public ResponseEntity<ApiResponse<ClaimDraftResponse>> upsertDraft(
            @Valid @RequestBody ClaimDraftUpsertRequest request) {
        ClaimDraftResponse response = claimDraftService.upsertDraft(request);
        return ResponseEntity.ok(ApiResponse.success("Draft saved", response));
    }

    @DeleteMapping
    @Operation(summary = "Delete current user's draft for batch")
    public ResponseEntity<ApiResponse<Void>> deleteDraft(@RequestParam("batchId") Long batchId) {
        claimDraftService.deleteDraft(batchId);
        return ResponseEntity.ok(ApiResponse.success("Draft deleted", null));
    }
}
