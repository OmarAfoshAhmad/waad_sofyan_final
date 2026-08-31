package com.waad.tba.modules.claimcontext.controller;

import com.waad.tba.common.dto.ApiResponse;
import com.waad.tba.modules.claimcontext.repository.ClaimContextDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/claim-contexts")
@RequiredArgsConstructor
public class ClaimContextController {
    private final ClaimContextDefinitionRepository repository;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Item>>> listActive() {
        List<Item> items = repository.findByActiveTrueOrderByDisplayOrderAscCodeAsc().stream()
                .map(context -> new Item(context.getCode(), context.getNameAr(), context.getBaseEncounterType().name()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    public record Item(String code, String nameAr, String baseEncounterType) {}
}
