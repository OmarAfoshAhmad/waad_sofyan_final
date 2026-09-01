package com.waad.tba.modules.claimcontext.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.claimcontext.service.ClaimContextQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/claim-contexts")
@RequiredArgsConstructor
public class ClaimContextController {

    private final ClaimContextQueryService queryService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ClaimContextQueryService.ClaimContextView>>> listActive() {
        return ResponseEntity.ok(ApiResponse.success(queryService.listActive()));
    }
}
