package com.waad.tba.modules.backlog.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.backlog.dto.BacklogClaimRequest;
import com.waad.tba.modules.backlog.dto.BacklogImportResponse;
import com.waad.tba.modules.backlog.service.BacklogService;
import com.waad.tba.modules.claim.entity.ClaimSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/backlog")
@RequiredArgsConstructor
@Tag(name = "Backlog Claims", description = "Backlog Claims Entry and Import")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MEDICAL_REVIEWER', 'DATA_ENTRY')")
public class BacklogController {

    private final BacklogService backlogService;

    @PostMapping("/manual")
    @Operation(summary = "Create manual backlog claim")
    public ResponseEntity<ApiResponse<Long>> createManualBacklog(@Valid @RequestBody BacklogClaimRequest request) {
        String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();
        Long claimId = backlogService.createBacklogClaim(request, currentUser, ClaimSource.MANUAL_BACKLOG);
        return ResponseEntity.ok(ApiResponse.success("\u062a\u0645 \u0625\u0646\u0634\u0627\u0621 \u0627\u0644\u0645\u0637\u0627\u0644\u0628\u0629 \u0628\u0646\u062c\u0627\u062d", claimId));
    }

    @DeleteMapping("/{claimId}")
    @Operation(summary = "Cancel (soft-delete) a backlog claim")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DATA_ENTRY')")
    public ResponseEntity<ApiResponse<Void>> deleteBacklogClaim(@PathVariable Long claimId) {
        String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();
        backlogService.cancelBacklogClaim(claimId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("تم إلغاء المطالبة بنجاح", null));
    }

    @PostMapping("/import")
    @Operation(summary = "Import backlog claims from Excel")
    public ResponseEntity<ApiResponse<BacklogImportResponse>> importExcel(@RequestParam("file") MultipartFile file) {
        String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();
        BacklogImportResponse response = backlogService.importExcel(file, currentUser);
        return ResponseEntity.ok(ApiResponse.success("تمت معالجة الاستيراد", response));
    }
}
